package xyz.xenondevs.nova.world.block.logic.place

import kotlinx.coroutines.runBlocking
import org.bukkit.GameMode
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.AddonsInitializer
import xyz.xenondevs.nova.data.context.Context
import xyz.xenondevs.nova.data.context.intention.ContextIntentions
import xyz.xenondevs.nova.data.context.param.ContextParamTypes
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InternalInit
import xyz.xenondevs.nova.initialize.InternalInitStage
import xyz.xenondevs.nova.integration.protection.ProtectionManager
import xyz.xenondevs.nova.player.WrappedPlayerInteractEvent
import xyz.xenondevs.nova.util.BlockUtils
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.bukkitBlockData
import xyz.xenondevs.nova.util.isInsideWorldRestrictions
import xyz.xenondevs.nova.util.item.isActuallyInteractable
import xyz.xenondevs.nova.util.item.isReplaceable
import xyz.xenondevs.nova.util.item.novaItem
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.util.serverPlayer
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.NovaBlock
import xyz.xenondevs.nova.world.block.state.model.BackingStateConfig
import xyz.xenondevs.nova.world.block.state.model.DisplayEntityBlockModelData
import xyz.xenondevs.nova.world.format.WorldDataManager
import xyz.xenondevs.nova.world.pos

/**
 * Handles in-game block placing by players.
 */
@InternalInit(
    stage = InternalInitStage.POST_WORLD,
    dependsOn = [AddonsInitializer::class, WorldDataManager::class]
)
internal object BlockPlacing : Listener {
    
    @InitFun
    private fun init() {
        registerEvents()
    }
    
    @EventHandler(ignoreCancelled = true)
    private fun handleBlockPlace(event: BlockPlaceEvent) {
        // Prevent players from placing blocks where there are actually already blocks form Nova
        // This can happen when the hitbox material is replaceable, like as structure void
        event.isCancelled = WorldDataManager.getBlockState(event.block.pos) != null
    }
    
    @EventHandler(ignoreCancelled = true)
    private fun handleBlockPlace(event: BlockMultiPlaceEvent) {
        // Prevent players from placing blocks where there are actually already blocks form Nova
        // This can happen when the hitbox material is replaceable, like as structure void
        event.isCancelled = event.replacedBlockStates.any { WorldDataManager.getBlockState(it.location.pos) != null }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    private fun handleInteract(wrappedEvent: WrappedPlayerInteractEvent) {
        if (wrappedEvent.actionPerformed)
            return
        
        val event = wrappedEvent.event
        val action = event.action
        val player = event.player
        if (action == Action.RIGHT_CLICK_BLOCK) {
            val handItem = event.item
            val block = event.clickedBlock!!
            
            if (!block.type.isActuallyInteractable() || player.isSneaking) {
                val novaItem = handItem?.novaItem
                val novaBlock = novaItem?.block
                if (novaBlock != null) {
                    event.isCancelled = true
                    wrappedEvent.actionPerformed = true
                    
                    placeNovaBlock(event, novaBlock)
                } else if (
                    WorldDataManager.getBlockState(block.pos) != null // the block placed against is from Nova
                    && block.type.isReplaceable() // and will be replaced without special behavior
                    && novaItem == null
                    && handItem?.type?.isBlock == true // a vanilla block material is used 
                ) {
                    event.isCancelled = true
                    wrappedEvent.actionPerformed = true
                    
                    placeVanillaBlock(event)
                }
            }
        }
    }
    
    private fun placeNovaBlock(event: PlayerInteractEvent, novaBlock: NovaBlock) {
        val player = event.player
        val handItem = event.item!!
        
        val clickedBlock = event.clickedBlock!!
        var pos = clickedBlock.location.pos
        if (!clickedBlock.type.isReplaceable() || WorldDataManager.getBlockState(pos) != null)
            pos = pos.advance(event.blockFace)
        
        val ctxBuilder = Context.intention(ContextIntentions.BlockPlace)
            .param(ContextParamTypes.BLOCK_POS, pos)
            .param(ContextParamTypes.BLOCK_ITEM_STACK, handItem)
            .param(ContextParamTypes.SOURCE_ENTITY, player)
            .param(ContextParamTypes.CLICKED_BLOCK_FACE, event.blockFace)
        
        val newState = novaBlock.chooseBlockState(ctxBuilder.build())
        ctxBuilder.param(ContextParamTypes.BLOCK_STATE_NOVA, newState)
        
        val ctx = ctxBuilder.build()
        
        val vanillaState = when (val info = newState.modelProvider.info) {
            is BackingStateConfig -> info.vanillaBlockState.bukkitBlockData
            is DisplayEntityBlockModelData -> info.hitboxType
            is BlockData -> info
            else -> throw UnsupportedOperationException()
        }
        
        if (pos.location.isInsideWorldRestrictions()
            && BlockUtils.isUnobstructed(pos, player, vanillaState)
            && ProtectionManager.canPlace(player, handItem, pos)
            && canPlace(player, handItem, pos, pos.location.advance(event.blockFace.oppositeFace).pos)
            && runBlocking { novaBlock.canPlace(pos, newState, ctx) } // assume blocking is ok because player is online
        ) {
            if (player.gameMode != GameMode.CREATIVE)
                handItem.amount--
            
            BlockUtils.placeNovaBlock(pos, newState, ctx)
            player.swingHand(event.hand!!)
        }
    }
    
    private fun placeVanillaBlock(event: PlayerInteractEvent) {
        val player = event.player
        val handItem = event.item!!
        val placedOn = event.clickedBlock!!.pos
        val pos = event.clickedBlock!!.location.advance(event.blockFace).pos
        
        if (
            ProtectionManager.canPlace(player, handItem, pos)
            && canPlace(player, handItem, pos, placedOn)
        ) {
            val placed = BlockUtils.placeVanillaBlock(pos, player.serverPlayer, handItem, true)
            if (placed && player.gameMode != GameMode.CREATIVE) {
                player.inventory.setItem(event.hand!!, handItem.apply { amount -= 1 })
            }
        }
    }
    
    private fun canPlace(player: Player, item: ItemStack, block: BlockPos, placedOn: BlockPos): Boolean {
        if (
            player.gameMode == GameMode.SPECTATOR
            || !block.location.isInsideWorldRestrictions()
            || !block.block.type.isReplaceable()
            || WorldDataManager.getBlockState(block) != null
        ) return false
        
        if (player.gameMode == GameMode.ADVENTURE)
            return placedOn.block.type.key in item.itemMeta.placeableKeys
        
        return true
    }
    
}