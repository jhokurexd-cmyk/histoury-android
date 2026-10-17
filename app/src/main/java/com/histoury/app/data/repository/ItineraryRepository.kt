package com.histoury.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.histoury.app.data.itinerary.ItineraryPlanner
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.model.ItineraryStatus
import com.histoury.app.data.model.ItineraryStop
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.model.withLegacyStopStatuses
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/**
 * How long a plain write waits for the server before trusting Firestore's
 * offline queue instead.
 */
private const val WRITE_ACK_TIMEOUT_MS = 6_000L

/**
 * Awaits a Firestore write, but not forever. A set/update/delete made
 * without signal is applied to the local cache at once and only completes
 * its Task when the server acknowledges it — which offline is "never", so
 * Save Trip / Start Trip used to spin endlessly. After the timeout the
 * write is left in Firestore's offline queue (it syncs by itself once the
 * phone is back online) and treated as done. Real failures such as a
 * rules rejection still throw when they arrive in time.
 */
private suspend fun Task<Void>.awaitOrQueued() {
    withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { await() }
}

/** What "Save Changes" rewrites on an existing itinerary — everything but createdAt. */
private val UPDATABLE_FIELDS = listOf(
    "documentId", "userId", "name", "stops", "totalDistanceMeters",
    "totalWalkingMinutes", "totalVisitMinutes", "travelMode", "startTimeMinutes",
    "completedStopIds", "status", "startedAt", "completedAt", "deleteAt", "updatedAt"
)

class ItineraryRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getItinerariesForCurrentUser(): List<Itinerary> =
        loadItinerariesForCurrentUser() ?: emptyList()

    /**
     * The signed-in user's itineraries, or null when they couldn't be read
     * at all (so the caller can keep what it's already showing instead of
     * replacing the list with an empty one).
     *
     * Saved itineraries used to "disappear": any failure — a session not yet
     * restored on a cold start, a stale token after an app update, one
     * document that didn't deserialize — was swallowed and reported as an
     * empty list. Now it waits for the session, retries once with a fresh
     * token, falls back to the offline cache, and skips only the document
     * that can't be read.
     */
    suspend fun loadItinerariesForCurrentUser(): List<Itinerary>? {

        val user = awaitRestoredUser() ?: return emptyList()
        val userId = user.uid

        val query = db.collection("itineraries").whereEqualTo("userId", userId)

        val snapshot = try {
            query.get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
            ) {
                runCatching { user.getIdToken(true).await() }
                runCatching { query.get().await() }.getOrNull()
                    ?: runCatching { query.get(Source.CACHE).await() }.getOrNull()
            } else {
                runCatching { query.get(Source.CACHE).await() }.getOrNull()
            }
        } catch (e: Exception) {
            runCatching { query.get(Source.CACHE).await() }.getOrNull()
        } ?: return null

        // Empty and only from the phone's cache (no server): unknown, not
        // "no trips" — nothing may have been cached yet on this phone.
        if (snapshot.isEmpty && snapshot.metadata.isFromCache) return null

        return try {

            // ESTIMATE: an itinerary saved moments ago (or offline) still has
            // its createdAt/updatedAt pending; without this they read back as
            // null and a just-saved trip sorted to the bottom of the list.
            val loaded = snapshot.documents.mapNotNull { document ->
                runCatching {
                    document.toObject(Itinerary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.withLegacyStopStatuses()
                        ?.copy(documentId = document.id)
                }.getOrNull()
            }

            // Backfill the deadline for itineraries completed before the
            // retention fields were introduced.
            val itineraries = loaded.map { itinerary ->
                if (itinerary.status == ItineraryStatus.COMPLETED && itinerary.deleteAt == null) {
                    val completedAtMillis = timestampMillis(itinerary.completedAt)
                        ?: timestampMillis(itinerary.updatedAt)
                        ?: System.currentTimeMillis()
                    val completedAt = Date(completedAtMillis)
                    val deleteAt = Date(completedAtMillis + COMPLETED_RETENTION_MS)
                    // Not awaited: tidying-up writes go through Firestore's
                    // queue on their own. Awaiting them offline held the
                    // list up for several seconds per trip.
                    runCatching {
                        db.collection("itineraries").document(itinerary.documentId).update(
                            mapOf("completedAt" to completedAt, "deleteAt" to deleteAt)
                        )
                    }
                    itinerary.copy(completedAt = completedAt, deleteAt = deleteAt)
                } else {
                    itinerary
                }
            }

            val now = System.currentTimeMillis()
            val (expired, active) = itineraries.partition { itinerary ->
                itinerary.status == ItineraryStatus.COMPLETED && timestampMillis(itinerary.deleteAt)?.let {
                    it <= now
                } == true
            }

            expired.forEach { itinerary ->
                runCatching {
                    db.collection("itineraries").document(itinerary.documentId).delete()
                }
            }

            active

        } catch (e: Exception) {
            null
        }
    }

    suspend fun getItineraryById(documentId: String): Itinerary? {

        return try {

            val ref = db.collection("itineraries").document(documentId)

            // Offline: the copy cached on the phone is still a usable trip.
            val document = runCatching { ref.get().await() }.getOrNull()
                ?: ref.get(Source.CACHE).await()

            document.toObject(Itinerary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.withLegacyStopStatuses()
                ?.copy(documentId = document.id)

        } catch (e: Exception) {
            null
        }
    }

    /**
     * The visitor's currently IN_PROGRESS itinerary, if any. There is only
     * ever meant to be one — [com.histoury.app.data.viewmodel.ItineraryViewModel.beginTrip]
     * pauses every other active trip when one starts — but if two ever
     * exist (e.g. from an older app version), the most recently started one
     * wins. The query's own order (by document id) is effectively random, so
     * taking "the first match" could pick an old trip after a restart.
     *
     * This is what lets a killed-and-reopened app (see
     * [com.histoury.app.data.itinerary.ActiveItineraryTrip.rehydrate]) and
     * Home's "Continue Trip" card find the same trip without either one
     * keeping its own copy of "which itinerary is active."
     */
    suspend fun getActiveItinerary(): Itinerary? =
        getActiveItineraries().maxWithOrNull(
            compareBy<Itinerary>(
                { timestampMillis(it.startedAt) ?: 0L },
                { timestampMillis(it.updatedAt) ?: 0L },
                { it.documentId }
            )
        )

    /**
     * Every IN_PROGRESS itinerary of the signed-in user (normally zero or
     * one). Empty when there is no user or the read fails.
     */
    suspend fun getActiveItineraries(): List<Itinerary> {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

        return try {

            db.collection("itineraries")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", ItineraryStatus.IN_PROGRESS)
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    runCatching {
                        document.toObject(Itinerary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.withLegacyStopStatuses()
                            ?.copy(documentId = document.id)
                    }.getOrNull()
                }

        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Pauses every IN_PROGRESS itinerary except [keepDocumentId], so only
     * one trip is ever under way. This used to look at a single match
     * (limit 1), which was often the trip being started itself — and then
     * the older trip stayed IN_PROGRESS too. Returns the ids it paused.
     */
    suspend fun pauseOtherActiveItineraries(keepDocumentId: String): List<String> =
        getActiveItineraries()
            .map { it.documentId }
            .filter { it.isNotBlank() && it != keepDocumentId }
            .filter { pauseItinerary(it) }

    /**
     * The most recently updated itinerary that's still underway — IN_PROGRESS
     * or PAUSED — for Home's "Continue Your Trip" card. Unlike
     * [getActiveItinerary] (which only ever means "resume navigation right
     * now"), this also surfaces a paused trip, since Home's card is meant to
     * offer resuming it, not just one already mid-navigation. Built on top
     * of [getItinerariesForCurrentUser] rather than a second Firestore query
     * so this doesn't need its own composite index.
     */
    suspend fun getResumableItinerary(): Itinerary? =
        getItinerariesForCurrentUser()
            .filter { it.status == ItineraryStatus.IN_PROGRESS || it.status == ItineraryStatus.PAUSED }
            .maxByOrNull { timestampMillis(it.updatedAt) ?: 0L }

    /** Creates a new itinerary owned by the current user. Returns its new document id, or null on failure. */
    suspend fun createItinerary(itinerary: Itinerary): String? {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return try {

            val docRef = db.collection("itineraries").document()

            val toSave = itinerary.copy(
                documentId = docRef.id,
                userId = userId,
                createdAt = FieldValue.serverTimestamp(),
                updatedAt = FieldValue.serverTimestamp()
            )

            docRef.set(toSave).awaitOrQueued()

            docRef.id

        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateItinerary(documentId: String, itinerary: Itinerary): Boolean {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return false

        return try {

            // Every field except createdAt. A plain set() used to overwrite
            // createdAt with null on each "Save Changes", which dropped the
            // trip's date and moved it around (or to the bottom of) the
            // saved list.
            db.collection("itineraries")
                .document(documentId)
                .set(
                    itinerary.copy(
                        documentId = documentId,
                        userId = userId,
                        updatedAt = FieldValue.serverTimestamp()
                    ),
                    SetOptions.mergeFields(UPDATABLE_FIELDS)
                )
                .awaitOrQueued()

            true

        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteItinerary(documentId: String): Boolean {

        return try {
            db.collection("itineraries").document(documentId).delete().awaitOrQueued()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Explicit "Pause & Save for Later" — the trip stays IN_PROGRESS's data intact, only the status changes. */
    suspend fun pauseItinerary(documentId: String): Boolean {
        if (documentId.isBlank()) return false
        return try {
            db.collection("itineraries").document(documentId)
                .update(mapOf("status" to ItineraryStatus.PAUSED, "updatedAt" to FieldValue.serverTimestamp()))
                .awaitOrQueued()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Reopening a paused (or already in-progress) trip from "Continue Trip" / "Resume Trip". */
    suspend fun resumeItinerary(documentId: String): Boolean {
        if (documentId.isBlank()) return false
        return try {
            db.collection("itineraries").document(documentId)
                .update(mapOf("status" to ItineraryStatus.IN_PROGRESS, "updatedAt" to FieldValue.serverTimestamp()))
                .awaitOrQueued()
            // Only one trip may be under way at a time.
            pauseOtherActiveItineraries(documentId)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ----- Per-stop progress -----
    //
    // Arrival and completion are deliberately separate calls (see
    // StopStatus's kdoc): a geofence dwell or reaching a destination while
    // navigating in-app can only ever call markStopArrived, never
    // markStopCompleted — that one only runs from an explicit "Complete
    // Stop" action. All four go through the same transactional read-modify
    // -write so a stop's status, the itinerary's derived completedStopIds,
    // and the itinerary's own status never fall out of sync with each
    // other, even if two writes for the same trip land close together.

    suspend fun markStopArrived(documentId: String, stopId: String): Boolean =
        updateStopStatus(documentId, stopId, StopStatus.ARRIVED) != null

    suspend fun markStopExploring(documentId: String, stopId: String): Boolean =
        updateStopStatus(documentId, stopId, StopStatus.EXPLORING) != null

    /** Returns the itinerary as saved (so the caller knows if this was also the final stop), or null on failure. */
    suspend fun markStopCompleted(documentId: String, stopId: String): Itinerary? =
        updateStopStatus(documentId, stopId, StopStatus.COMPLETED)

    /** Same contract as [markStopCompleted] — a skipped stop still counts toward finishing the trip. */
    suspend fun markStopSkipped(documentId: String, stopId: String): Itinerary? =
        updateStopStatus(documentId, stopId, StopStatus.SKIPPED)

    /** PENDING and ARRIVED can still move anywhere; a resolved stop (COMPLETED/SKIPPED) never changes again. */
    private fun canTransition(from: String, to: String): Boolean {
        if (StopStatus.isResolved(from)) return false
        return when (to) {
            StopStatus.ARRIVED -> from == StopStatus.PENDING || from == StopStatus.ARRIVED
            StopStatus.EXPLORING -> true
            StopStatus.COMPLETED -> true
            StopStatus.SKIPPED -> true
            else -> false
        }
    }

    /**
     * What a status change does to an itinerary: the field updates to write
     * (null when there is nothing valid to change) and the itinerary as it
     * will look afterwards. Pure, so the online transaction and the offline
     * fallback below make exactly the same change.
     */
    private fun applyStopStatus(
        documentId: String,
        itinerary: Itinerary,
        stopId: String,
        newStatus: String,
        now: Date
    ): Pair<Map<String, Any>?, Itinerary> {

        val targetStop = itinerary.stops.firstOrNull { it.refId == stopId }

        if (targetStop == null || !canTransition(targetStop.status, newStatus)) {
            // Nothing valid to change — most often a duplicate arrival
            // event for a stop that's already past ARRIVED. Reading
            // without writing keeps this a no-op rather than an error.
            return null to itinerary.copy(documentId = documentId)
        }

        val updatedStops = itinerary.stops.map { stop ->
            if (stop.refId == stopId) stop.copy(status = newStatus) else stop
        }

        val completedStopIds = updatedStops
            .filter { it.status == StopStatus.COMPLETED }
            .map { it.refId }

        val tripResolved = updatedStops.isNotEmpty() &&
            updatedStops.all { StopStatus.isResolved(it.status) }

        val updates = mutableMapOf<String, Any>(
            "stops" to updatedStops,
            "completedStopIds" to completedStopIds,
            "updatedAt" to now
        )

        var resultStatus = itinerary.status
        var resultCompletedAt = itinerary.completedAt
        var resultDeleteAt = itinerary.deleteAt

        if (tripResolved) {
            resultStatus = ItineraryStatus.COMPLETED
            resultCompletedAt = now
            resultDeleteAt = Date(now.time + COMPLETED_RETENTION_MS)
            updates["status"] = resultStatus
            updates["completedAt"] = now
            updates["deleteAt"] = resultDeleteAt
        } else if (itinerary.status == ItineraryStatus.PLANNED || itinerary.status == ItineraryStatus.PAUSED) {
            // A stop only ever changes status on a trip the visitor
            // has actually started (or resumed into) — make sure the
            // itinerary itself reflects that rather than staying
            // PLANNED/PAUSED while its stops say otherwise.
            resultStatus = ItineraryStatus.IN_PROGRESS
            updates["status"] = resultStatus
        }

        return updates to itinerary.copy(
            documentId = documentId,
            stops = updatedStops,
            completedStopIds = completedStopIds,
            status = resultStatus,
            completedAt = resultCompletedAt,
            deleteAt = resultDeleteAt
        )
    }

    private suspend fun updateStopStatus(
        documentId: String,
        stopId: String,
        newStatus: String
    ): Itinerary? {

        if (documentId.isBlank() || stopId.isBlank()) return null

        val ref = db.collection("itineraries").document(documentId)

        // Online: a transaction, so two writes for the same trip landing
        // close together can't overwrite each other.
        val online = runOnline {
            db.runTransaction { transaction ->
                val itinerary = transaction.get(ref).toObject(Itinerary::class.java)
                    ?.withLegacyStopStatuses()
                    ?: return@runTransaction null
                val (updates, result) = applyStopStatus(documentId, itinerary, stopId, newStatus, Date())
                if (updates != null) transaction.update(ref, updates)
                result
            }.await()
        }

        return when (online) {
            is OnlineResult.Done -> online.value
            OnlineResult.Failed -> null
            // Offline: transactions need the server, so arriving at or
            // completing a stop with no signal used to be lost. Make the
            // same change against the phone's cached copy instead; Firestore
            // applies it locally at once and sends it when back online.
            OnlineResult.Offline -> try {
                val itinerary = ref.get(Source.CACHE).await()
                    .toObject(Itinerary::class.java)
                    ?.withLegacyStopStatuses()
                    ?: return null
                val (updates, result) = applyStopStatus(documentId, itinerary, stopId, newStatus, Date())
                if (updates != null) ref.update(updates).awaitOrQueued()
                result
            } catch (e: Exception) {
                null
            }
        }
    }

    /** How a transaction attempt ended. */
    private sealed class OnlineResult<out T> {
        data class Done<T>(val value: T?) : OnlineResult<T>()
        /** No connection (or no answer in time): use the offline path. */
        object Offline : OnlineResult<Nothing>()
        /** A real failure, e.g. refused by the security rules. */
        object Failed : OnlineResult<Nothing>()
    }

    /**
     * Runs a transaction, telling "the phone is offline" apart from a real
     * failure. A transaction that gets no answer in [TRANSACTION_TIMEOUT_MS]
     * counts as offline too: weak signal inside the walls often means a
     * connection that exists but never answers.
     */
    private suspend fun <T> runOnline(block: suspend () -> T?): OnlineResult<T> =
        try {
            val value = withTimeoutOrNull(TRANSACTION_TIMEOUT_MS) { Box(block()) }
            if (value == null) OnlineResult.Offline else OnlineResult.Done(value.value)
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> OnlineResult.Offline
                else -> OnlineResult.Failed
            }
        } catch (e: java.io.IOException) {
            OnlineResult.Offline
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            OnlineResult.Failed
        }

    /** Lets a null result be told apart from a timeout. */
    private class Box<T>(val value: T?)

    /**
     * "Optimize Route" from the out-of-order-arrival dialog: the visitor
     * reached [arrivedRefId] out of the itinerary's saved order and chose to
     * reroute from here instead of continuing back for whichever stop was
     * originally next.
     *
     * Already-resolved stops (COMPLETED/SKIPPED) keep their relative order —
     * they're done, re-sequencing them changes nothing a visitor would see —
     * [arrivedRefId] becomes the new first open stop, and every other
     * still-open stop is re-ordered by [ItineraryPlanner.optimizeRoute]
     * starting from [arrivedRefId]'s own coordinates, the same
     * nearest-neighbor-plus-2-opt pass used when the itinerary was first
     * built. Nothing is removed: an unvisited stop stays on the trip, only
     * possibly in a new position.
     *
     * Only the `stops` array (and its `order` fields) is touched here —
     * [arrivedRefId]'s own status is untouched, so the caller still needs
     * its usual markStopArrived afterwards to actually record the arrival.
     * Runs in the same kind of transaction as [updateStopStatus] so a
     * reorder can't land on top of (or be clobbered by) a concurrent status
     * write for the same document.
     *
     * Returns the reordered stop list on success, or null if the itinerary
     * couldn't be loaded/saved, or [arrivedRefId] isn't one of its stops.
     */
    suspend fun optimizeFromCurrentStop(documentId: String, arrivedRefId: String): List<ItineraryStop>? {

        if (documentId.isBlank() || arrivedRefId.isBlank()) return null

        val ref = db.collection("itineraries").document(documentId)

        val online = runOnline {
            db.runTransaction { transaction ->
                val itinerary = transaction.get(ref).toObject(Itinerary::class.java)
                    ?.withLegacyStopStatuses()
                    ?: return@runTransaction null
                val reordered = reorderFrom(itinerary, arrivedRefId) ?: return@runTransaction null
                transaction.update(ref, mapOf("stops" to reordered, "updatedAt" to Date()))
                reordered
            }.await()
        }

        return when (online) {
            is OnlineResult.Done -> online.value
            OnlineResult.Failed -> null
            // Offline: same reorder on the cached copy, queued for sync.
            OnlineResult.Offline -> try {
                val itinerary = ref.get(Source.CACHE).await()
                    .toObject(Itinerary::class.java)
                    ?.withLegacyStopStatuses()
                    ?: return null
                val reordered = reorderFrom(itinerary, arrivedRefId) ?: return null
                ref.update(mapOf("stops" to reordered, "updatedAt" to Date())).awaitOrQueued()
                reordered
            } catch (e: Exception) {
                null
            }
        }
    }

    /** The stop order after "Optimize Route" from [arrivedRefId], or null if it isn't a stop. */
    private fun reorderFrom(itinerary: Itinerary, arrivedRefId: String): List<ItineraryStop>? {

        val arrivedStop = itinerary.stops.firstOrNull { it.refId == arrivedRefId } ?: return null

        val resolved = itinerary.stops.filter { StopStatus.isResolved(it.status) }
        val stillOpen = itinerary.stops.filter {
            !StopStatus.isResolved(it.status) && it.refId != arrivedRefId
        }

        val optimizedRemainder = ItineraryPlanner.optimizeRoute(
            startLatitude = arrivedStop.latitude,
            startLongitude = arrivedStop.longitude,
            stops = stillOpen
        ).orderedStops

        var nextOrder = 0
        val reordered = mutableListOf<ItineraryStop>()

        resolved.forEach { reordered.add(it.copy(order = nextOrder++)) }
        reordered.add(arrivedStop.copy(order = nextOrder++))
        optimizedRemainder.forEach { reordered.add(it.copy(order = nextOrder++)) }

        return reordered
    }

    private fun timestampMillis(value: Any?): Long? = when (value) {
        is com.google.firebase.Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    companion object {
        private const val COMPLETED_RETENTION_MS = 48L * 60L * 60L * 1000L

        /** How long a transaction may take before the offline path is used. */
        private const val TRANSACTION_TIMEOUT_MS = 8_000L
    }
}
