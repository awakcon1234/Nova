package xyz.xenondevs.nova.world.item.behavior

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.DyedItemColor
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.getMod
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.nova.config.entryOrElse
import xyz.xenondevs.nova.util.data.ImageUtils
import xyz.xenondevs.nova.world.item.vanilla.VanillaMaterialProperty
import java.awt.Color

/**
 * Creates a factory for [AnimatedDye] behaviors using the given values, if not specified otherwise in the config.
 *
 * @param defaultTicksPerColor The default value for the amount of ticks between each color.
 * Used when `ticks_per_color` is not specified in the config, or null to require the presence of a config entry.
 *
 * @param defaultColors The default value for the list of colors to cycle through,
 * to be used when `colors` is not specified in the config, or null to require the presence of a config entry.
 */
@Suppress("FunctionName")
fun AnimatedDye(
    defaultTicksPerColor: Int? = null,
    defaultColors: List<Color>? = null
) = ItemBehaviorFactory<AnimatedDye> {
    val cfg = it.config
    AnimatedDye(
        cfg.entryOrElse(defaultTicksPerColor, "ticks_per_color"),
        cfg.entryOrElse(defaultColors, "colors")
    )
}

/**
 * Animates the `minecraft:dyed_color` component by interpolating between a given set of colors.
 *
 * @param ticksPerColor The amount of ticks between each color.
 * @param colors The list of colors to cycle through.
 */
class AnimatedDye(
    ticksPerColor: Provider<Int>,
    colors: Provider<List<Color>>
) : ItemBehavior {
    
    private val componentFrames: Provider<List<DataComponentMap>> = combinedProvider(
        ticksPerColor, colors
    ) { ticksPerFrame, frames ->
        buildList {
            for (i in 0..<(frames.size * ticksPerFrame)) {
                val from = frames[i / ticksPerFrame]
                val to = frames[(i / ticksPerFrame + 1) % frames.size]
                val color = ImageUtils.lerp(from, to, i % ticksPerFrame / ticksPerFrame.toFloat())
                
                this += DataComponentMap.builder()
                    .set(DataComponents.DYED_COLOR, DyedItemColor(color.rgb, false))
                    .build()
            }
        }
    }
    
    override val baseDataComponents = componentFrames.getMod(EquipmentAnimator.tick)
    override val vanillaMaterialProperties = provider(listOf(VanillaMaterialProperty.DYEABLE))
    
    init {
        EquipmentAnimator.animatedBehaviors += this
    }
    
}