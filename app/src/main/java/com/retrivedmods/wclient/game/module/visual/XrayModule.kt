package com.retrivedmods.wclient.game.module.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import kotlin.math.*

class XrayModule : Module("xray", ModuleCategory.Visual) {

    private val ores = mutableMapOf<Vector3f, Int>()
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val range by floatValue("range", 30f, 10f..64f)
    
    // Кэш для блоков
    private val trackedOres = setOf(
        "diamond_ore", "ancient_debris", "gold_ore", 
        "emerald_ore", "iron_ore", "lapis_ore", "redstone_ore"
    )

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        if (packet is UpdateBlockPacket) {
            val blockName = packet.definition.toString().lowercase()
            val pos = packet.blockPosition
            // Используем toDouble() для преобразования Int в Double
            val vecPos = Vector3f.from(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
            
            // Проверяем, является ли блок рудой
            val isOre = trackedOres.any { blockName.contains(it) }
            
            if (isOre) {
                // Назначаем цвет в зависимости от типа руды
                ores[vecPos] = when {
                    blockName.contains("diamond_ore") -> Color.CYAN
                    blockName.contains("ancient_debris") -> Color.rgb(139, 69, 19) // коричневый
                    blockName.contains("gold_ore") -> Color.YELLOW
                    blockName.contains("emerald_ore") -> Color.GREEN
                    blockName.contains("iron_ore") -> Color.rgb(255, 200, 150) // светло-оранжевый
                    blockName.contains("lapis_ore") -> Color.BLUE
                    blockName.contains("redstone_ore") -> Color.RED
                    else -> Color.WHITE
                }
            } else if (blockName.contains("air")) {
                // Удаляем блок, если он заменен воздухом
                ores.remove(vecPos)
            }
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || ores.isEmpty()) return
        try {
            val localPlayer = session.localPlayer
            val playerPos = localPlayer.vec3Position
            
            // Очищаем дальние блоки
            ores.keys.removeIf { pos ->
                val dx = pos.x - playerPos.x
                val dy = pos.y - playerPos.y
                val dz = pos.z - playerPos.z
                (dx * dx + dy * dy + dz * dz) > range * range
            }
            
            if (ores.isEmpty()) return

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { 
                style = Paint.Style.STROKE
                strokeWidth = 4f
                alpha = 180
            }
            
            val screenWidth = canvas.width
            val screenHeight = canvas.height
            
            ores.forEach { (pos, color) ->
                paint.color = color
                // Рисуем маркер вместо 3D куба (более простой подход)
                drawOreMarker(pos, playerPos, localPlayer.rotationYaw, 
                    localPlayer.rotationPitch, canvas, paint, screenWidth, screenHeight)
            }
        } catch (e: Exception) { 
            e.printStackTrace() 
        }
    }

    private fun drawOreMarker(
        blockPos: Vector3f, 
        playerPos: Vector3f,
        yaw: Float,
        pitch: Float,
        canvas: Canvas,
        paint: Paint,
        screenWidth: Int,
        screenHeight: Int
    ) {
        // Вычисляем относительную позицию блока
        val dx = blockPos.x - playerPos.x
        val dy = blockPos.y - playerPos.y - 1.62f // высота глаз игрока
        val dz = blockPos.z - playerPos.z
        
        // Преобразуем углы в радианы
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        
        // Вращение по горизонтали (yaw)
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)
        
        // Вращение по вертикали (pitch)
        val sinPitch = sin(pitchRad)
        val cosPitch = cos(pitchRad)
        
        // Преобразование в систему координат камеры
        val rotatedX = cosYaw * dx - sinYaw * dz
        val rotatedZ = sinYaw * dx + cosYaw * dz
        
        val rotatedY = cosPitch * dy - sinPitch * rotatedZ
        val finalZ = sinPitch * dy + cosPitch * rotatedZ
        
        // Проверяем, находится ли блок перед камерой
        if (finalZ <= 0.1f) return
        
        // Перспективная проекция
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val aspectRatio = screenWidth.toFloat() / screenHeight
        val scale = (screenHeight / 2f) / tan(fovRad / 2f)
        
        val screenX = (rotatedX / finalZ) * scale + screenWidth / 2f
        val screenY = (-rotatedY / finalZ) * scale + screenHeight / 2f
        
        // Проверяем, находится ли точка в пределах экрана
        if (screenX in 0f..screenWidth.toFloat() && screenY in 0f..screenHeight.toFloat()) {
            // Рисуем простой маркер (квадрат)
            val markerSize = 15f
            val distance = sqrt(dx*dx + dy*dy + dz*dz).toFloat()
            val alpha = (1f - distance / range).coerceIn(0.1f, 1f) * 255
            paint.alpha = alpha.toInt()
            
            canvas.drawRect(
                screenX - markerSize / 2,
                screenY - markerSize / 2,
                screenX + markerSize / 2,
                screenY + markerSize / 2,
                paint
            )
            
            // Опционально: рисуем текст с расстоянием
            if (distance < 10f) {
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 20f
                    isAntiAlias = true
                }
                canvas.drawText(
                    "${distance.toInt()}m",
                    screenX,
                    screenY - markerSize,
                    textPaint
                )
            }
        }
    }
    
    override fun onDisabled() { 
        ores.clear() 
    }
}
