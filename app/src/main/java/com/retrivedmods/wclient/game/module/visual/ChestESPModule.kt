package com.retrivedmods.wclient.game.module.visual

import android.graphics.*
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ChestESPModule : Module("chest_esp", ModuleCategory.Visual) {

    private val chests = mutableSetOf<Vector3f>()
    // Настройки: FOV (угол обзора) и максимальная дистанция отрисовки
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val maxDistance = 64f 

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        try {
            // СПОСОБ 1: Ловим обновление блоков (когда блок ставится или прогружается)
            if (packet is UpdateBlockPacket) {
                // definition.toString() возвращает имя блока, например "minecraft:chest"
                val blockName = packet.definition.toString().lowercase()
                
                if (blockName.contains("chest") || blockName.contains("shulker")) {
                    val pos = packet.blockPosition
                    // Центрируем позицию (+0.5), чтобы квадрат был ровно на блоке
                    val chestPos = Vector3f.from(
                        pos.x.toFloat() + 0.5f,
                        pos.y.toFloat(),
                        pos.z.toFloat() + 0.5f
                    )
                    chests.add(chestPos)
                }
            }
            
            // СПОСОБ 2: Ловим данные сущности блока (NBT теги) - самый надежный способ
            if (packet is BlockEntityDataPacket) {
                val tag = packet.data
                // Безопасное получение ID
                val id = tag?.getString("id")?.lowercase() ?: ""
                
                if (id.contains("chest") || id.contains("shulker")) {
                    val pos = packet.blockPosition
                    val chestPos = Vector3f.from(
                        pos.x.toFloat() + 0.5f,
                        pos.y.toFloat(),
                        pos.z.toFloat() + 0.5f
                    )
                    chests.add(chestPos)
                }
            }

        } catch (e: Exception) {
            // Игнорируем ошибки пакетов, чтобы не крашить игру
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated) return

        try {
            val localPlayer = session.localPlayer
            val playerPos = localPlayer.vec3Position
            
            // Очищаем список от сундуков, которые слишком далеко
            // Это важно для производительности
            if (chests.isNotEmpty()) {
                chests.removeIf { chest ->
                    val dx = chest.x - playerPos.x
                    val dy = chest.y - playerPos.y
                    val dz = chest.z - playerPos.z
                    (dx * dx + dy * dy + dz * dz) > (maxDistance * maxDistance * 1.5) // Запас 1.5x
                }
            }

            if (chests.isEmpty()) return

            // Математика камеры
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

            // Настройка кисти (желтые линии)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.YELLOW
            }

            // Рисуем каждый сундук
            chests.forEach { pos ->
                drawChestBox(pos, viewProjMatrix, canvas, paint, playerPos)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createViewMatrix(position: Vector3f, yaw: Float, pitch: Float): Matrix4f {
        val translation = Matrix4f.createTranslation(-position.x, -position.y, -position.z)
        val rotationPitch = rotateX(-pitch)
        val rotationYaw = rotateY(-yaw - 180f) // Поправка на 180 градусов для Bedrock
        return rotationPitch.mul(rotationYaw).mul(translation)
    }

    private fun drawChestBox(pos: Vector3f, matrix: Matrix4f, canvas: Canvas, paint: Paint, playerPos: Vector3f) {
        // Проверка дистанции для конкретного сундука
        val dx = pos.x - playerPos.x
        val dy = pos.y - playerPos.y
        val dz = pos.z - playerPos.z
        val distSq = dx * dx + dy * dy + dz * dz
        
        if (distSq > maxDistance * maxDistance) return

        // Вершины куба (размером 1 блок) вокруг центра
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

        // Проецируем 3D точки на 2D экран
        val screenPoints = vertices.mapNotNull { worldToScreen(it, matrix, canvas.width, canvas.height) }
        
        // Рисуем только если видны все 8 углов (или можно упростить до drawLine если часть видна)
        if (screenPoints.size == 8) {
            val edges = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 0, // Низ (или верх, зависит от Y)
                4 to 5, 5 to 6, 6 to 7, 7 to 4, // Верх
                0 to 4, 1 to 5, 2 to 6, 3 to 7  // Стойки
            )

            // Прозрачность зависит от дистанции
            val distance = sqrt(distSq)
            val alpha = (255 * (1f - distance / maxDistance)).toInt().coerceIn(40, 255)
            paint.alpha = alpha

            edges.forEach { (a, b) ->
                canvas.drawLine(
                    screenPoints[a].x, screenPoints[a].y,
                    screenPoints[b].x, screenPoints[b].y,
                    paint
                )
            }
        }
    }

    private fun worldToScreen(pos: Vector3f, viewProj: Matrix4f, width: Int, height: Int): Vector2f? {
        val x = viewProj.get(0, 0) * pos.x + viewProj.get(0, 1) * pos.y + viewProj.get(0, 2) * pos.z + viewProj.get(0, 3)
        val y = viewProj.get(1, 0) * pos.x + viewProj.get(1, 1) * pos.y + viewProj.get(1, 2) * pos.z + viewProj.get(1, 3)
        val w = viewProj.get(3, 0) * pos.x + viewProj.get(3, 1) * pos.y + viewProj.get(3, 2) * pos.z + viewProj.get(3, 3)

        if (w <= 0.01f) return null // Точка за камерой

        val invW = 1f / w
        val screenX = (x * invW + 1f) * 0.5f * width
        val screenY = (1f - y * invW) * 0.5f * height

        return Vector2f.from(screenX, screenY)
    }

    private fun rotateX(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        return Matrix4f.from(1f, 0f, 0f, 0f, 0f, c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f)
    }

    private fun rotateY(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble()).toFloat()
        val c = cos(rad)
        val s = sin(rad)
        return Matrix4f.from(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f, 0f, 0f, 0f, 1f)
    }

    override fun onDisabled() {
        chests.clear()
    }
}
