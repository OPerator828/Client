package com.retrivedmods.wclient.game.module.visual

import android.graphics.*
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket
import kotlin.math.cos
import kotlin.math.sin

class ChestESPModule : Module("chest_esp", ModuleCategory.Visual) {

    private val chests = mutableSetOf<Vector3f>()
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val maxDistance = 64f // Максимальная дистанция отрисовки

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        // Более надежная проверка типа пакета
        when (packet) {
            is BlockEntityDataPacket -> {
                try {
                    val tag = packet.data
                    val pos = packet.blockPosition
                    
                    if (tag != null && pos != null) {
                        val id = tag.getString("id", "")
                        // Проверяем различные варианты ID сундуков
                        if (id.contains("chest", ignoreCase = true) || 
                            id.contains("shulker", ignoreCase = true) ||
                            id == "Chest" || id == "EnderChest" || id == "TrappedChest") {
                            
                            val chestPos = Vector3f.from(
                                pos.x.toFloat() + 0.5f, // Центрируем
                                pos.y.toFloat(),
                                pos.z.toFloat() + 0.5f
                            )
                            chests.add(chestPos)
                        }
                    }
                } catch (e: Exception) {
                    // Логируем ошибку для отладки
                    e.printStackTrace()
                }
            }
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || chests.isEmpty()) return

        try {
            val localPlayer = session.localPlayer
            val playerPos = localPlayer.vec3Position
            
            // Очищаем сундуки, которые слишком далеко
            chests.removeIf { chest ->
                val dx = chest.x - playerPos.x
                val dy = chest.y - playerPos.y
                val dz = chest.z - playerPos.z
                (dx * dx + dy * dy + dz * dz) > maxDistance * maxDistance
            }

            if (chests.isEmpty()) return

            // Создаем правильную view-projection матрицу
            val viewMatrix = createViewMatrix(
                playerPos,
                localPlayer.rotationYaw,
                localPlayer.rotationPitch
            )
            
            val projMatrix = Matrix4f.createPerspective(
                Math.toRadians(fov.toDouble()).toFloat(),
                canvas.width.toFloat() / canvas.height.toFloat(),
                0.05f,
                maxDistance * 2
            )
            
            val viewProjMatrix = projMatrix.mul(viewMatrix)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = Color.YELLOW
            }

            chests.forEach { pos ->
                drawChestBox(pos, viewProjMatrix, canvas, paint, playerPos)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createViewMatrix(
        position: Vector3f,
        yaw: Float,
        pitch: Float
    ): Matrix4f {
        // Правильная последовательность трансформаций для view матрицы
        val translation = Matrix4f.createTranslation(
            -position.x,
            -position.y,
            -position.z
        )
        
        val rotationPitch = rotateX(-pitch)
        val rotationYaw = rotateY(-yaw - 180f)
        
        return rotationPitch.mul(rotationYaw).mul(translation)
    }

    private fun drawChestBox(
        pos: Vector3f,
        matrix: Matrix4f,
        canvas: Canvas,
        paint: Paint,
        playerPos: Vector3f
    ) {
        // Проверяем дистанцию
        val dx = pos.x - playerPos.x
        val dy = pos.y - playerPos.y
        val dz = pos.z - playerPos.z
        val distSq = dx * dx + dy * dy + dz * dz
        
        if (distSq > maxDistance * maxDistance) return

        // Вершины куба (размер блока Minecraft = 1)
        val vertices = arrayOf(
            Vector3f.from(pos.x - 0.5f, pos.y, pos.z - 0.5f),
            Vector3f.from(pos.x + 0.5f, pos.y, pos.z - 0.5f),
            Vector3f.from(pos.x + 0.5f, pos.y + 1f, pos.z - 0.5f),
            Vector3f.from(pos.x - 0.5f, pos.y + 1f, pos.z - 0.5f),
            Vector3f.from(pos.x - 0.5f, pos.y, pos.z + 0.5f),
            Vector3f.from(pos.x + 0.5f, pos.y, pos.z + 0.5f),
            Vector3f.from(pos.x + 0.5f, pos.y + 1f, pos.z + 0.5f),
            Vector3f.from(pos.x - 0.5f, pos.y + 1f, pos.z + 0.5f)
        )

        val screenPoints = vertices.mapNotNull { worldToScreen(it, matrix, canvas.width, canvas.height) }
        
        if (screenPoints.size != 8) return // Не все точки видны

        // Линии куба (12 ребер)
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0, // Передняя грань
            4 to 5, 5 to 6, 6 to 7, 7 to 4, // Задняя грань
            0 to 4, 1 to 5, 2 to 6, 3 to 7  // Соединяющие линии
        )

        // Изменяем цвет в зависимости от расстояния
        val distance = kotlin.math.sqrt(distSq)
        val alpha = (255 * (1f - distance / maxDistance)).toInt().coerceIn(50, 255)
        paint.alpha = alpha

        edges.forEach { (a, b) ->
            canvas.drawLine(
                screenPoints[a].x,
                screenPoints[a].y,
                screenPoints[b].x,
                screenPoints[b].y,
                paint
            )
        }
    }

    private fun worldToScreen(pos: Vector3f, viewProj: Matrix4f, width: Int, height: Int): Vector2f? {
        // Применяем матрицу преобразования
        val x = viewProj.get(0, 0) * pos.x + viewProj.get(0, 1) * pos.y + 
                viewProj.get(0, 2) * pos.z + viewProj.get(0, 3)
        val y = viewProj.get(1, 0) * pos.x + viewProj.get(1, 1) * pos.y + 
                viewProj.get(1, 2) * pos.z + viewProj.get(1, 3)
        val w = viewProj.get(3, 0) * pos.x + viewProj.get(3, 1) * pos.y + 
                viewProj.get(3, 2) * pos.z + viewProj.get(3, 3)

        // Проверка на то, что точка за камерой
        if (w <= 0.01f) return null

        val invW = 1f / w
        
        // NDC to screen coordinates
        val screenX = (x * invW + 1f) * 0.5f * width
        val screenY = (1f - y * invW) * 0.5f * height

        // Проверка на выход за границы экрана
        if (screenX < -100 || screenX > width + 100 || 
            screenY < -100 || screenY > height + 100) {
            return null
        }

        return Vector2f.from(screenX, screenY)
    }

    private fun rotateX(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        return Matrix4f.from(
            1f, 0f, 0f, 0f,
            0f, c, -s, 0f,
            0f, s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun rotateY(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        return Matrix4f.from(
            c, 0f, s, 0f,
            0f, 1f, 0f, 0f,
            -s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    override fun onDisabled() {
        chests.clear()
    }
}
