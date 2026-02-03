package com.retrivedmods.wclient.game.module.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import kotlin.math.cos
import kotlin.math.sin

class XrayModule : Module("xray", ModuleCategory.Visual) {

    private val ores = mutableMapOf<Vector3f, Int>()
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val range by floatValue("range", 30f, 10f..64f)

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        if (packet is UpdateBlockPacket) {
            val blockName = packet.definition.toString().lowercase()
            val pos = packet.blockPosition
            val vecPos = Vector3f.from(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

            when {
                blockName.contains("diamond_ore") -> ores[vecPos] = Color.CYAN
                blockName.contains("ancient_debris") -> ores[vecPos] = Color.rgb(139, 69, 19)
                blockName.contains("gold_ore") -> ores[vecPos] = Color.YELLOW
                blockName.contains("emerald_ore") -> ores[vecPos] = Color.GREEN
                blockName.contains("iron_ore") -> ores[vecPos] = Color.rgb(222, 184, 135)
                blockName.contains("air") -> ores.remove(vecPos)
            }
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || ores.isEmpty()) return
        try {
            val localPlayer = session.localPlayer
            val playerPos = localPlayer.vec3Position
            
            ores.keys.removeIf { pos ->
                val dx = pos.x - playerPos.x
                val dy = pos.y - playerPos.y
                val dz = pos.z - playerPos.z
                (dx * dx + dy * dy + dz * dz) > range * range * 1.5
            }
            if (ores.isEmpty()) return

            val viewMatrix = createViewMatrix(playerPos, localPlayer.rotationYaw, localPlayer.rotationPitch)
            val projMatrix = Matrix4f.createPerspective(
                Math.toRadians(fov.toDouble()).toFloat(),
                canvas.width.toFloat() / canvas.height.toFloat(),
                0.05f, range * 2
            )
            val viewProjMatrix = projMatrix.mul(viewMatrix)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }

            ores.forEach { (pos, color) ->
                paint.color = color
                drawBlockBox(pos, viewProjMatrix, canvas, paint, playerPos)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ВОТ ЭТОЙ ФУНКЦИИ НЕ ХВАТАЛО:
    private fun createViewMatrix(position: Vector3f, yaw: Float, pitch: Float): Matrix4f {
        val translation = Matrix4f.createTranslation(-position.x, -position.y, -position.z)
        val rotationPitch = rotateX(-pitch)
        val rotationYaw = rotateY(-yaw - 180f)
        return rotationPitch.mul(rotationYaw).mul(translation)
    }

    private fun drawBlockBox(pos: Vector3f, matrix: Matrix4f, canvas: Canvas, paint: Paint, playerPos: Vector3f) {
        val dx = pos.x - playerPos.x; val dy = pos.y - playerPos.y; val dz = pos.z - playerPos.z
        if ((dx * dx + dy * dy + dz * dz) > range * range) return
        val vertices = arrayOf(
            Vector3f.from(pos.x, pos.y, pos.z), Vector3f.from(pos.x+1, pos.y, pos.z),
            Vector3f.from(pos.x+1, pos.y+1, pos.z), Vector3f.from(pos.x, pos.y+1, pos.z),
            Vector3f.from(pos.x, pos.y, pos.z+1), Vector3f.from(pos.x+1, pos.y, pos.z+1),
            Vector3f.from(pos.x+1, pos.y+1, pos.z+1), Vector3f.from(pos.x, pos.y+1, pos.z+1)
        )
        val screenPoints = vertices.mapNotNull { worldToScreen(it, matrix, canvas.width, canvas.height) }
        if (screenPoints.size == 8) {
             val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
             edges.forEach { (a, b) -> canvas.drawLine(screenPoints[a].x, screenPoints[a].y, screenPoints[b].x, screenPoints[b].y, paint) }
        }
    }

    private fun worldToScreen(pos: Vector3f, viewProj: Matrix4f, width: Int, height: Int): Vector2f? {
        val x = viewProj.get(0, 0) * pos.x + viewProj.get(0, 1) * pos.y + viewProj.get(0, 2) * pos.z + viewProj.get(0, 3)
        val y = viewProj.get(1, 0) * pos.x + viewProj.get(1, 1) * pos.y + viewProj.get(1, 2) * pos.z + viewProj.get(1, 3)
        val w = viewProj.get(3, 0) * pos.x + viewProj.get(3, 1) * pos.y + viewProj.get(3, 2) * pos.z + viewProj.get(3, 3)
        if (w <= 0.01f) return null
        val invW = 1f / w
        return Vector2f.from((x * invW + 1f) * 0.5f * width, (1f - y * invW) * 0.5f * height)
    }

    private fun rotateX(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val c = cos(rad); val s = sin(rad)
        return Matrix4f.from(1f, 0f, 0f, 0f, 0f, c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f)
    }

    private fun rotateY(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val c = cos(rad); val s = sin(rad)
        return Matrix4f.from(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f, 0f, 0f, 0f, 1f)
    }
    
    override fun onDisabled() { ores.clear() }
}
