package xyz.xenondevs.nova.world.item.behavior

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.component.DataComponents
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.provider
import xyz.xenondevs.nova.config.entryOrElse
import xyz.xenondevs.nova.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.util.NumberFormatUtils
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.item.novaCompound
import xyz.xenondevs.nova.util.item.retrieveData
import xyz.xenondevs.nova.util.item.storeData
import xyz.xenondevs.nova.util.unwrap
import org.bukkit.inventory.ItemStack as BukkitStack

private val ENERGY_KEY = Key.key("nova", "energy")

/**
 * Creates a factory for [Chargeable] behaviors using the given values, if not specified otherwise in the item's config.
 *
 * @param maxEnergy The maximum amount of energy the item can store.
 * Used when `max_energy` is not specified in the config, or `null` to require the presence of a config entry.
 *
 * @param affectsItemDurability Whether the item's durability bar should be used to visualize the amount
 * of energy stored in the item. Used when `charge_affects_item_durability` is not specified in the config.
 */
@Suppress("FunctionName")
fun Chargeable(
    maxEnergy: Long? = null,
    affectsItemDurability: Boolean = true
) = ItemBehaviorFactory<Chargeable> {
    val cfg = it.config
    Chargeable.Default(
        cfg.entryOrElse(maxEnergy, "max_energy"),
        cfg.entryOrElse<Boolean>(affectsItemDurability, "charge_affects_item_durability")
    )
}

/**
 * Allows items to store energy and be charged.
 */
interface Chargeable : ItemBehavior {
    
    /**
     * The maximum amount of energy this item can store.
     */
    val maxEnergy: Long
    
    /**
     * Gets the current amount of energy stored in the given [itemStack].
     */
    fun getEnergy(itemStack: BukkitStack): Long
    
    /**
     * Sets the current amount of energy stored in the given [itemStack] to [energy].
     */
    fun setEnergy(itemStack: BukkitStack, energy: Long)
    
    /**
     * Adds the given [energy] to the current amount of energy stored in the given [itemStack], capped at [maxEnergy].
     */
    fun addEnergy(itemStack: BukkitStack, energy: Long)
    
    class Default(
        maxEnergy: Provider<Long>,
        affectsItemDurability: Provider<Boolean>
    ) : ItemBehavior, Chargeable {
        
        override val maxEnergy by maxEnergy
        private val affectsItemDurability by affectsItemDurability
        
        override val defaultCompound = provider {
            NamespacedCompound().apply { this[ENERGY_KEY] = 0L }
        }
        
        override fun modifyClientSideStack(player: Player?, itemStack: ItemStack, data: NamespacedCompound): ItemStack {
            val energy = data[ENERGY_KEY] ?: 0L
            
            val lore = itemStack.lore() ?: mutableListOf()
            lore += Component.text(
                NumberFormatUtils.getEnergyString(energy, maxEnergy),
                NamedTextColor.GRAY
            ).withoutPreFormatting()
            itemStack.lore(lore)
            
            if (affectsItemDurability) {
                val fraction = (maxEnergy - energy) / maxEnergy.toDouble()
                val damage = (fraction * Int.MAX_VALUE).toInt()
                itemStack.unwrap().set(DataComponents.MAX_DAMAGE, Int.MAX_VALUE)
                itemStack.unwrap().set(DataComponents.DAMAGE, damage)
            }
            
            return itemStack
        }
        
        override fun getEnergy(itemStack: BukkitStack): Long =
            itemStack.retrieveData(ENERGY_KEY) ?: 0L
        
        override fun setEnergy(itemStack: BukkitStack, energy: Long) =
            itemStack.storeData(ENERGY_KEY, energy.coerceIn(0..maxEnergy))
        
        override fun addEnergy(itemStack: BukkitStack, energy: Long) {
            val compound = itemStack.novaCompound ?: NamespacedCompound()
            val currentEnergy = compound[ENERGY_KEY] ?: 0L
            compound[ENERGY_KEY] = (currentEnergy + energy).coerceIn(0..maxEnergy)
            itemStack.novaCompound = compound
        }
        
        override fun toString(itemStack: ItemStack): String {
            return "Chargeable(" +
                "energy=${getEnergy(itemStack)}," +
                " maxEnergy=$maxEnergy," +
                " affectsItemDurability=$affectsItemDurability" +
                ")"
        }
        
    }
    
}