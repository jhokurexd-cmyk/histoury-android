package com.histoury.app.data.offline

import android.content.Context
import java.io.File

/**
 * Where downloaded site assets live on disk.
 *
 * Everything here is in `filesDir`, not `cacheDir`, and that is the entire
 * point of this file.
 *
 * `cacheDir` is documented as disposable: Android deletes it whenever the
 * device runs low on storage, and the user can clear it from Settings with
 * one tap. For a model downloaded over hotel wifi the night before a visit,
 * that is the worst possible behaviour — it disappears exactly when the
 * visitor is standing at the gate with no signal. `filesDir` is never
 * reclaimed by the system; it only goes when the app is uninstalled or its
 * data is cleared deliberately.
 *
 * Files are keyed by a hash of their URL rather than by site. Two things
 * fall out of that for free: replacing a model in the admin panel produces
 * a new timestamped filename, so the key changes and the stale copy is
 * simply never read again; and two sites sharing an asset share one file
 * rather than storing it twice.
 */
object OfflineStore {

    private const val ROOT = "offline"

    /** 3D models, as downloaded GLB files. */
    fun modelsDir(context: Context): File = dir(context, "ar-models")

    /** Reference photographs ARCore matches the structure against. */
    fun referencesDir(context: Context): File = dir(context, "ar-references")

    /** Narration audio. */
    fun audioDir(context: Context): File = dir(context, "voiceover")

    private fun dir(context: Context, name: String): File =
        File(File(context.filesDir, ROOT), name).apply { mkdirs() }

    /**
     * A stable filename for a URL.
     *
     * Hashed rather than derived from the URL's own filename, because
     * Storage download URLs carry query tokens that would make an illegal
     * or absurdly long filename.
     */
    fun keyFor(url: String): String = url.hashCode().toUInt().toString(16)

    fun fileFor(directory: File, url: String, extension: String): File =
        File(directory, "${keyFor(url)}.$extension")

    /** Total bytes currently stored, for a settings screen to report. */
    fun totalBytes(context: Context): Long =
        File(context.filesDir, ROOT)
            .walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    /**
     * Removes everything downloaded.
     *
     * Deliberately all-or-nothing for now: per-site deletion needs a record
     * of which files belong to which site, which is the manifest that comes
     * with the download-for-offline feature rather than with caching.
     */
    fun clear(context: Context): Boolean =
        File(context.filesDir, ROOT).deleteRecursively()
}
