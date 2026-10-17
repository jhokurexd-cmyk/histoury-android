package com.histoury.app.data.offline

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Download over" — which connections a download the visitor starts may use. */
enum class DownloadNetwork(val label: String, val wireValue: String) {
    WIFI_ONLY("Wi-Fi only", "wifi"),
    WIFI_OR_MOBILE("Wi-Fi or mobile data", "any");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.wireValue == value } ?: WIFI_ONLY
    }
}

/** "Automatic resource updates" — only for sites that are already downloaded. */
enum class AutoUpdateMode(val label: String, val wireValue: String) {
    WIFI_ONLY("Wi-Fi only", "wifi"),
    WIFI_OR_MOBILE("Wi-Fi or mobile data", "any"),
    OFF("Off", "off");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.wireValue == value } ?: WIFI_ONLY
    }
}

/**
 * Offline Downloads settings, kept on the device (SharedPreferences, like
 * ThemePreference and OnboardingPreference).
 *
 * Both default to Wi-Fi only: an AR model runs to tens of megabytes, and a
 * visitor on a prepaid data plan shouldn't find it gone because a download
 * started while they were on mobile data.
 *
 * Also remembers whether the one-time "Download Resources" pop-up has been
 * dealt with, so it isn't shown again on every launch.
 */
object DownloadPreferences {

    private const val PREFS_NAME = "histoury_offline_downloads"
    private const val KEY_DOWNLOAD_OVER = "download_over"
    private const val KEY_AUTO_UPDATES = "auto_updates"
    private const val KEY_PROMPT_HANDLED = "resource_prompt_handled"
    private const val KEY_PROMPT_KNOWN_SITES = "resource_prompt_known_sites"
    private const val KEY_AUTO_DOWNLOAD_WIFI = "auto_download_wifi"
    private const val KEY_AUTO_DOWNLOAD_EXCLUDED = "auto_download_excluded"

    private val _downloadOver = MutableStateFlow(DownloadNetwork.WIFI_ONLY)
    val downloadOver: StateFlow<DownloadNetwork> = _downloadOver.asStateFlow()

    private val _autoUpdates = MutableStateFlow(AutoUpdateMode.WIFI_ONLY)
    val autoUpdates: StateFlow<AutoUpdateMode> = _autoUpdates.asStateFlow()

    private val _autoDownloadOnWifi = MutableStateFlow(true)

    /**
     * "Auto-download on Wi-Fi": while on Wi-Fi, every AR site not yet saved
     * downloads in the background by itself (see AutoDownloader). On by
     * default; never uses mobile data.
     */
    val autoDownloadOnWifi: StateFlow<Boolean> = _autoDownloadOnWifi.asStateFlow()

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Reads the stored settings once. Safe to call from anywhere, repeatedly. */
    fun load(context: Context) {
        if (loaded) return
        val prefs = prefs(context)
        _downloadOver.value = DownloadNetwork.from(prefs.getString(KEY_DOWNLOAD_OVER, null))
        _autoUpdates.value = AutoUpdateMode.from(prefs.getString(KEY_AUTO_UPDATES, null))
        _autoDownloadOnWifi.value = prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, true)
        loaded = true
    }

    fun downloadOver(context: Context): DownloadNetwork {
        load(context)
        return _downloadOver.value
    }

    fun autoUpdates(context: Context): AutoUpdateMode {
        load(context)
        return _autoUpdates.value
    }

    fun setDownloadOver(context: Context, value: DownloadNetwork) {
        load(context)
        _downloadOver.value = value
        prefs(context).edit().putString(KEY_DOWNLOAD_OVER, value.wireValue).apply()
        // A download waiting for Wi-Fi may be allowed to go now.
        OfflineSiteManager.onSettingsChanged(context)
    }

    fun setAutoUpdates(context: Context, value: AutoUpdateMode) {
        load(context)
        _autoUpdates.value = value
        prefs(context).edit().putString(KEY_AUTO_UPDATES, value.wireValue).apply()
        OfflineSiteManager.onSettingsChanged(context)
    }

    fun autoDownloadOnWifi(context: Context): Boolean {
        load(context)
        return _autoDownloadOnWifi.value
    }

    fun setAutoDownloadOnWifi(context: Context, enabled: Boolean) {
        load(context)
        _autoDownloadOnWifi.value = enabled
        prefs(context).edit().putBoolean(KEY_AUTO_DOWNLOAD_WIFI, enabled).apply()
        if (!enabled) OfflineSiteManager.dropAutoDownloads(context)
    }

    /**
     * Sites the visitor cancelled or removed. Auto-download skips them, so a
     * removed site doesn't come back on the next Wi-Fi connection. Tapping
     * Download on one takes it off this list again.
     */
    fun autoDownloadExcluded(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_AUTO_DOWNLOAD_EXCLUDED, emptySet()).orEmpty()

    fun excludeFromAutoDownload(context: Context, siteId: String) {
        val current = autoDownloadExcluded(context)
        if (siteId in current) return
        prefs(context).edit().putStringSet(KEY_AUTO_DOWNLOAD_EXCLUDED, current + siteId).apply()
    }

    fun includeInAutoDownload(context: Context, siteId: String) {
        val current = autoDownloadExcluded(context)
        if (siteId !in current) return
        prefs(context).edit().putStringSet(KEY_AUTO_DOWNLOAD_EXCLUDED, current - siteId).apply()
    }

    // ----- the one-time "Download Resources" pop-up -----

    fun isResourcePromptHandled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PROMPT_HANDLED, false)

    /** AR sites that existed when the visitor last answered the pop-up. */
    fun resourcePromptKnownSites(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PROMPT_KNOWN_SITES, emptySet()).orEmpty()

    /**
     * Records that the visitor answered the pop-up (any button, including
     * Close), together with the AR sites that existed at the time. It is only
     * offered again when a new AR experience is published after that.
     */
    fun markResourcePromptHandled(context: Context, knownArSiteIds: Set<String>) {
        prefs(context).edit()
            .putBoolean(KEY_PROMPT_HANDLED, true)
            .putStringSet(KEY_PROMPT_KNOWN_SITES, knownArSiteIds.toSet())
            .apply()
    }
}
