package com.retrivedmods.wclient.game.module.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket
import java.util.zip.Inflater
import kotlin.math.*

class XrayModule : Module("xray", ModuleCategory.Visual) {

    data class BlockPos(val x: Int, val y: Int, val z: Int) {
        fun distanceTo(otherX: Float, otherY: Float, otherZ: Float): Float {
            val dx = x.toFloat() - otherX
            val dy = y.toFloat() - otherY
            val dz = z.toFloat() - otherZ
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }

    private val ores = mutableMapOf<BlockPos, Int>()
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val range by floatValue("range", 30f, 10f..64f)

    // Полный список руд на 1.21.131 (string names без minecraft:)
    private val oreTypes = mapOf(
        "diamond_ore" to Color.CYAN,
        "deepslate_diamond_ore" to Color.CYAN,
        "ancient_debris" to Color.rgb(139, 69, 19),
        "gold_ore" to Color.YELLOW,
        "deepslate_gold_ore" to Color.YELLOW,
        "emerald_ore" to Color.GREEN,
        "deepslate_emerald_ore" to Color.GREEN,
        "iron_ore" to Color.rgb(255, 200, 150),
        "deepslate_iron_ore" to Color.rgb(255, 200, 150),
        "lapis_ore" to Color.BLUE,
        "deepslate_lapis_ore" to Color.BLUE,
        "redstone_ore" to Color.RED,
        "lit_redstone_ore" to Color.RED,
        "deepslate_redstone_ore" to Color.RED,
        "lit_deepslate_redstone_ore" to Color.RED,
        "coal_ore" to Color.DKGRAY,
        "deepslate_coal_ore" to Color.DKGRAY,
        "copper_ore" to Color.rgb(184, 115, 51),
        "deepslate_copper_ore" to Color.rgb(184, 115, 51),
        "nether_gold_ore" to Color.YELLOW,
        "quartz_ore" to Color.rgb(200, 200, 200)  // nether_quartz_ore
    )

    // Runtime IDs стабильные на 1.21.131 (из wiki + data dumps 2025–2026)
    private val oreRuntimeIds = setOf(
        16, 661,   // coal_ore, deepslate_coal_ore
        15, 656,   // iron_ore, deepslate_iron_ore
        21, 655,   // lapis_ore, deepslate_lapis_ore
        14, 657,   // gold_ore, deepslate_gold_ore
        56, 660,   // diamond_ore, deepslate_diamond_ore
        129, 662,  // emerald_ore, deepslate_emerald_ore
        73, 74, 658, 659,  // redstone_ore, lit, deep, lit_deep
        566, 663,  // copper_ore, deepslate_copper_ore
        526,       // ancient_debris
        543,       // nether_gold_ore
        153        // quartz_ore (nether_quartz_ore)
    )

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet
        try {
            if (packet is UpdateBlockPacket) {
                // Фикс: definition.toString() обычно даёт "minecraft:diamond_ore" или подобное
                val blockDef = packet.definition
                val blockNameRaw = blockDef.toString().lowercase()
                val blockName = blockNameRaw.removePrefix("minecraft:").removePrefix("block{identifier=")
                    .removeSuffix("}").trim()  // чистим возможный мусор

                val pos = BlockPos(packet.blockPosition.x, packet.blockPosition.y, packet.blockPosition.z)

                if (oreTypes.containsKey(blockName)) {
                    ores[pos] = oreTypes[blockName]!!
                } else if (blockName.contains("air") || blockName == "cave_air") {
                    ores.remove(pos)
                }
            } else if (packet is LevelChunkPacket) {
                parseLevelChunk(packet)
            }
        } catch (e: Exception) {
            // Для дебага раскомменти: println("Xray packet error: ${e.message}")
        }
    }

    private fun parseLevelChunk(packet: LevelChunkPacket) {
        try {
            val chunkX = packet.chunkX * 16
            val chunkZ = packet.chunkZ * 16

            // Фикс: data — ByteBuf, копируем в ByteArray
            val dataBuf: ByteBuf = packet.data
            val payloadBytes = ByteArray(dataBuf.readableBytes())
            dataBuf.readBytes(payloadBytes)
            // Не release здесь, если пакет ещё нужен дальше — но в proxy обычно ок

            val decompressed = decompress(payloadBytes)
            val buf = Unpooled.wrappedBuffer(decompressed)

            var subY = -64
            while (buf.readableBytes() > 0 && subY < 320) {
                val version = buf.readByte().toInt() and 0xFF
                if (version !in 8..9) {  // 1.21+ использует 8 или 9 subchunk format
                    buf.skipBytes(buf.readableBytes())
                    break
                }

                val numStorages = buf.readByte().toInt() and 0xFF
                for (storageIdx in 0 until numStorages) {
                    val bitsPerBlock = buf.readByte().toInt() and 0xFF
                    if (bitsPerBlock == 0) continue

                    val paletteSize = readVarInt(buf)
                    val palette = mutableListOf<Int>()
                    repeat(paletteSize) { palette.add(readVarInt(buf)) }

                    if (!palette.any { it in oreRuntimeIds }) {
                        val dataBytes = ((4096L * bitsPerBlock + 7) / 8).toInt()
                        buf.skipBytes(dataBytes)
                        continue
                    }

                    val bitReader = BitReader(buf)
                    for (localIdx in 0 until 4096) {
                        val idx = bitReader.readBits(bitsPerBlock)
                        val runtimeId = if (bitsPerBlock <= 8) (palette.getOrNull(idx) ?: 0) else idx
                        if (runtimeId in oreRuntimeIds) {
                            val lx = localIdx % 16
                            val ly = (localIdx / 16) % 16
                            val lz = (localIdx / 256) % 16
                            val wx = chunkX + lx
                            val wy = subY + ly
                            val wz = chunkZ + lz
                            ores[BlockPos(wx, wy, wz)] = getOreColor(runtimeId) ?: Color.WHITE
                        }
                    }
                }

                // Пропускаем lighting (block light + sky light = 2048 + 2048 байт)
                if (buf.readableBytes() >= 4096) {
                    buf.skipBytes(4096)
                }
                subY += 16
            }
            buf.release()
        } catch (e: Exception) {
            // println("Chunk parse fail: ${e.message}")
        }
    }

    private class BitReader(private val buf: ByteBuf) {
        private var bitPos = 0
        private var currentByte = 0

        fun readBits(bits: Int): Int {
            var value = 0
            var bitsLeft = bits
            while (bitsLeft > 0) {
                if (bitPos == 0) {
                    currentByte = buf.readByte().toInt() and 0xFF
                }
                val bitsThis = minOf(8 - bitPos, bitsLeft)
                val mask = (1 shl bitsThis) - 1
                value = (value shl bitsThis) or ((currentByte shr bitPos) and mask)
                bitPos += bitsThis
                if (bitPos == 8) bitPos = 0
                bitsLeft -= bitsThis
            }
            return value
        }
    }

    private fun readVarInt(buf: ByteBuf): Int {
        var value = 0
        var shift = 0
        while (true) {
            val b = buf.readByte().toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 35) throw RuntimeException("VarInt too big")
        }
        return value
    }

    private fun getOreColor(runtimeId: Int): Int? = when (runtimeId) {
        16, 661 -> Color.DKGRAY
        15, 656 -> Color.rgb(255, 200, 150)
        21, 655 -> Color.BLUE
        14, 657 -> Color.YELLOW
        56, 660 -> Color.CYAN
        129, 662 -> Color.GREEN
        73, 74, 658, 659 -> Color.RED
        566, 663 -> Color.rgb(184, 115, 51)
        526 -> Color.rgb(139, 69, 19)
        543 -> Color.YELLOW
        153 -> Color.rgb(200, 200, 200)
        else -> null
    }

    private fun decompress(compressed: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val output = ByteArray(2 * 1024 * 1024)  // до 2MB на чанк
        val len = try { inflater.inflate(output) } catch (e: Exception) { 0 }
        inflater.end()
        return output.copyOf(len)
    }

    // ТВОЙ render без изменений
    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated || ores.isEmpty()) return
        
        try {
            val localPlayer = session.localPlayer
            val playerX = localPlayer.vec3Position.x.toFloat()
            val playerY = localPlayer.vec3Position.y.toFloat()
            val playerZ = localPlayer.vec3Position.z.toFloat()
            
            ores.keys.removeIf { it.distanceTo(playerX, playerY, playerZ) > range }
            
            if (ores.isEmpty()) return

            val screenWidth = canvas.width.toFloat()
            val screenHeight = canvas.height.toFloat()
            
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
            
            ores.forEach { (pos, color) ->
                renderOre(pos, color, canvas, playerX, playerY, playerZ, 
                    localPlayer.rotationYaw, localPlayer.rotationPitch, 
                    paint, outlinePaint, textPaint, screenWidth, screenHeight)
            }
        } catch (e: Exception) {}
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
        val dx = pos.x.toFloat() - playerX
        val dy = pos.y.toFloat() - playerY - 1.6f
        val dz = pos.z.toFloat() - playerZ
        
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance > range) return
        
        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)
        val sinPitch = sin(pitchRad)
        val cosPitch = cos(pitchRad)
        
        val x1 = cosYaw * dz - sinYaw * dx
        val z1 = sinYaw * dz + cosYaw * dx
        
        val x2 = x1
        val y2 = cosPitch * dy - sinPitch * z1
        val z2 = sinPitch * dy + cosPitch * z1
        
        if (z2 <= 0.1f) return
        
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val scale = (screenHeight / 2f) / tan(fovRad / 2f)
        
        val screenX = (-x2 / z2) * scale + screenWidth / 2f
        val screenY = (-y2 / z2) * scale + screenHeight / 2f
        
        if (screenX !in 0f..screenWidth || screenY !in 0f..screenHeight) return
        
        val size = (30f / (distance.coerceAtLeast(1f))).coerceIn(10f, 30f)
        val alpha = ((1f - distance / range) * 200).toInt().coerceIn(50, 255)
        
        paint.color = color
        paint.alpha = alpha
        outlinePaint.alpha = alpha
        
        val left = screenX - size / 2
        val top = screenY - size / 2
        val right = screenX + size / 2
        val bottom = screenY + size / 2
        
        canvas.drawOval(RectF(left, top, right, bottom), paint)
        canvas.drawOval(RectF(left, top, right, bottom), outlinePaint)
        
        if (distance < 15f) {
            textPaint.alpha = alpha
            canvas.drawText("${distance.toInt()}m", screenX, top - 5f, textPaint)
        }
    }
    
    override fun onDisabled() { ores.clear() }
    override fun onEnabled() { ores.clear() }
}
