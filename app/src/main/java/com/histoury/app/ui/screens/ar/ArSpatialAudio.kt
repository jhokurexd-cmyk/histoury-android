package com.histoury.app.ui.screens.ar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentInstance
import com.google.ar.core.Pose
import com.histoury.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Positional ambience for AR reconstructions.
 *
 * Sounds are placed by the model, not by the app: any glTF node whose name
 * starts with `SFX_` is a sound source, and the word after the prefix picks
 * the clip (`SFX_hooves_03` plays [Clip.HOOVES]). Because the position is
 * read from the node's world transform every update, a source parented to
 * something animated — a kalesa, a walking vendor — travels with it, and a
 * model with no `SFX_` nodes simply plays nothing.
 *
 * There is no true 3D audio here, deliberately: SoundPool's per-stream
 * left/right volume is enough to make a passing kalesa sweep across the
 * stereo field and fade with distance, and it costs nothing on a phone that
 * is already rendering the reconstruction.
 *
 * Distances are measured in the model's own metres (world distance divided
 * by the applied scale), so a plaza shrunk onto a tabletop still sounds like
 * a plaza: walking up to the kalesa lane makes the hooves louder by the same
 * proportion it would at full size.
 */
class ArSpatialAudio(context: Context) {

    /**
     * @param refMeters model-space distance at which the clip is at half gain.
     * @param floor gain that never drops away, for ambience that should always
     *   be faintly present wherever the visitor stands.
     * @param intervalSeconds null for a continuous loop; otherwise the range
     *   of gaps between one-shot plays.
     */
    enum class Clip(
        val resId: Int,
        val refMeters: Float,
        val maxGain: Float,
        val floor: Float,
        val intervalSeconds: ClosedFloatingPointRange<Float>?
    ) {
        CROWD(R.raw.ar_crowd, 45f, 0.55f, 0.12f, null),
        HOOVES(R.raw.ar_hooves, 14f, 0.75f, 0f, null),
        BROOM(R.raw.ar_broom, 6f, 0.45f, 0f, null),
        BELLS(R.raw.ar_bells, 160f, 0.8f, 0.25f, 40f..75f),
        DOVES(R.raw.ar_doves, 40f, 0.5f, 0.05f, 9f..22f);

        companion object {
            fun fromNodeName(name: String): Clip? {
                val key = name.removePrefix(SFX_PREFIX).substringBefore('_').substringBefore('.')
                return entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
            }
        }
    }

    private class Source(val entity: Int, val clip: Clip) {
        var streamId = 0
        var nextPlayAt = 0L
        var left = 0f
        var right = 0f
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = HashMap<Clip, Int>()
    private val loaded = HashSet<Int>()
    private var sources: List<Source> = emptyList()
    private var engine: Engine? = null
    private var paused = false
    private var lastUpdateNanos = 0L
    private val world = FloatArray(16)

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        Clip.entries.forEach { clip ->
            runCatching { soundIds[clip] = pool.load(context, clip.resId, 1) }
                .onFailure { Log.w(TAG, "Could not load ${clip.name}", it) }
        }
    }

    /** Finds the model's `SFX_` nodes. Call once the model is in the scene. */
    fun bind(engine: Engine, instance: FilamentInstance) {
        unbind()
        this.engine = engine
        val asset = instance.asset
        val now = System.nanoTime()
        sources = instance.entities.toList().mapNotNull { entity ->
            val name = asset.getName(entity) ?: return@mapNotNull null
            if (!name.startsWith(SFX_PREFIX)) return@mapNotNull null
            val clip = Clip.fromNodeName(name) ?: return@mapNotNull null
            Source(entity, clip).also { source ->
                // Stagger first plays so the bells don't ring the moment the
                // model appears, and several one-shots never start together.
                clip.intervalSeconds?.let { source.nextPlayAt = now + secondsToNanos(Random.nextFloat() * 8f + 4f) }
            }
        }
        Log.d(TAG, "Bound ${sources.size} sound sources")
    }

    fun unbind() {
        sources.forEach { if (it.streamId != 0) pool.stop(it.streamId) }
        sources = emptyList()
        engine = null
    }

    fun pause() {
        paused = true
        pool.autoPause()
    }

    fun resume() {
        paused = false
        pool.autoResume()
    }

    fun release() {
        unbind()
        pool.release()
    }

    /**
     * @param camera the ARCore camera pose, in the same world space as the
     *   scene's nodes.
     * @param modelScale the uniform scale applied to the model, used to turn
     *   world distances back into the reconstruction's own metres.
     */
    fun update(camera: Pose, modelScale: Float) {
        val eng = engine ?: return
        if (paused || sources.isEmpty() || modelScale <= 0f) return

        // Ten updates a second is plenty for volume and pan, and keeps this
        // off the per-frame budget.
        val now = System.nanoTime()
        if (now - lastUpdateNanos < UPDATE_INTERVAL_NANOS) return
        lastUpdateNanos = now

        val tm = eng.transformManager
        val cx = camera.tx(); val cy = camera.ty(); val cz = camera.tz()
        val right = camera.xAxis

        for (source in sources) {
            val soundId = soundIds[source.clip] ?: continue
            if (soundId !in loaded) continue

            val ti = tm.getInstance(source.entity)
            if (ti == 0) continue
            tm.getWorldTransform(ti, world)

            val dx = world[12] - cx; val dy = world[13] - cy; val dz = world[14] - cz
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            val modelMeters = dist / modelScale

            val clip = source.clip
            val r = modelMeters / clip.refMeters
            val gain = (clip.maxGain / (1f + r * r)).coerceAtLeast(clip.floor * clip.maxGain)

            // Equal-power pan from where the source sits across the camera.
            val side = if (dist > 1e-4f) ((dx * right[0] + dy * right[1] + dz * right[2]) / dist).coerceIn(-1f, 1f) else 0f
            val angle = (side + 1f) * (PI.toFloat() / 4f)
            source.left = gain * cos(angle)
            source.right = gain * sin(angle)

            if (clip.intervalSeconds == null) {
                if (source.streamId == 0) {
                    // Loops start at slightly different rates so five kalesas
                    // on the same clip never trot in lockstep.
                    source.streamId = pool.play(soundId, source.left, source.right, 1, -1, Random.nextFloat() * 0.12f + 0.94f)
                } else {
                    pool.setVolume(source.streamId, source.left, source.right)
                }
            } else if (now >= source.nextPlayAt) {
                pool.play(soundId, source.left, source.right, 0, 0, Random.nextFloat() * 0.06f + 0.97f)
                val range = clip.intervalSeconds
                source.nextPlayAt = now + secondsToNanos(range.start + Random.nextFloat() * (range.endInclusive - range.start))
            }
        }
    }

    private fun secondsToNanos(s: Float) = (s * 1_000_000_000L).toLong()

    companion object {
        private const val TAG = "HistouryAR"
        const val SFX_PREFIX = "SFX_"
        private const val UPDATE_INTERVAL_NANOS = 100_000_000L
    }
}
