package xyz.xenondevs.nova.ui.waila.info.impl

import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.block.data.type.RedstoneRail
import org.bukkit.entity.Player
import xyz.xenondevs.nova.ui.waila.info.VanillaWailaInfoProvider
import xyz.xenondevs.nova.ui.waila.info.WailaInfo
import xyz.xenondevs.nova.world.BlockPos

internal object RailWailaInfoProvider : VanillaWailaInfoProvider<RedstoneRail>(
    setOf(Material.ACTIVATOR_RAIL, Material.DETECTOR_RAIL, Material.POWERED_RAIL)
) {
    
    override fun getInfo(player: Player, pos: BlockPos, blockState: RedstoneRail): WailaInfo {
        val info = DefaultVanillaWailaInfoProvider.getInfo(player, pos, blockState)
        info.icon = Key.key(blockState.material.name.lowercase() + if (blockState.isPowered) "_on" else "")
        return info
    }
    
}