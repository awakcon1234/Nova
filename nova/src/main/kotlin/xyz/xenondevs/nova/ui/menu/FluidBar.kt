package xyz.xenondevs.nova.ui.menu

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.invui.internal.util.InventoryUtils
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemBuilder
import xyz.xenondevs.invui.item.setItemProvider
import xyz.xenondevs.nova.util.NumberFormatUtils
import xyz.xenondevs.nova.util.addItemCorrectly
import xyz.xenondevs.nova.util.item.takeUnlessEmpty
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.FluidContainer
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.NetworkedFluidContainer
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.item.DefaultGuiItems
import xyz.xenondevs.nova.world.item.NovaItem

private fun getFluidBarItem(type: FluidType?): NovaItem = when (type) {
    FluidType.WATER -> DefaultGuiItems.BAR_BLUE
    else -> DefaultGuiItems.BAR_ORANGE
}

private fun ItemBuilder.setFluidDisplayName(amount: Long, capacity: Long): ItemBuilder {
    if (amount == Long.MAX_VALUE) {
        setName("∞ mB / ∞ mB")
    } else if (capacity == Long.MAX_VALUE) {
        setName(NumberFormatUtils.getFluidString(amount) + " / ∞ mB")
    } else {
        setName(NumberFormatUtils.getFluidString(amount, capacity))
    }
    
    return this
}

/**
 * A multi-item gui component for displaying fluid levels.
 */
class FluidBar(
    height: Int,
    fluidHolder: FluidHolder,
    private val fluidContainer: NetworkedFluidContainer,
    private val capacity: Provider<Long>,
    private val type: Provider<FluidType?>,
    private val amount: Provider<Long>
) : VerticalBar(height) {
    
    private val allowedConnectionType = fluidHolder.containers[fluidContainer]!!
    
    constructor(height: Int, fluidHolder: FluidHolder, container: FluidContainer) : this(
        height, fluidHolder, container, container.capacityProvider, container.typeProvider, container.amountProvider
    )
    
    @Suppress("DEPRECATION")
    override fun createBarItem(section: Int) = Item.builder()
        .setItemProvider(type, amount, capacity) { type, amount, capacity ->
            createItemBuilder(
                getFluidBarItem(type),
                section,
                amount.toDouble() / capacity.toDouble()
            ).setFluidDisplayName(amount, capacity)
        }.addClickHandler { _, click ->
            val player = click.player
            val cursor = click.cursor.takeUnlessEmpty()
            when (cursor?.type) {
                Material.BUCKET -> if (allowedConnectionType.extract && fluidContainer.amount >= 1000) {
                    val bucket = fluidContainer.type!!.bucket
                    if (cursor.amount > 1) {
                        click.cursor.amount -= 1
                        if (player.inventory.addItemCorrectly(bucket) != 0)
                            InventoryUtils.dropItemLikePlayer(player, bucket)
                    } else player.setItemOnCursor(bucket)
                    fluidContainer.takeFluid(1000)
                }
                
                Material.WATER_BUCKET -> if (allowedConnectionType.insert && fluidContainer.accepts(FluidType.WATER, 1000)) {
                    player.setItemOnCursor(ItemStack(Material.BUCKET))
                    fluidContainer.addFluid(FluidType.WATER, 1000)
                }
                
                Material.LAVA_BUCKET -> if (allowedConnectionType.insert && fluidContainer.accepts(FluidType.LAVA, 1000)) {
                    player.setItemOnCursor(ItemStack(Material.BUCKET))
                    fluidContainer.addFluid(FluidType.LAVA, 1000)
                }
                
                else -> Unit
            }
        }.build()
    
}

/**
 * A multi-item gui component for displaying a constant fluid level.
 */
class StaticFluidBar(
    height: Int,
    private val capacity: Long,
    private val type: FluidType,
    private val amount: Long
) : VerticalBar(height) {
    
    override fun createBarItem(section: Int): Item {
        return Item.simple(
            createItemBuilder(
                getFluidBarItem(type),
                section,
                amount.toDouble() / capacity.toDouble()
            ).setFluidDisplayName(amount, capacity)
        )
    }
    
}