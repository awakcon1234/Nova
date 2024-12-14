@file:Suppress("UNCHECKED_CAST")

package xyz.xenondevs.nova.world.player.attachment

import net.kyori.adventure.key.Key
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import xyz.xenondevs.nova.LOGGER
import xyz.xenondevs.nova.initialize.DisableFun
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InternalInit
import xyz.xenondevs.nova.initialize.InternalInitStage
import xyz.xenondevs.nova.network.event.PacketHandler
import xyz.xenondevs.nova.network.event.PacketListener
import xyz.xenondevs.nova.network.event.clientbound.ClientboundSetPassengersPacketEvent
import xyz.xenondevs.nova.network.event.registerPacketListener
import xyz.xenondevs.nova.registry.NovaRegistries.ATTACHMENT_TYPE
import xyz.xenondevs.nova.serialization.persistentdata.get
import xyz.xenondevs.nova.serialization.persistentdata.set
import xyz.xenondevs.nova.util.getValue
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.util.runTaskLater
import xyz.xenondevs.nova.util.runTaskTimer
import kotlin.collections.set

private val ATTACHMENTS_KEY = NamespacedKey("nova", "attachments1")

@InternalInit(stage = InternalInitStage.POST_WORLD)
object AttachmentManager : Listener, PacketListener {
    
    private val activeAttachments = HashMap<Player, HashMap<AttachmentType<*>, Attachment>>()
    private val inactiveAttachments = HashMap<Player, HashSet<Key>>()
    
    @InitFun
    private fun init() {
        registerEvents()
        registerPacketListener()
        Bukkit.getOnlinePlayers().forEach(::loadAttachments)
        runTaskTimer(0, 1) { activeAttachments.values.flatMap(Map<*, Attachment>::values).forEach(Attachment::handleTick) }
    }
    
    @DisableFun
    private fun disable() {
        Bukkit.getOnlinePlayers().forEach { saveAndRemoveAttachments(it) }
    }
    
    fun <A : Attachment, T : AttachmentType<A>> addAttachment(player: Player, type: T): A {
        check(!player.isDead) { "Attachments cannot be added to dead players" }
        
        val attachmentsMap = activeAttachments.getOrPut(player, ::HashMap)
        if (type in attachmentsMap)
            return attachmentsMap[type] as A
        
        val attachment = type.constructor(player)
        attachmentsMap[type] = attachment
        
        return attachment
    }
    
    fun hasAttachment(player: Player, type: AttachmentType<*>): Boolean =
        activeAttachments[player]?.contains(type) ?: false || inactiveAttachments[player]?.contains(type.id) ?: false
    
    fun removeAttachment(player: Player, type: AttachmentType<*>) {
        val inactiveAttachmentsMap = inactiveAttachments[player]
        inactiveAttachmentsMap?.remove(type.id)
        
        if (inactiveAttachmentsMap != null && inactiveAttachmentsMap.isEmpty())
            inactiveAttachments -= player
        
        val activeAttachmentsMap = activeAttachments[player]
        val attachment = activeAttachmentsMap?.remove(type)
        
        if (activeAttachmentsMap != null && activeAttachmentsMap.isEmpty())
            activeAttachments -= player
        
        attachment?.despawn()
    }
    
    @EventHandler
    private fun handlePlayerJoin(event: PlayerJoinEvent) {
        loadAttachments(event.player)
    }
    
    @EventHandler
    private fun handlePlayerQuit(event: PlayerQuitEvent) {
        saveAndRemoveAttachments(event.player)
    }
    
    @EventHandler
    private fun handleDeath(event: PlayerDeathEvent) {
        deactivateAttachments(event.entity)
    }
    
    @EventHandler
    private fun handleRespawn(event: PlayerRespawnEvent) {
        runTaskLater(1) {
            val player = event.player
            if (player.isOnline && !player.isDead)
                activateAttachments(event.player)
        }
    }
    
    @EventHandler
    private fun handleTeleport(event: PlayerTeleportEvent) {
        activeAttachments[event.player]?.values?.forEach(Attachment::handleTeleport)
    }
    
    @PacketHandler
    private fun handlePassengersSet(event: ClientboundSetPassengersPacketEvent) {
        val attachments = (activeAttachments.entries.firstOrNull { (player, _) -> player.entityId == event.vehicle } ?: return).value.values
        event.passengers += attachments.map(Attachment::passengerId)
    }
    
    private fun deactivateAttachments(player: Player) {
        val attachmentsMap = activeAttachments[player] ?: return
        val inactive = inactiveAttachments.getOrPut(player, ::HashSet)
        attachmentsMap.forEach { (type, attachment) ->
            inactive += type.id
            attachment.despawn()
        }
        
        activeAttachments -= player
    }
    
    private fun activateAttachments(player: Player) {
        val attachmentIds = inactiveAttachments[player] ?: return
        activateAttachments(player, attachmentIds)
        inactiveAttachments -= player
    }
    
    private fun activateAttachments(player: Player, attachmentIds: Set<Key>) {
        attachmentIds.forEach {
            val type = ATTACHMENT_TYPE.getValue(it)
            if (type != null) {
                addAttachment(player, type)
            } else LOGGER.error("Unknown attachment type $it on player ${player.name}")
        }
    }
    
    private fun loadAttachments(player: Player) {
        val attachmentIds = player.persistentDataContainer
            .get<HashSet<Key>>(ATTACHMENTS_KEY)
            ?: return
        
        if (player.isDead) {
            inactiveAttachments.getOrPut(player, ::HashSet) += attachmentIds
        } else {
            activateAttachments(player, attachmentIds)
        }
    }
    
    private fun saveAttachments(player: Player) {
        val dataContainer = player.persistentDataContainer
        val attachmentIds = HashSet<Key>()
        activeAttachments[player]?.forEach { attachmentIds += it.key.id }
        inactiveAttachments[player]?.let { attachmentIds += it }
        if (attachmentIds.isNotEmpty()) {
            dataContainer.set(ATTACHMENTS_KEY, attachmentIds)
        } else dataContainer.remove(ATTACHMENTS_KEY)
    }
    
    private fun saveAndRemoveAttachments(player: Player) {
        saveAttachments(player)
        activeAttachments.remove(player)
            ?.forEach { it.value.despawn() }
    }
    
}