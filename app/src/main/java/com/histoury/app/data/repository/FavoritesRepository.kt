package com.histoury.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.histoury.app.util.ConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in visitor's favorite historical sites.
 *
 * Stored on their own profile, users/{uid}.favoriteSiteIds, as an array of
 * historical_sites document ids. Read here with a live listener; written
 * through the setFavoriteSite Cloud Function (arrayUnion / arrayRemove), so
 * a double tap can never create a duplicate entry.
 *
 * One process-wide object with ONE snapshot listener, re-attached only when
 * the signed-in account changes. Every heart in the app — Home, Site
 * Information, the map, the Favorites page — reads [favoriteIds], so they
 * all agree the moment one of them is tapped, and no screen opens a listener
 * of its own. A tap shows immediately; if the server refuses it the heart is
 * put back and the visitor is told.
 *
 * OFFLINE
 * A tap with no signal is kept on the phone (per account) and sent when the
 * phone is back online, instead of being undone. Each saved tap is an
 * explicit state ("favorite: true/false" for one site), not a toggle, so
 * sending it late or twice can't flip anything the wrong way; the last tap
 * on this phone wins. Until it is sent, the hearts show the tap on top of
 * what the server says.
 */
object FavoritesRepository {

    private const val TAG = "Favorites"

    private val db = FirebaseFirestore.getInstance()

    // Same region as the app's other Cloud Functions.
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")

    private val _favoriteIds = MutableStateFlow<List<String>>(emptyList())

    /** Newest first. */
    val favoriteIds: StateFlow<List<String>> = _favoriteIds.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** False until the first answer (from the cache or the server) arrives. */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private var registration: ListenerRegistration? = null

    /** The list as the server last reported it, before pending taps are applied. */
    @Volatile
    private var serverIds: List<String> = emptyList()

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private const val PENDING_PREFS = "histoury_pending_favorites"

    /**
     * Call once from Application.onCreate: lets taps made offline be kept
     * on the phone and sent whenever the connection comes back.
     */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        scope.launch {
            ConnectivityObserver.observe(app)
                .distinctUntilChanged()
                .filter { online -> online }
                .collect { flushPending() }
        }
    }

    // ----- taps waiting to be sent: siteId -> favorite -----

    @Synchronized
    private fun pendingFor(uid: String): MutableMap<String, Boolean> {
        val context = appContext ?: return mutableMapOf()
        val raw = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE).getString(uid, null)
            ?: return mutableMapOf()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getBoolean(it) }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    @Synchronized
    private fun savePending(uid: String, pending: Map<String, Boolean>) {
        val context = appContext ?: return
        val edit = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE).edit()
        if (pending.isEmpty()) edit.remove(uid) else edit.putString(uid, JSONObject(pending).toString())
        edit.apply()
    }

    @Synchronized
    private fun setPending(uid: String, siteId: String, favorite: Boolean?) {
        val pending = pendingFor(uid)
        if (favorite == null) pending.remove(siteId) else pending[siteId] = favorite
        savePending(uid, pending)
    }

    /** The server's list with this account's unsent taps applied on top. */
    private fun withPending(uid: String, ids: List<String>): List<String> {
        val pending = pendingFor(uid)
        if (pending.isEmpty()) return ids
        var result = ids
        pending.forEach { (siteId, favorite) ->
            result = if (favorite) {
                if (siteId in result) result else listOf(siteId) + result
            } else {
                result - siteId
            }
        }
        return result
    }

    /** True when [error] means "couldn't reach the server", not "refused". */
    private fun isConnectionProblem(error: Exception): Boolean {
        val context = appContext
        if (context != null && !ConnectivityObserver.isOnline(context)) return true
        val code = (error as? FirebaseFunctionsException)?.code
        return code == FirebaseFunctionsException.Code.UNAVAILABLE ||
            code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
            error is java.io.IOException ||
            error.cause is java.io.IOException
    }

    /** Sends every tap saved while offline. Safe to call any time. */
    fun flushPending() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pending = pendingFor(uid)
        if (pending.isEmpty()) return
        pending.forEach { (siteId, favorite) ->
            functions.getHttpsCallable("setFavoriteSite")
                .call(mapOf("siteId" to siteId, "favorite" to favorite))
                .addOnSuccessListener {
                    // Only if no newer tap replaced it meanwhile.
                    if (pendingFor(uid)[siteId] == favorite) setPending(uid, siteId, null)
                }
                .addOnFailureListener { error ->
                    if (isConnectionProblem(error)) return@addOnFailureListener // try again next time
                    // Refused (site removed, list full...): drop it and show
                    // what the server actually has.
                    Log.w(TAG, "Pending favorite for $siteId refused", error)
                    if (pendingFor(uid)[siteId] == favorite) setPending(uid, siteId, null)
                    if (FirebaseAuth.getInstance().currentUser?.uid == uid) {
                        _favoriteIds.value = withPending(uid, serverIds)
                    }
                }
        }
    }
    private var listeningUid: String? = null

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            attach(auth.currentUser?.uid)
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var retryDelayMs = INITIAL_RETRY_MS

    @Synchronized
    private fun attach(uid: String?) {

        if (uid == listeningUid && (uid == null || registration != null)) return

        val sameAccount = uid == listeningUid

        registration?.remove()
        registration = null
        listeningUid = uid

        // Re-attaching for the SAME account after an error keeps the hearts
        // as they are; only a change of account starts from empty.
        if (!sameAccount) {
            serverIds = emptyList()
            _favoriteIds.value = emptyList()
            _isLoaded.value = uid == null
            retryDelayMs = INITIAL_RETRY_MS
        }

        if (uid == null) return

        registration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // A Firestore listener that reports an error is FINISHED
                    // — it never delivers another update. The old comment
                    // here assumed it recovered by itself; it doesn't, so
                    // after one connection error the hearts stopped
                    // updating for the rest of the session. Keep what's
                    // showing and attach a new listener after a back-off.
                    Log.w(TAG, "Favorites listener stopped; retrying", error)
                    _isLoaded.value = true
                    scheduleReattach(uid)
                    return@addSnapshotListener
                }
                retryDelayMs = INITIAL_RETRY_MS
                val ids = (snapshot?.get("favoriteSiteIds") as? List<*>)
                    .orEmpty()
                    .filterIsInstance<String>()
                    .distinct()
                    .asReversed()
                serverIds = ids
                _favoriteIds.value = withPending(uid, ids)
                _isLoaded.value = true
            }
    }

    @Synchronized
    private fun scheduleReattach(uid: String) {
        registration?.remove()
        registration = null
        val delay = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
        mainHandler.postDelayed({
            // Only if still the same account and nothing re-attached since.
            if (FirebaseAuth.getInstance().currentUser?.uid == uid && listeningUid == uid) attach(uid)
        }, delay)
    }

    private const val INITIAL_RETRY_MS = 2_000L
    private const val MAX_RETRY_MS = 60_000L

    fun isFavorite(siteDocumentId: String): Boolean = siteDocumentId in _favoriteIds.value

    /**
     * Adds or removes a site. The heart changes at once; if the write is
     * refused (e.g. signed out, or the server rejects it) the change is put
     * back and [onError] says so.
     */
    fun toggle(siteDocumentId: String, onError: (String) -> Unit = {}) {

        if (siteDocumentId.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onError("Sign in to save favorites.")
            return
        }

        // Make sure the listener is attached for this account (cold start).
        attach(uid)

        val before = _favoriteIds.value
        val adding = siteDocumentId !in before

        _favoriteIds.value = if (adding) listOf(siteDocumentId) + before else before - siteDocumentId

        // This tap is now the latest word on this site: an older unsent
        // offline tap for it must not be sent after it.
        setPending(uid, siteDocumentId, null)

        // Saved through the setFavoriteSite Cloud Function: the Firestore
        // rules don't let the app write fields on users/{uid} directly, which
        // is why every tap used to fail. The function sets an explicit state
        // (not a toggle), so a retry or double tap can't flip it back.
        functions.getHttpsCallable("setFavoriteSite")
            .call(mapOf("siteId" to siteDocumentId, "favorite" to adding))
            .addOnFailureListener { error ->
                Log.w(TAG, "setFavoriteSite failed", error)
                if (appContext != null && isConnectionProblem(error)) {
                    // No signal: keep the heart as tapped and send it later.
                    setPending(uid, siteDocumentId, adding)
                    onError(
                        if (adding) "Added on this phone. It will sync when you're back online."
                        else "Removed on this phone. It will sync when you're back online."
                    )
                    return@addOnFailureListener
                }
                // Only undo this tap, not anything that changed since.
                val now = _favoriteIds.value
                _favoriteIds.value = if (adding) now - siteDocumentId else listOf(siteDocumentId) + now
                onError(failureMessage(error, adding))
            }
    }

    /**
     * The function's own messages are written for visitors (rate limit, site
     * removed, list full); anything else — no connection, server trouble —
     * gets a plain retry message.
     */
    private fun failureMessage(error: Exception, adding: Boolean): String {
        val serverMessage = (error as? FirebaseFunctionsException)
            ?.takeIf {
                it.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ||
                    it.code == FirebaseFunctionsException.Code.NOT_FOUND ||
                    it.code == FirebaseFunctionsException.Code.UNAUTHENTICATED ||
                    it.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT
            }
            ?.message
        return serverMessage ?: if (adding) {
            "Couldn't add to Favorites. Check your connection and try again."
        } else {
            "Couldn't remove from Favorites. Check your connection and try again."
        }
    }
}
