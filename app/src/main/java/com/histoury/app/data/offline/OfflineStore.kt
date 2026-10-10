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

    /** Largest model accepted; anything bigger is treated as a bad download. */
    private const val MAX_GLB_BYTES = 250L * 1024L * 1024L

    /**
     * True when [file] is a whole, readable GLB.
     *
     * A GLB starts with a 12-byte header: the magic "glTF", a version, and
     * the total length of the file. Checking that length (not just the
     * magic) is what catches a model cut off halfway by a dropped
     * connection — its header is intact but the file is shorter than the
     * header says, and loading it would fail on site with no signal to
     * fetch it again.
     */
    fun isValidGlb(file: File): Boolean {
        if (!file.isFile || file.length() < 12L || file.length() > MAX_GLB_BYTES) return false

        return runCatching {
            file.inputStream().buffered().use { input ->
                val header = ByteArray(12)
                var read = 0
                while (read < 12) {
                    val n = input.read(header, read, 12 - read)
                    if (n < 0) break
                    read += n
                }
                if (read < 12) return@use false
                val magicOk = header[0] == 0x67.toByte() && header[1] == 0x6C.toByte() &&
                    header[2] == 0x54.toByte() && header[3] == 0x46.toByte() // "glTF"
                val declared = (header[8].toLong() and 0xFF) or
                    ((header[9].toLong() and 0xFF) shl 8) or
                    ((header[10].toLong() and 0xFF) shl 16) or
                    ((header[11].toLong() and 0xFF) shl 24)
                // Shorter than declared = truncated. Longer is tolerated
                // (some exporters pad the end), so a loadable model is never
                // rejected over it.
                magicOk && (declared <= 0L || file.length() >= declared)
            }
        }.getOrDefault(false)
    }

    /** Whether a downloaded asset looks whole: a valid GLB, or any non-empty file. */
    fun isUsable(file: File): Boolean =
        if (file.extension.equals("glb", ignoreCase = true)) isValidGlb(file)
        else file.isFile && file.length() > 0L

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
