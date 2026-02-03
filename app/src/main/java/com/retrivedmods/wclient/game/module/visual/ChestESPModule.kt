package com.retrivedmods.wclient.game.module.visual

import android.graphics.*
import android.util.Log
import com.retrivedmods.wclient.game.InterceptablePacket
import com.retrivedmods.wclient.game.Module
import com.retrivedmods.wclient.game.ModuleCategory
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ChestESPModule : Module("chest_esp", ModuleCategory.Visual) {

    private val chests = mutableSetOf<Vector3f>()
    private val fov by floatValue("fov", 90f, 40f..110f)
    private val maxDistance = 64f
    
    companion object {
        private const val TAG = "ChestESP"
        private const val DEBUG = true
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        val packet = interceptablePacket.packet

        try {
            when (packet) {
                is BlockEntityDataPacket -> {
                    if (DEBUG) Log.d(TAG, "BlockEntityDataPacket received")
                    handleBlockEntityData(packet)
                }
                
                is LevelChunkPacket -> {
                    if (DEBUG) Log.d(TAG, "LevelChunkPacket received")
                }
                
                is UpdateBlockPacket -> {
                    if (DEBUG) Log.d(TAG, "UpdateBlockPacket received: ${packet.definition}")
                    val blockName = packet.definition?.toString() ?: ""
                    if (blockName.contains("chest", ignoreCase = true)) {
                        val pos = packet.blockPosition
                        val chestPos = Vector3f.from(
                            pos.x.toFloat() + 0.5f,
                            pos.y.toFloat(),
                            pos.z.toFloat() + 0.5f
                        )
                        chests.add(chestPos)
                        if (DEBUG) Log.d(TAG, "Chest found via UpdateBlockPacket at $chestPos")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet: ${packet.javaClass.simpleName}", e)
        }
    }

    private fun handleBlockEntityData(packet: BlockEntityDataPacket) {
        try {
            val tag = packet.data
            val pos = packet.blockPosition
            
            if (DEBUG) {
                Log.d(TAG, "Position: $pos")
                Log.d(TAG, "Tag: $tag")
                // ИСПРАВЛЕНО: используем toString() вместо keySet
                tag?.let { Log.d(TAG, "Tag content: $it") }
            }
            
            if (tag != null && pos != null) {
                val id = tag.getString("id", "")
                if (DEBUG) Log.d(TAG, "Block entity ID: $id")
                
                if (id.contains("chest", ignoreCase = true) || 
                    id.contains("shulker", ignoreCase = true) ||
                    id == "Chest" || 
                    id == "EnderChest" || 
                    id == "TrappedChest" ||
                    id == "minecraft:chest" ||
                    id == "minecraft:ender_chest" ||
                    id == "minecraft:trapped_chest") {
                    
                    val chestPos = Vector3f.from(
                        pos.x.toFloat() + 0.5f,
                        pos.y.toFloat(),
                        pos.z.toFloat() + 0.5f
                    )
                    chests.add(chestPos)
                    if (DEBUG) Log.d(TAG, "Chest added at $chestPos, total: ${chests.size}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleBlockEntityData", e)
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled) {
            if (DEBUG) Log.d(TAG, "Module disabled")
            return
        }
        if (!isSessionCreated) {
            if (DEBUG) Log.d(TAG, "Session not created")
            return
        }

        try {
            val localPlayer = session.localPlayer
            val playerPos = localPlayer.vec3Position
            
            if (DEBUG && chests.isNotEmpty()) {
                Log.d(TAG, "Rendering ${chests.size} chests, player at $playerPos")
            }
            
            chests.removeIf { chest ->
                val dx = chest.x - playerPos.x
                val dy = chest.y - playerPos.y
                val dz = chest.z - playerPos.z
                (dx * dx + dy * dy + dz * dz) > maxDistance * maxDistance
            }

            if (chests.isEmpty()) return

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
                strokeWidth = 5f
                color = Color.YELLOW
            }

            var renderedCount = 0
            chests.forEach { pos ->
                if (drawChestBox(pos, viewProjMatrix, canvas, paint, playerPos)) {
                    renderedCount++
                }
            }
            
            if (DEBUG) {
                Log.d(TAG, "Rendered $renderedCount/${chests.size} chests")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in render", e)
        }
    }

    private fun createViewMatrix(
        position: Vector3f,
        yaw: Float,
        pitch: Float
    ): Matrix4f {
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
    ): Boolean {
        val dx = pos.x - playerPos.x
        val dy = pos.y - playerPos.y
        val dz = pos.z - playerPos.z
        val distSq = dx * dx + dy * dy + dz * dz
        
        if (distSq > maxDistance * maxDistance) return false

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
        
        if (screenPoints.size != 8) {
            if (DEBUG) Log.d(TAG, "Only ${screenPoints.size}/8 points visible for chest at $pos")
            return false
        }

        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )

        val distance = sqrt(distSq)
        val alpha = (255 * (1f - distance / maxDistance)).toInt().coerceIn(100, 255)
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
        
        return true
    }

    private fun worldToScreen(pos: Vector3f, viewProj: Matrix4f, width: Int, height: Int): Vector2f? {
        val x = viewProj.get(0, 0) * pos.x + viewProj.get(0, 1) * pos.y + 
                viewProj.get(0, 2) * pos.z + viewProj.get(0, 3)
        val y = viewProj.get(1, 0) * pos.x + viewProj.get(1, 1) * pos.y + 
                viewProj.get(1, 2) * pos.z + viewProj.get(1, 3)
        val w = viewProj.get(3, 0) * pos.x + viewProj.get(3, 1) * pos.y + 
                viewProj.get(3, 2) * pos.z + viewProj.get(3, 3)

        if (w <= 0.01f) return null

        val invW = 1f / w
        val screenX = (x * invW + 1f) * 0.5f * width
        val screenY = (1f - y * invW) * 0.5f * height

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
        if (DEBUG) Log.d(TAG, "Module disabled, clearing ${chests.size} chests")
        chests.clear()
    }
}
