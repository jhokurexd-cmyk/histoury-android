package com.histoury.app.ui.screens.ar

import com.google.ar.core.Pose
import com.histoury.app.data.model.ArSmokeSettings
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode

/** Owns one tiny unlit GLB (18 quads / two shared 256px textures), separate from the ruins. */
internal class MobileSmokeEffect(
    private val loader: ModelLoader,
    private val instance: ModelInstance,
    private val ruins: ModelNode,
    settings: ArSmokeSettings
) {
    private val config = settings.sanitized()
    private val root = ModelNode(instance, autoAnimate = false)
    private val cards = root.renderableNodes.sortedBy { it.name }
    private var elapsedSeconds = 0.0
    private var previousFrameNanos = 0L
    private var lastUpdateSeconds = -1.0
    private var closed = false

    init {
        require(cards.size == SmokeMotion.MAX_CARDS) { "Unexpected smoke asset card count" }
        root.isVisible = false
        root.isShadowCaster = false
        root.isShadowReceiver = false
        root.isTouchable = false
        root.collisionShape = null
        cards.forEach {
            it.isTouchable = false
            it.collisionShape = null
            it.setGlobalBlendOrderEnabled(true)
            it.materialInstance?.setParameter("baseColorFactor", 1f, 1f, 1f, 0f)
        }
        // A sibling preserves metre-based offsets and doesn't enlarge the model's bounds.
        root.parent = ruins.parent
    }

    fun update(frameNanos: Long, cameraPose: Pose, tracking: Boolean) {
        if (closed) return
        if (!tracking) {
            root.isVisible = false
            previousFrameNanos = 0L
            lastUpdateSeconds = -1.0
            return
        }
        if (previousFrameNanos > 0L) {
            // Don't jump after lifecycle pauses or long tracking interruptions.
            elapsedSeconds += ((frameNanos - previousFrameNanos) / 1e9).coerceIn(0.0, 0.1)
        }
        previousFrameNanos = frameNanos
        if (elapsedSeconds - lastUpdateSeconds < 1.0 / 30.0) return
        lastUpdateSeconds = elapsedSeconds
        root.position = ruins.position
        root.quaternion = ruins.quaternion
        root.isVisible = config.enabled && config.emitters.isNotEmpty() && config.density > 0.0
        if (!root.isVisible) return
        val q = cameraPose.rotationQuaternion
        val faceCamera = Quaternion(q[0], q[1], q[2], q[3])
        cards.forEachIndexed { i, card ->
            card.isVisible = i < config.emitters.size * SmokeMotion.CARDS_PER_PLUME
            if (card.isVisible) {
                val p = SmokeMotion.sample(config, i, elapsedSeconds)
                card.position = Position(p.x, p.y, p.z)
                card.worldQuaternion = faceCamera
                card.scale = Scale(p.size, p.size, 1f)
                card.materialInstance?.setParameter("baseColorFactor", 1f, 1f, 1f, p.opacity)
            }
        }
        // Explicit back-to-front ordering for overlapping transparent cards.
        cards.filter { it.isVisible }.sortedByDescending {
            val p = it.worldPosition
            val dx = p.x - cameraPose.tx(); val dy = p.y - cameraPose.ty(); val dz = p.z - cameraPose.tz()
            dx * dx + dy * dy + dz * dz
        }.forEachIndexed { order, card -> card.setBlendOrder(order) }
    }

    fun destroy() {
        if (closed) return
        closed = true
        root.parent = null
        // gltfio owns every entity, material and texture of this asset. Destroy exactly once.
        loader.destroyModel(instance.asset)
    }
}
