@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UNCHECKED_CAST")

package xyz.xenondevs.nova.world.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.Style
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemAttributeModifiers
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.CBF
import xyz.xenondevs.commons.provider.immutable.combinedProvider
import xyz.xenondevs.commons.provider.immutable.map
import xyz.xenondevs.invui.item.builder.ItemBuilder
import xyz.xenondevs.nova.LOGGER
import xyz.xenondevs.nova.config.ConfigProvider
import xyz.xenondevs.nova.config.Configs
import xyz.xenondevs.nova.network.event.serverbound.ServerboundPlayerActionPacketEvent
import xyz.xenondevs.nova.registry.NovaRegistries
import xyz.xenondevs.nova.resources.builder.task.model.VanillaMaterialTypes
import xyz.xenondevs.nova.resources.layout.item.RequestedItemModelLayout
import xyz.xenondevs.nova.resources.lookup.ResourceLookups
import xyz.xenondevs.nova.resources.model.ItemModelData
import xyz.xenondevs.nova.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.util.component.adventure.toNMSComponent
import xyz.xenondevs.nova.util.data.get
import xyz.xenondevs.nova.util.data.logExceptionMessages
import xyz.xenondevs.nova.util.item.ItemUtils
import xyz.xenondevs.nova.world.block.NovaBlock
import xyz.xenondevs.nova.world.block.event.BlockBreakActionEvent
import xyz.xenondevs.nova.world.item.behavior.ItemBehavior
import xyz.xenondevs.nova.world.item.behavior.ItemBehaviorFactory
import xyz.xenondevs.nova.world.item.behavior.ItemBehaviorHolder
import xyz.xenondevs.nova.world.item.logic.PacketItems
import xyz.xenondevs.nova.world.player.WrappedPlayerInteractEvent
import xyz.xenondevs.nova.world.player.equipment.ArmorEquipEvent
import java.util.logging.Level
import kotlin.Unit
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf
import net.minecraft.util.Unit as MojangUnit
import net.minecraft.world.item.ItemStack as MojangStack

/**
 * Represents an item type in Nova.
 */
class NovaItem internal constructor(
    val id: ResourceLocation,
    val name: Component?,
    val style: Style,
    behaviorHolders: List<ItemBehaviorHolder>,
    val maxStackSize: Int,
    private val _craftingRemainingItem: ItemStack?,
    val isHidden: Boolean,
    val block: NovaBlock?,
    private val configId: String,
    internal val requestedLayout: RequestedItemModelLayout
) {
    
    /**
     * The [ItemStack] that is left over after this [NovaItem] was
     * used in a crafting recipe.
     */
    val craftingRemainingItem: ItemStack?
        get() = _craftingRemainingItem?.clone()
    
    val model: ItemModelData by ResourceLookups.ITEM_MODEL_LOOKUP.provider.map {
        val material = vanillaMaterial
        val models = it[this]?.get(material) ?: emptyMap()
        ItemModelData(this, models)
    }
    
    /**
     * The configuration for this [NovaItem].
     * Trying to read config values from this when no config is present will result in an exception.
     */
    val config: ConfigProvider = Configs[configId]
    
    /**
     * The [ItemBehaviors][ItemBehavior] of this [NovaItem].
     */
    val behaviors: List<ItemBehavior> = behaviorHolders.map { holder ->
        when (holder) {
            is ItemBehavior -> holder
            is ItemBehaviorFactory<*> -> holder.create(this)
        }
    }
    
    /**
     * The underlying vanilla material of this [NovaItem].
     */
    internal val vanillaMaterial: Material by combinedProvider(
        ResourceLookups.ITEM_MODEL_LOOKUP.provider,
        combinedProvider(behaviors.map(ItemBehavior::vanillaMaterialProperties))
    ) { lookup, properties ->
        var vanillaMaterial = VanillaMaterialTypes.getMaterial(properties.flatten().toHashSet())
        
        // fall back to first available material if vanilla material is not present in lookups
        val itemModels = lookup[this]
        if (itemModels != null && vanillaMaterial !in itemModels)
            vanillaMaterial = itemModels.keys.first()
        
        vanillaMaterial
    }
    
    /**
     * The base data components of this [NovaItem].
     */
    internal val baseDataComponents: DataComponentMap by combinedProvider(
        behaviors.map(ItemBehavior::baseDataComponents)
    ) { dataComponentMaps ->
        val builder = DataComponentMap.builder()
        if (name != null) {
            builder.set(DataComponents.ITEM_NAME, name.toNMSComponent())
        } else {
            builder.set(DataComponents.HIDE_TOOLTIP, MojangUnit.INSTANCE)
        }
        builder.set(DataComponents.ATTRIBUTE_MODIFIERS, loadConfiguredAttributeModifiers())
        builder.set(DataComponents.MAX_STACK_SIZE, maxStackSize)
        val base = builder.build()
        val allMaps = buildList {
            add(base)
            addAll(dataComponentMaps)
        }
        return@combinedProvider ItemUtils.mergeDataComponentMaps(allMaps)
    }
    
    /**
     * The default components patch applied to all [ItemStacks][ItemStack] of this [NovaItem].
     */
    internal val defaultPatch: DataComponentPatch by combinedProvider(
        behaviors.map(ItemBehavior::defaultPatch)
    ) { dataComponentPatches ->
        val base = DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().also { compoundTag ->
                compoundTag.put("nova", CompoundTag().also {
                    it.putString("id", id.toString())
                })
                defaultCompound?.let { compoundTag.putByteArray("nova_cbf", CBF.write(it)) }
            }))
            .build()
        val allPatches = buildList {
            add(base)
            addAll(dataComponentPatches)
        }
        return@combinedProvider ItemUtils.mergeDataComponentPatches(allPatches)
    }
    
    /**
     * The default [NamespacedCompound] that is applied to all [ItemStacks][ItemStack] of this [NovaItem].
     */
    private val defaultCompound: NamespacedCompound? by combinedProvider(
        behaviors.map(ItemBehavior::defaultCompound)
    ) { defaultCompounds ->
        val compound = NamespacedCompound()
        for (defaultCompound in defaultCompounds) {
            compound.putAll(defaultCompound)
        }
        
        compound.takeUnless { it.isEmpty() }
    }
    
    /**
     * Creates an [ItemBuilder] for an [ItemStack] of this [NovaItem], in server-side format.
     */
    fun createItemBuilder(modelId: String = "default"): ItemBuilder =
        ItemBuilder(createItemStack(1, modelId))
    
    /**
     * Creates an [ItemStack] of this [NovaItem] with the given [amount] and [modelId] in server-side format.
     */
    fun createItemStack(amount: Int = 1, modelId: String = "default"): ItemStack =
        createItemStack { putString("modelId", modelId) }.also { it.amount = amount }
    
    private fun createItemStack(writeModelId: CompoundTag.() -> Unit): ItemStack {
        val itemStack = MojangStack(PacketItems.SERVER_SIDE_ITEM_HOLDER, 1, defaultPatch)
        itemStack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { customData ->
            customData.update { compoundTag ->
                val novaCompound = compoundTag.getCompound("nova") // should be present in defaultPatch
                writeModelId(novaCompound)
            }
        }
        
        return itemStack.asBukkitMirror()
    }
    
    /**
     * Checks whether this [NovaItem] has an [ItemBehavior] of the reified type [T], or a subclass of it.
     */
    inline fun <reified T : Any> hasBehavior(): Boolean =
        hasBehavior(T::class)
    
    /**
     * Checks whether this [NovaItem] has an [ItemBehavior] of the specified class [type], or a subclass of it.
     */
    fun <T : Any> hasBehavior(type: KClass<T>): Boolean =
        behaviors.any { type.isSuperclassOf(it::class) }
    
    /**
     * Gets the first [ItemBehavior] that is an instance of [T], or null if there is none.
     */
    inline fun <reified T : Any> getBehaviorOrNull(): T? =
        getBehaviorOrNull(T::class)
    
    /**
     * Gets the first [ItemBehavior] that is an instance of [type] or a subclass, or null if there is none.
     */
    fun <T : Any> getBehaviorOrNull(type: KClass<T>): T? =
        behaviors.firstOrNull { type.isSuperclassOf(it::class) } as T?
    
    /**
     * Gets the first [ItemBehavior] that is an instance of [T], or throws an [IllegalStateException] if there is none.
     */
    inline fun <reified T : Any> getBehavior(): T =
        getBehavior(T::class)
    
    /**
     * Gets the first [ItemBehavior] that is an instance of [behavior], or throws an [IllegalStateException] if there is none.
     */
    fun <T : Any> getBehavior(behavior: KClass<T>): T =
        getBehaviorOrNull(behavior) ?: throw IllegalStateException("Item $id does not have a behavior of type ${behavior.simpleName}")
    
    /**
     * Modifies the block damage of this [NovaItem] when breaking a block.
     */
    internal fun modifyBlockDamage(player: Player, itemStack: ItemStack, damage: Double): Double {
        return behaviors.fold(damage) { currentDamage, behavior -> behavior.modifyBlockDamage(player, itemStack, currentDamage) }
    }
    
    /**
     * Modifies the client-side stack of this [NovaItem], in the context that it is sent to [player] and has [data].
     */
    internal fun modifyClientSideStack(player: Player?, itemStack: ItemStack, data: NamespacedCompound): ItemStack {
        return behaviors.fold(itemStack) { stack, behavior -> behavior.modifyClientSideStack(player, stack, data) }
    }
    
    private fun loadConfiguredAttributeModifiers(): ItemAttributeModifiers {
        val section = Configs.getOrNull(configId)?.node("attribute_modifiers")
        if (section == null || section.virtual())
            return ItemAttributeModifiers.EMPTY
        
        val builder = ItemAttributeModifiers.builder()
        for ((slotName, attributesNode) in section.childrenMap()) {
            try {
                val slotGroup = EquipmentSlotGroup.entries.firstOrNull { it.name.equals(slotName as String, true) }
                    ?: throw IllegalArgumentException("Unknown equipment slot group: $slotName")
                
                for ((idx, attributeNode) in attributesNode.childrenList().withIndex()) {
                    try {
                        val attribute = attributeNode.node("attribute").get<Attribute>()
                            ?: throw NoSuchElementException("Missing value 'attribute'")
                        val operation = attributeNode.node("operation").get<AttributeModifier.Operation>()
                            ?: throw NoSuchElementException("Missing value 'operation'")
                        val value = attributeNode.node("value").get<Double>()
                            ?: throw NoSuchElementException("Missing value 'value'")
                        
                        builder.add(
                            BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute),
                            AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath(
                                    "nova",
                                    "configured_attribute_modifier_${slotGroup}_$idx"
                                ),
                                value,
                                operation
                            ),
                            slotGroup
                        )
                    } catch (e: Exception) {
                        LOGGER.logExceptionMessages(Level.WARNING, "Failed to load attribute modifier for $this, $slotGroup with index $idx", e)
                    }
                }
            } catch (e: Exception) {
                LOGGER.logExceptionMessages(Level.WARNING, "Failed to load attribute modifier for $this", e)
            }
        }
        
        return builder.build()
    }
    
    //<editor-fold desc="event methods", defaultstate="collapsed">
    internal fun handleInteract(player: Player, itemStack: ItemStack, action: Action, wrappedEvent: WrappedPlayerInteractEvent) {
        behaviors.forEach { it.handleInteract(player, itemStack, action, wrappedEvent) }
    }
    
    internal fun handleEntityInteract(player: Player, itemStack: ItemStack, clicked: Entity, event: PlayerInteractAtEntityEvent) {
        behaviors.forEach { it.handleEntityInteract(player, itemStack, clicked, event) }
    }
    
    internal fun handleAttackEntity(player: Player, itemStack: ItemStack, attacked: Entity, event: EntityDamageByEntityEvent) {
        behaviors.forEach { it.handleAttackEntity(player, itemStack, attacked, event) }
    }
    
    internal fun handleBreakBlock(player: Player, itemStack: ItemStack, event: BlockBreakEvent) {
        behaviors.forEach { it.handleBreakBlock(player, itemStack, event) }
    }
    
    internal fun handleDamage(player: Player, itemStack: ItemStack, event: PlayerItemDamageEvent) {
        behaviors.forEach { it.handleDamage(player, itemStack, event) }
    }
    
    internal fun handleBreak(player: Player, itemStack: ItemStack, event: PlayerItemBreakEvent) {
        behaviors.forEach { it.handleBreak(player, itemStack, event) }
    }
    
    internal fun handleEquip(player: Player, itemStack: ItemStack, equipped: Boolean, event: ArmorEquipEvent) {
        behaviors.forEach { it.handleEquip(player, itemStack, equipped, event) }
    }
    
    internal fun handleInventoryClick(player: Player, itemStack: ItemStack, event: InventoryClickEvent) {
        behaviors.forEach { it.handleInventoryClick(player, itemStack, event) }
    }
    
    internal fun handleInventoryClickOnCursor(player: Player, itemStack: ItemStack, event: InventoryClickEvent) {
        behaviors.forEach { it.handleInventoryClickOnCursor(player, itemStack, event) }
    }
    
    internal fun handleInventoryHotbarSwap(player: Player, itemStack: ItemStack, event: InventoryClickEvent) {
        behaviors.forEach { it.handleInventoryHotbarSwap(player, itemStack, event) }
    }
    
    internal fun handleBlockBreakAction(player: Player, itemStack: ItemStack, event: BlockBreakActionEvent) {
        behaviors.forEach { it.handleBlockBreakAction(player, itemStack, event) }
    }
    
    internal fun handleRelease(player: Player, itemStack: ItemStack, event: ServerboundPlayerActionPacketEvent) {
        behaviors.forEach { it.handleRelease(player, itemStack, event) }
    }
    //</editor-fold>
    
    override fun toString() = id.toString()
    
    companion object {
        
        val CODEC = NovaRegistries.ITEM.byNameCodec()
        
    }
    
}