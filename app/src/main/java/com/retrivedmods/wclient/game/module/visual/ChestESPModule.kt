package com.retrivedmods.wclient.game.module.visual

import android.graphics.*
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import com.retrivedmods.wclient.render.RenderOverlayView
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
// Используем правильный импорт пакета для блочных сущностей
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket 
import kotlin.math.cos
import kotlin.math.sin

class ChestESPModule : Module("chest_esp", ModuleCategory.Visual) {

    companion object {
        private var renderView: RenderOverlayView? = null
        fun setRenderView(view: RenderOverlayView) {
            renderView = view
        }
    }

    private val chests = mutableSetOf<Vector3f>()
    private val fov by floatValue("fov", 110f, 40f..110f)

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Проверяем пакет BlockEntityDataPacket (именно он отвечает за сундуки в мире)
        if (packet is BlockEntityDataPacket) {
            val tag = packet.data // В этой библиотеке поле называется .data
            val id = tag.getString("id")
            
            // Если в ID есть слово Chest или Shulker - это наш клиент
            if (id.contains("Chest", ignoreCase = true) || id.contains("Shulker", ignoreCase = true)) {
                val pos = packet.blockPosition
                chests.add(Vector3f.from(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()))
            }
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || chests.isEmpty()) return

        val localPlayer = session.localPlayer
        val viewProj = Matrix4f.createPerspective(fov, canvas.width.toFloat() / canvas.height, 0.1f, 128f)
            .mul(Matrix4f.createTranslation(localPlayer.vec3Position)
                .mul(rotateY(-localPlayer.rotationYaw - 180f))
                .mul(rotateX(-localPlayer.rotationPitch))
                .invert())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.YELLOW
        }

        chests.forEach { pos ->
            drawChestBox(pos, viewProj, canvas, paint)
        }
    }

    private fun drawChestBox(pos: Vector3f, matrix: Matrix4f, canvas: Canvas, paint: Paint) {
        val vertices = arrayOf(
            Vector3f.from(pos.x, pos.y, pos.z),
            Vector3f.from(pos.x + 1f, pos.y, pos.z),
            Vector3f.from(pos.x + 1f, pos.y + 1f, pos.z),
            Vector3f.from(pos.x, pos.y + 1f, pos.z),
            Vector3f.from(pos.x, pos.y, pos.z + 1f),
            Vector3f.from(pos.x + 1f, pos.y, pos.z + 1f),
            Vector3f.from(pos.x + 1f, pos.y + 1f, pos.z + 1f),
            Vector3f.from(pos.x, pos.y + 1f, pos.z + 1f)
        )

        val screenPoints = vertices.mapNotNull { worldToScreen(it, matrix, canvas.width, canvas.height) }
        if (screenPoints.size == 8) {
            val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
            edges.forEach { (a, b) ->
                canvas.drawLine(screenPoints[a].x, screenPoints[a].y, screenPoints[b].x, screenPoints[b].y, paint)
            }
        }
    }

    private fun worldToScreen(pos: Vector3f, m: Matrix4f, w: Int, h: Int): Vector2f? {
        val rw = m.get(3, 0) * pos.x + m.get(3, 1) * pos.y + m.get(3, 2) * pos.z + m.get(3, 3)
        if (rw <= 0.01f) return null
        val inv = 1f / rw
        val x = w / 2f + (m.get(0, 0) * pos.x + m.get(0, 1) * pos.y + m.get(0, 2) * pos.z + m.get(0, 3)) * inv * w / 2f
        val y = h / 2f - (m.get(1, 0) * pos.x + m.get(1, 1) * pos.y + m.get(1, 2) * pos.z + m.get(1, 3)) * inv * h / 2f
        return Vector2f.from(x, y)
    }

    private fun rotateX(a: Float): Matrix4f {
        val r = Math.toRadians(a.toDouble())
        return Matrix4f.from(1f, 0f, 0f, 0f, 0f, cos(r).toFloat(), -sin(r).toFloat(), 0f, 0f, sin(r).toFloat(), cos(r).toFloat(), 0f, 0f, 0f, 0f, 1f)
    }

    private fun rotateY(a: Float): Matrix4f {
        val r = Math.toRadians(a.toDouble())
        return Matrix4f.from(cos(r).toFloat(), 0f, sin(r).toFloat(), 0f, 0f, 1f, 0f, 0f, -sin(r).toFloat(), 0f, cos(r).toFloat(), 0f, 0f, 0f, 0f, 1f)
    }

    override fun onDisabled() {
        chests.clear()
        renderView?.invalidate()
    }
}
