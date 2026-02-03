package com.retrivedmods.wclient.game.world

import com.retrivedmods.wclient.game.GameSession
import com.retrivedmods.wclient.game.entity.Entity
import com.retrivedmods.wclient.game.entity.EntityUnknown
import com.retrivedmods.wclient.game.entity.Item
import com.retrivedmods.wclient.game.entity.Player
import org.cloudburstmc.protocol.bedrock.packet.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Suppress("MemberVisibilityCanBePrivate")
class Level(val session: GameSession) {

    val entityMap = ConcurrentHashMap<Long, Entity>()
    val playerMap = ConcurrentHashMap<UUID, PlayerListPacket.Entry>()
    
    // Запоминаем ID локального игрока
    var localRuntimeId: Long = 0

    fun onDisconnect() {
        entityMap.clear()
        playerMap.clear()
        localRuntimeId = 0
    }

    fun onPacketBound(packet: BedrockPacket) {
        when (packet) {
            is StartGamePacket -> {
                entityMap.clear()
                playerMap.clear()
                // ВАЖНО: Сохраняем свой ID при старте
                localRuntimeId = packet.runtimeEntityId
            }

            is AddEntityPacket -> {
                val entity = EntityUnknown(
                    packet.runtimeEntityId,
                    packet.uniqueEntityId,
                    packet.identifier
                ).apply {
                    move(packet.position)
                    rotate(packet.rotation)
                    handleSetData(packet.metadata)
                    handleSetAttribute(packet.attributes)
                }
                entityMap[packet.runtimeEntityId] = entity
            }

            is AddItemEntityPacket -> {
                val entity = Item(packet.runtimeEntityId, packet.uniqueEntityId).apply {
                    move(packet.position)
                    handleSetData(packet.metadata)
                }
                entityMap[packet.runtimeEntityId] = entity
            }

            is AddPlayerPacket -> {
                // ФИКС: Если пакет добавляет НАС ЖЕ, игнорируем его в Level.
                // Мы уже управляем собой через LocalPlayer в GameSession.
                // Это убирает "плавающий" бокс вокруг себя.
                if (packet.runtimeEntityId == localRuntimeId) return

                val entity = Player(
                    packet.runtimeEntityId,
                    packet.uniqueEntityId,
                    packet.uuid,
                    packet.username
                ).apply {
                    move(packet.position)
                    rotate(packet.rotation)
                    handleSetData(packet.metadata)
                }
                entityMap[packet.runtimeEntityId] = entity
            }

            is RemoveEntityPacket -> {
                val entityToRemove = entityMap.values.find { it.uniqueEntityId == packet.uniqueEntityId } ?: return
                entityMap.remove(entityToRemove.runtimeEntityId)
            }

            is TakeItemEntityPacket -> {
                entityMap.remove(packet.itemRuntimeEntityId)
            }

            is PlayerListPacket -> {
                val add = packet.action == PlayerListPacket.Action.ADD
                packet.entries.forEach {
                    if (add) {
                        playerMap[it.uuid] = it
                    } else {
                        playerMap.remove(it.uuid)
                    }
                }
            }

            // ОПТИМИЗАЦИЯ: Прямая передача пакетов по ID.
            // Раньше пакеты шли через "else -> forEach", что медленно и вызывало микро-фризы позиций.
            
            is MoveEntityAbsolutePacket -> {
                if (packet.runtimeEntityId == localRuntimeId) return
                entityMap[packet.runtimeEntityId]?.onPacketBound(packet)
            }
            
            is MoveEntityDeltaPacket -> {
                if (packet.runtimeEntityId == localRuntimeId) return
                entityMap[packet.runtimeEntityId]?.onPacketBound(packet)
            }
            
            is MovePlayerPacket -> {
                if (packet.runtimeEntityId == localRuntimeId) return
                entityMap[packet.runtimeEntityId]?.onPacketBound(packet)
            }
            
            is SetEntityDataPacket -> {
                if (packet.runtimeEntityId == localRuntimeId) return
                entityMap[packet.runtimeEntityId]?.onPacketBound(packet)
            }
            
            is MobEquipmentPacket -> {
                 if (packet.runtimeEntityId == localRuntimeId) return
                 entityMap[packet.runtimeEntityId]?.onPacketBound(packet)
            }
             
            is MobArmorEquipmentPacket -> {
                 if (packet.runtimeEntityId == localRuntimeId) return
                 entityMap[packet.runtimeEntityId]?.onPacketBound(packet)
            }

            else -> {
                // Для всех остальных редких пакетов оставляем перебор
                entityMap.values.forEach { entity ->
                    entity.onPacketBound(packet)
                }
            }
        }
    }
}
