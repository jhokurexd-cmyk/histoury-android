package com.histoury.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone currently has a working internet connection, as a
 * stream: true = online, false = offline. Emits the current state first,
 * then only real changes.
 *
 * "Online" means at least one network with INTERNET capability that Android
 * has VALIDATED — i.e. it actually reaches the internet, not just a Wi-Fi
 * with a login page in front of it.
 */
object ConnectivityObserver {

    /**
     * What kind of connection the phone is using right now, for the
     * "Download over" setting. WIFI covers Wi-Fi and Ethernet; MOBILE is
     * cellular (or anything else that isn't Wi-Fi). NONE means no network
     * that actually reaches the internet.
     */
    enum class NetworkKind { NONE, WIFI, MOBILE }

    fun currentKind(context: Context): NetworkKind {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkKind.WIFI
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkKind.NONE
        return kindOf(caps)
    }

    private fun kindOf(caps: NetworkCapabilities): NetworkKind {
        val usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!usable) return NetworkKind.NONE
        return if (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            NetworkKind.WIFI
        } else {
            NetworkKind.MOBILE
        }
    }

    /**
     * The default network's kind as a stream: the current value first, then
     * every change (Wi-Fi turned off, switching to mobile data, signal lost).
     * The download queue follows this to pause and resume on its own.
     */
    fun observeKind(context: Context): Flow<NetworkKind> = callbackFlow {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(NetworkKind.WIFI)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(kindOf(caps))
            }

            override fun onLost(network: Network) {
                // The default network went away. If another takes over,
                // onCapabilitiesChanged for it follows straight after.
                // (Asking the manager here can still return the lost one.)
                trySend(NetworkKind.NONE)
            }
        }

        trySend(currentKind(appContext))
        runCatching { manager.registerDefaultNetworkCallback(callback) }

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
        .conflate()
        .distinctUntilChanged()

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        // Networks currently usable for the internet.
        val usable = mutableSetOf<Network>()

        fun publish() {
            trySend(usable.isNotEmpty())
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                synchronized(usable) { if (ok) usable.add(network) else usable.remove(network) }
                publish()
            }

            override fun onLost(network: Network) {
                synchronized(usable) { usable.remove(network) }
                publish()
            }

            override fun onUnavailable() {
                publish()
            }
        }

        trySend(isOnline(appContext))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
        .conflate()
        .distinctUntilChanged()
}
