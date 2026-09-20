package com.histoury.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Application entry point. Its only job is to configure the two caches that
 * make Histoury usable without a connection:
 *
 *  - Firestore's local cache, resized to unlimited so site records and
 *    site_content documents are never garbage-collected out from under a
 *    tourist who is standing inside Intramuros with no signal.
 *
 *  - Coil's disk cache, moved out of the volatile cache directory and given
 *    a fixed budget, so featured images and thumbnails survive both the
 *    system's low-storage cleanup and Firebase Storage's short cache
 *    headers.
 *
 * Both have to be set up here, before anything else touches Firestore or
 * loads an image, which is why this class exists at all.
 */
class HistouryApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        configureFirestoreCache()
    }

    /**
     * Firestore keeps a local copy of every document it has read, and serves
     * reads from it whenever the server is unreachable — that part is on by
     * default. What is *not* on by default is keeping it: the cache is
     * capped at 100MB and least-recently-used documents are evicted to stay
     * under that cap.
     *
     * Intramuros' entire text corpus is a few megabytes, so we lift the cap
     * instead and let nothing be evicted.
     */
    private fun configureFirestoreCache() {

        try {

            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build()
                )
                .build()

            FirebaseFirestore.getInstance().firestoreSettings = settings

            // If PersistentCacheSettings is unresolved on an older Firestore
            // version, swap the block above for the legacy equivalent:
            //
            //   FirebaseFirestoreSettings.Builder()
            //       .setPersistenceEnabled(true)
            //       .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            //       .build()

        } catch (e: IllegalStateException) {

            // Thrown if something already used Firestore before we got here.
            // Not fatal — the default cache is still active, just capped.
        }
    }

    /**
     * Coil picks this up automatically because the Application implements
     * ImageLoaderFactory; no call sites need to change.
     *
     * Two deliberate choices:
     *
     *  - The disk cache lives under filesDir, not cacheDir. Android clears
     *    cacheDir on its own when storage runs low, which is exactly the
     *    moment before a site visit that you do not want your images gone.
     *
     *  - respectCacheHeaders(false). Firebase Storage serves download URLs
     *    with a short max-age unless the object's cacheControl metadata is
     *    set explicitly, and honouring that means Coil re-validates over the
     *    network and fails when offline. Ignoring the headers makes a cached
     *    image a cached image. Site photos are effectively immutable — a new
     *    upload gets a new URL — so there is nothing to go stale.
     */
    override fun newImageLoader(): ImageLoader {

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve(OFFLINE_IMAGE_DIR))
                    .maxSizeBytes(OFFLINE_IMAGE_BUDGET_BYTES)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    companion object {

        /** Coil owns this directory exclusively — nothing else may write here. */
        const val OFFLINE_IMAGE_DIR = "offline_images"

        /**
         * 250MB. Intramuros' full image set is well under this; the headroom
         * is for user gallery photos and profile pictures.
         */
        const val OFFLINE_IMAGE_BUDGET_BYTES = 250L * 1024L * 1024L
    }
}
