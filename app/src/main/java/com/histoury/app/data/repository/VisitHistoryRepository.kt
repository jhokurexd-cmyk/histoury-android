package com.histoury.app.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.histoury.app.data.model.VisitHistory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

private const val DUPLICATE_VISIT_WINDOW_MS = 30 * 60 * 1000L

// ----------------------------------------------------------------------------
// Visits made without signal
//
// Recording a visit is a Firestore TRANSACTION (see recordVisitIfNotRecent),
// and transactions need the server — offline they fail at once, and the
// visit was simply lost. A site visited in a dead zone (common inside the
// thick walls of Intramuros) then stayed locked in Site Library and
// Favorites for good.
//
// Now a visit that can't be written is kept in a small on-phone queue with
// the time it actually happened, counted as visited straight away, and
// written to Firestore (with its original time) the next time any visit is
// recorded or the visit history is loaded with a connection.
// ----------------------------------------------------------------------------
private const val PENDING_PREFS = "histoury_pending_visits"
private const val PENDING_KEY = "visits"
private const val MAX_PENDING_VISITS = 100

private data class PendingVisit(val userId: String, val siteId: String, val atMs: Long)

private val pendingLock = Mutex()

private const val AUTH_RESTORE_WAIT_MS = 5_000L

// Together these stay under a BroadcastReceiver's ~10s goAsync() budget.
private const val FLUSH_TIMEOUT_MS = 3_000L
private const val WRITE_TIMEOUT_MS = 5_000L

/**
 * Fires whenever a visit is recorded (or queued) on this phone, so screens
 * showing visit history — Visit History, Site Library, Favorites — can
 * refresh without waiting to be reopened.
 */
object VisitHistoryEvents {
    private val _changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changes: kotlinx.coroutines.flow.SharedFlow<Unit> = _changes
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}

class VisitHistoryRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Records an arrival at [siteId] (the site's business slug, matching
     * the geofence/site_content convention) for the signed-in user, unless
     * a visit for the same site was already recorded in the last 30
     * minutes — this keeps a single real-world arrival (which can trigger
     * through both the background geofence receiver and the in-app live
     * navigation path) from creating duplicate history entries.
     *
     * Those two triggers can fire within milliseconds of each other for the
     * exact same arrival. A plain "query for a recent visit, then add() if
     * none" has a race: both calls can run their query before either has
     * written anything, both see no recent visit, and both add a duplicate
     * visit_history document. Firestore's mobile SDK can only do an atomic
     * check-and-write inside a transaction against a document reference,
     * not a query — so a tiny per-(user, site) marker document is what the
     * transaction actually reads and conditionally updates, and the real
     * visit_history record is created in that same transaction, so only one
     * of the two racing callers ever gets past the check.
     *
     * Silently does nothing if no one is signed in.
     *
     * Returns true when this is a new arrival — written now, or queued to
     * be written once there's signal — and false when it's a duplicate of a
     * recent visit (or couldn't be recorded at all).
     */
    suspend fun recordVisitIfNotRecent(siteId: String): Boolean {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return false

        if (siteId.isBlank()) {
            return false
        }

        // Anything queued while offline goes first, so it keeps its order.
        // Bounded: the geofence receiver only has ~10s of goAsync() time,
        // and a weak connection can stall Firestore well past that.
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) { flushPendingVisits() }

        val now = System.currentTimeMillis()

        return try {
            // A timeout lands in the catch below and is queued like any
            // other connection failure. If the transaction commits anyway,
            // the lock document makes the later retry a no-op.
            withTimeout(WRITE_TIMEOUT_MS) {
                writeVisit(userId, siteId, now)
            }.also { recorded ->
                if (recorded) VisitHistoryEvents.notifyChanged()
            }
        } catch (e: Exception) {
            // Permission errors won't fix themselves by retrying; everything
            // else (no signal, timeout) is queued and retried later.
            if (!isPermanentFailure(e) && enqueue(PendingVisit(userId, siteId, now))) {
                VisitHistoryEvents.notifyChanged()
                true
            } else {
                false
            }
        }
    }

    /**
     * The transactional write: one visit per (user, site) per 30 minutes,
     * dated [atMs]. Returns false when a recent visit already exists.
     */
    private suspend fun writeVisit(userId: String, siteId: String, atMs: Long): Boolean {

            val lockRef = db.collection("visit_history_locks").document("${userId}_$siteId")
            val newVisitRef = db.collection("visit_history").document()
            val now = atMs

            return db.runTransaction { transaction ->

                val lockSnapshot = transaction.get(lockRef)
                val lastVisitAtMs = lockSnapshot.getLong("lastVisitAtMs") ?: 0L

                // Both directions: a queued offline visit can be OLDER than
                // the lock when a newer arrival got written first, and it's
                // only a duplicate if it's within the window of that one.
                if (kotlin.math.abs(now - lastVisitAtMs) < DUPLICATE_VISIT_WINDOW_MS) {
                    return@runTransaction false
                }

                // Never move the lock backwards for an older queued visit.
                transaction.set(lockRef, mapOf("lastVisitAtMs" to maxOf(now, lastVisitAtMs)))

                transaction.set(
                    newVisitRef,
                    hashMapOf(
                        "userId" to userId,
                        "siteId" to siteId,
                        "sceneId" to "",
                        "visitDate" to Timestamp(Date(now)),
                        "duration" to 0L,
                        "arActivated" to false,
                        "photosCaptured" to 0,
                        "reviewEligible" to true,
                        "reviewSubmitted" to false,
                        "status" to "completed"
                    )
                )

                true

            }.await()
    }

    private fun isPermanentFailure(e: Throwable): Boolean =
        e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

    private fun prefs() = runCatching {
        FirebaseApp.getInstance().applicationContext
            .getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
    }.getOrNull()

    private fun readPending(): List<PendingVisit> {
        val raw = prefs()?.getString(PENDING_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                PendingVisit(item.getString("u"), item.getString("s"), item.getLong("t"))
            }
        }.getOrDefault(emptyList())
    }

    private fun writePending(visits: List<PendingVisit>) {
        val array = JSONArray()
        visits.takeLast(MAX_PENDING_VISITS).forEach {
            array.put(JSONObject().put("u", it.userId).put("s", it.siteId).put("t", it.atMs))
        }
        prefs()?.edit()?.putString(PENDING_KEY, array.toString())?.apply()
    }

    /** Returns false when an equivalent visit was already queued. */
    private suspend fun enqueue(visit: PendingVisit): Boolean = pendingLock.withLock {
        val current = readPending()
        // Same de-duplication window as the server side.
        val duplicate = current.any {
            it.userId == visit.userId && it.siteId == visit.siteId &&
                kotlin.math.abs(it.atMs - visit.atMs) < DUPLICATE_VISIT_WINDOW_MS
        }
        if (!duplicate) writePending(current + visit)
        !duplicate
    }

    /** Writes queued offline visits (for the signed-in user) to Firestore. */
    suspend fun flushPendingVisits() = pendingLock.withLock {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@withLock
        val queued = readPending()
        if (queued.none { it.userId == userId }) return@withLock

        val remaining = mutableListOf<PendingVisit>()
        var offline = false
        queued.forEach { visit ->
            if (visit.userId != userId || offline) {
                remaining += visit
                return@forEach
            }
            try {
                writeVisit(visit.userId, visit.siteId, visit.atMs)
            } catch (e: Exception) {
                if (!isPermanentFailure(e)) {
                    // Still no connection: keep this and the rest for later.
                    offline = true
                    remaining += visit
                } else {
                    // A "permission denied" right after an update/restart is
                    // usually a stale sign-in token, not a real refusal.
                    // Refresh it and try once more before giving up on the
                    // visit, so queued visits aren't thrown away.
                    val retried = runCatching {
                        FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
                        writeVisit(visit.userId, visit.siteId, visit.atMs)
                    }
                    val retryError = retried.exceptionOrNull()
                    if (retryError != null && !isPermanentFailure(retryError)) {
                        offline = true
                        remaining += visit
                    }
                }
            }
        }
        writePending(remaining)
    }

    /** Queued visits for the signed-in user, as history entries. */
    private suspend fun pendingVisitsFor(userId: String): List<VisitHistory> = pendingLock.withLock {
        readPending().filter { it.userId == userId }.map {
            VisitHistory(
                id = "",
                userId = it.userId,
                siteId = it.siteId,
                visitDate = Timestamp(Date(it.atMs)),
                reviewEligible = false,
                status = "pending"
            )
        }
    }

    /**
     * The signed-in user's visits, plus any still queued offline.
     *
     * Kept for the screens that only need "which sites has this person
     * visited" (Site Library, Favorites, map, itinerary). A load that fails
     * returns whatever could be read — see [loadVisitHistory] for the version
     * that reports failure instead of an empty list.
     */
    suspend fun getVisitHistoryForCurrentUser(): List<VisitHistory> =
        loadVisitHistory().getOrElse { emptyList() }

    /**
     * Loads the signed-in user's visit history, robustly.
     *
     * "Visit History is empty after an app update until I log out and back
     * in" came from this read failing and the failure being reported as an
     * empty list:
     *
     *  - Right after an update or cold start, Firebase Auth restores the
     *    session from disk but may still be refreshing its ID token. A query
     *    sent in that window can be refused (PERMISSION_DENIED /
     *    UNAUTHENTICATED); logging in again "fixed" it only because it fetched
     *    a fresh token. Now: wait for Auth to report the user, and on an auth
     *    refusal force a token refresh and try once more.
     *  - One visit document that didn't match the model (e.g. a field written
     *    with a different type by an older build) made toObject() throw for
     *    the whole query, dropping every visit. Each document is now read on
     *    its own; a bad one is skipped, the rest still show.
     *  - Any other server failure (no signal) falls back to the copy
     *    Firestore keeps on the phone.
     *
     * Nothing here writes to, deletes, or rewrites existing visit records.
     * Returns failure only when neither the server nor the phone's cache
     * could be read, so the caller can keep what it's showing.
     */
    suspend fun loadVisitHistory(): Result<List<VisitHistory>> {

        val user = awaitSignedInUser() ?: return Result.success(emptyList())
        val userId = user.uid

        flushPendingVisits()

        // Visits still waiting for a connection count as visited (they
        // unlock Site Library / Favorites) until they're written.
        val pending = pendingVisitsFor(userId)

        val query = db.collection("visit_history").whereEqualTo("userId", userId)

        val snapshot = try {
            query.get().await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                e.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
            ) {
                // Stale token after an update/restart: refresh and retry once.
                runCatching { user.getIdToken(true).await() }
                runCatching { query.get().await() }.getOrNull()
                    ?: runCatching { query.get(Source.CACHE).await() }.getOrNull()
            } else {
                runCatching { query.get(Source.CACHE).await() }.getOrNull()
            }
        } catch (e: Exception) {
            runCatching { query.get(Source.CACHE).await() }.getOrNull()
        }

        if (snapshot == null && pending.isEmpty()) {
            return Result.failure(IllegalStateException("Couldn't load visit history."))
        }

        // An empty answer that came only from the phone's cache (server
        // unreachable) means "unknown", not "no visits": nothing may have been
        // cached yet. Report it as a failed load so the screen says so.
        if (snapshot != null && snapshot.isEmpty && snapshot.metadata.isFromCache && pending.isEmpty()) {
            return Result.failure(IllegalStateException("Visit history isn't available offline yet."))
        }

        val remote = snapshot?.documents.orEmpty().mapNotNull { document ->
            try {
                document.toObject(VisitHistory::class.java)?.copy(id = document.id)
            } catch (e: Exception) {
                // Readable fields only, rather than losing the visit.
                val siteId = document.getString("siteId") ?: return@mapNotNull null
                VisitHistory(
                    id = document.id,
                    userId = userId,
                    siteId = siteId,
                    visitDate = document.get("visitDate"),
                    reviewEligible = document.getBoolean("reviewEligible") ?: true,
                    reviewSubmitted = document.getBoolean("reviewSubmitted") ?: false,
                    status = document.getString("status") ?: "completed"
                )
            }
        }

        return Result.success(
            (remote + pending).sortedByDescending { visit ->
                (visit.visitDate as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
            }
        )
    }

    /**
     * The signed-in user, waiting briefly for Firebase Auth to finish
     * restoring a saved session on a cold start instead of reading "nobody
     * is signed in" a moment too early.
     */
    private suspend fun awaitSignedInUser(): FirebaseUser? {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it }
        return withTimeoutOrNull(AUTH_RESTORE_WAIT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : FirebaseAuth.AuthStateListener {
                    override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                        val current = firebaseAuth.currentUser ?: return
                        firebaseAuth.removeAuthStateListener(this)
                        if (continuation.isActive) continuation.resume(current)
                    }
                }
                auth.addAuthStateListener(listener)
                continuation.invokeOnCancellation { auth.removeAuthStateListener(listener) }
            }
        }
    }

    suspend fun markReviewSubmitted(visitId: String) {

        if (visitId.isBlank()) {
            return
        }

        try {

            db.collection("visit_history").document(visitId)
                .update("reviewSubmitted", true)
                .await()

        } catch (e: Exception) {
            // Non-critical; the review itself already succeeded.
        }
    }
}
