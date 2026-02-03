package com.retrivedmods.wclient.game.module.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import kotlin.math.*

class XrayModule : Module("xray", ModuleCategory.Visual) {

    // Простой класс для хранения позиции блока
    data class BlockPos(val x: Int, val y: Int, val z: Int) {
        fun distanceTo(otherX: Float, otherY: Float, otherZ: Float): Float {
            val dx = x - otherX
            val dy = y - otherY
            val dz = z - otherZ
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }

    private val ores = mutableMapOf<BlockPos, Int>()
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val range by floatValue("range", 30f, 10f..64f)
    
    // Типы руд для отслеживания
    private val oreTypes = listOf(
        "diamond_ore" to Color.CYAN,
        "ancient_debris" to Color.rgb(139, 69, 19),
        "gold_ore" to Color.YELLOW,
        "emerald_ore" to Color.GREEN,
        "iron_ore" to Color.rgb(255, 200, 150),
        "lapis_ore" to Color.BLUE,
        "redstone_ore" to Color.RED,
        "coal_ore" to Color.DKGRAY,
        "copper_ore" to Color.rgb(184, 115, 51)
    )

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        if (packet is UpdateBlockPacket) {
            try {
                val blockName = packet.definition.toString().lowercase()
                val pos = BlockPos(packet.blockPosition.x, packet.blockPosition.y, packet.blockPosition.z)
                
                // Ищем совпадение с типами руд
                val oreInfo = oreTypes.find { blockName.contains(it.first) }
                
                if (oreInfo != null) {
                    // Добавляем руду
                    ores[pos] = oreInfo.second
                } else if (blockName.contains("air")) {
                    // Удаляем блок, если он заменен воздухом
                    ores.remove(pos)
                } else {
                    // Удаляем, если это не руда (для очистки)
                    ores.remove(pos)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки парсинга
            }
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || ores.isEmpty()) return
        
        try {
            val localPlayer = session.localPlayer
            val playerX = localPlayer.vec3Position.x.toFloat()
            val playerY = localPlayer.vec3Position.y.toFloat()
            val playerZ = localPlayer.vec3Position.z.toFloat()
            
            // Очищаем дальние блоки
            ores.keys.removeIf { pos ->
                pos.distanceTo(playerX, playerY, playerZ) > range
            }
            
            if (ores.isEmpty()) return

            val screenWidth = canvas.width.toFloat()
            val screenHeight = canvas.height.toFloat()
            val centerX = screenWidth / 2f
            val centerY = screenHeight / 2f
            
            // Подготовка Paint
            val paint = Paint().apply {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }
            
            val outlinePaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.WHITE
                isAntiAlias = true
            }
            
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 20f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            
            // Рисуем все руды
            ores.forEach { (pos, color) ->
                renderOre(pos, color, canvas, playerX, playerY, playerZ, 
                    localPlayer.rotationYaw, localPlayer.rotationPitch, 
                    paint, outlinePaint, textPaint, screenWidth, screenHeight)
            }
        } catch (e: Exception) { 
            // Игнорируем ошибки рендеринга
        }
    }

    private fun renderOre(
        pos: BlockPos,
        color: Int,
        canvas: Canvas,
        playerX: Float, playerY: Float, playerZ: Float,
        yaw: Float, pitch: Float,
        paint: Paint, outlinePaint: Paint, textPaint: Paint,
        screenWidth: Float, screenHeight: Float
    ) {
        // Относительная позиция блока
        val dx = pos.x - playerX
        val dy = pos.y - playerY - 1.6f // Высота глаз
        val dz = pos.z - playerZ
        
        // Расстояние до блока
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        
        // Если блок слишком далеко, пропускаем
        if (distance > range) return
        
        // Преобразуем углы в радианы
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        
        // Поворот по горизонтали (yaw)
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)
        
        // Поворот по вертикали (pitch)
        val sinPitch = sin(pitchRad)
        val cosPitch = cos(pitchRad)
        
        // Вращаем позицию относительно камеры
        // Сначала горизонтальное вращение
        val x1 = cosYaw * dz - sinYaw * dx
        val z1 = sinYaw * dz + cosYaw * dx
        
        // Затем вертикальное вращение
        val x2 = x1
        val y2 = cosPitch * dy - sinPitch * z1
        val z2 = sinPitch * dy + cosPitch * z1
        
        // Если блок позади камеры, не рисуем
        if (z2 <= 0.1f) return
        
        // Перспективная проекция
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val scale = (screenHeight / 2f) / tan(fovRad / 2f)
        
        // Экранные координаты
        val screenX = (-x2 / z2) * scale + screenWidth / 2f
        val screenY = (-y2 / z2) * scale + screenHeight / 2f
        
        // Проверяем, находится ли точка на экране
        if (screenX !in 0f..screenWidth || screenY !in 0f..screenHeight) return
        
        // Размер маркера в зависимости от расстояния
        val size = (30f / (distance.coerceAtLeast(1f))).coerceIn(10f, 30f)
        
        // Прозрачность в зависимости от расстояния
        val alpha = ((1f - distance / range) * 200).toInt().coerceIn(50, 255)
        
        // Устанавливаем цвет и прозрачность
        paint.color = color
        paint.alpha = alpha
        outlinePaint.alpha = alpha
        
        // Рисуем маркер
        val left = screenX - size / 2
        val top = screenY - size / 2
        val right = screenX + size / 2
        val bottom = screenY + size / 2
        
        canvas.drawOval(RectF(left, top, right, bottom), paint)
        canvas.drawOval(RectF(left, top, right, bottom), outlinePaint)
        
        // Рисуем расстояние если близко
        if (distance < 15f) {
            textPaint.alpha = alpha
            canvas.drawText(
                "${distance.toInt()}m",
                screenX,
                top - 5f,
                textPaint
            )
        }
    }
    
    override fun onDisabled() { 
        ores.clear() 
    }
    
    override fun onEnabled() {
        // Очищаем при включении
        ores.clear()
    }
}
