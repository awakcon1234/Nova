@file:Suppress("DEPRECATION", "MemberVisibilityCanBePrivate")

package xyz.xenondevs.nova.world.item.logic

import com.mojang.serialization.Dynamic
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponentPredicate
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData.DataValue
import net.minecraft.resources.RegistryOps
import net.minecraft.util.Unit
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.util.CraftMagicNumbers
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import xyz.xenondevs.nova.initialize.InitFun
import xyz.xenondevs.nova.initialize.InternalInit
import xyz.xenondevs.nova.initialize.InternalInitStage
import xyz.xenondevs.nova.integration.customitems.CustomItemServiceManager
import xyz.xenondevs.nova.network.event.PacketHandler
import xyz.xenondevs.nova.network.event.PacketListener
import xyz.xenondevs.nova.network.event.clientbound.ClientboundContainerSetContentPacketEvent
import xyz.xenondevs.nova.network.event.clientbound.ClientboundContainerSetSlotPacketEvent
import xyz.xenondevs.nova.network.event.clientbound.ClientboundMerchantOffersPacketEvent
import xyz.xenondevs.nova.network.event.clientbound.ClientboundSetEntityDataPacketEvent
import xyz.xenondevs.nova.network.event.clientbound.ClientboundSetEquipmentPacketEvent
import xyz.xenondevs.nova.network.event.clientbound.ClientboundUpdateRecipesPacketEvent
import xyz.xenondevs.nova.network.event.registerPacketListener
import xyz.xenondevs.nova.network.event.serverbound.ServerboundSetCreativeModeSlotPacketEvent
import xyz.xenondevs.nova.registry.NovaRegistries
import xyz.xenondevs.nova.resources.ResourceGeneration
import xyz.xenondevs.nova.serialization.cbf.NamespacedCompound
import xyz.xenondevs.nova.util.REGISTRY_ACCESS
import xyz.xenondevs.nova.util.component.adventure.isEmpty
import xyz.xenondevs.nova.util.component.adventure.withoutPreFormatting
import xyz.xenondevs.nova.util.data.NBTUtils
import xyz.xenondevs.nova.util.data.getCompoundOrNull
import xyz.xenondevs.nova.util.data.getFirstOrThrow
import xyz.xenondevs.nova.util.data.getStringOrNull
import xyz.xenondevs.nova.util.get
import xyz.xenondevs.nova.util.item.novaCompound
import xyz.xenondevs.nova.util.item.unsafeCustomData
import xyz.xenondevs.nova.util.item.unsafeNovaTag
import xyz.xenondevs.nova.util.item.update
import xyz.xenondevs.nova.util.registerEvents
import xyz.xenondevs.nova.util.serverPlayer
import xyz.xenondevs.nova.util.unwrap
import xyz.xenondevs.nova.world.item.NovaItem
import xyz.xenondevs.nova.world.item.recipe.RecipeManager
import com.mojang.datafixers.util.Pair as MojangPair
import net.minecraft.world.item.ItemStack as MojangStack

@InternalInit(
    stage = InternalInitStage.POST_WORLD,
    dependsOn = [ResourceGeneration.PreWorld::class]
)
internal object PacketItems : Listener, PacketListener {
    
    val SERVER_SIDE_MATERIAL = Material.SHULKER_SHELL
    val SERVER_SIDE_ITEM = CraftMagicNumbers.getItem(SERVER_SIDE_MATERIAL)!!
    val SERVER_SIDE_ITEM_HOLDER = BuiltInRegistries.ITEM.wrapAsHolder(SERVER_SIDE_ITEM)
    const val SKIP_SERVER_SIDE_TOOLTIP = "NovaSkipPacketItems"
    private val INVUI_SLOT_KEY = NamespacedKey("nova", "slot")
    
    @InitFun
    private fun init() {
        registerEvents()
        registerPacketListener()
    }
    
    //<editor-fold desc="packet events", defaultstate="collapsed">
    @PacketHandler
    private fun handleSetContentPacket(event: ClientboundContainerSetContentPacketEvent) {
        val player = event.player
        val items = event.items
        
        items.forEachIndexed { i, item ->
            items[i] = getClientSideStack(player, item)
        }
        
        event.carriedItem = getClientSideStack(player, event.carriedItem)
    }
    
    @PacketHandler
    private fun handleSetSlotPacket(event: ClientboundContainerSetSlotPacketEvent) {
        event.item = getClientSideStack(event.player, event.item)
    }
    
    @PacketHandler
    private fun handleEntityData(event: ClientboundSetEntityDataPacketEvent) {
        val oldItems = event.packedItems
        val newItems = ArrayList<DataValue<*>>()
        for (dataValue in oldItems) {
            val value = dataValue.value
            if (value is MojangStack) {
                newItems += DataValue(
                    dataValue.id,
                    EntityDataSerializers.ITEM_STACK,
                    getClientSideStack(event.player, value, false)
                )
            } else {
                newItems += dataValue
            }
        }
        event.packedItems = newItems
    }
    
    @PacketHandler
    private fun handleSetEquipment(event: ClientboundSetEquipmentPacketEvent) {
        val player = event.player
        val slots = ArrayList(event.slots).also { event.slots = it }
        
        for ((i, pair) in slots.withIndex()) {
            slots[i] = MojangPair(
                pair.first,
                getClientSideStack(player, pair.second)
            )
        }
    }
    
    @PacketHandler
    private fun handleCreativeSetItem(event: ServerboundSetCreativeModeSlotPacketEvent) {
        event.itemStack = getServerSideStack(event.itemStack)
    }
    
    @PacketHandler
    private fun handleRecipes(event: ClientboundUpdateRecipesPacketEvent) {
        event.recipes = event.recipes.map { recipe ->
            val id = recipe.id
            RecipeManager.clientsideRecipes[id]?.let { RecipeHolder(id, it) } ?: recipe
        }
    }
    
    @PacketHandler
    private fun handleMerchantOffers(event: ClientboundMerchantOffersPacketEvent) {
        val newOffers = MerchantOffers()
        
        event.offers.forEach { offer ->
            val stackA = getClientSideStack(event.player, offer.baseCostA.itemStack)
            val costA = ItemCost(stackA.itemHolder, stackA.count, DataComponentPredicate.EMPTY, stackA)
            val costB = offer.costB.map {
                val stackB = getClientSideStack(event.player, it.itemStack)
                ItemCost(stackB.itemHolder, stackB.count, DataComponentPredicate.EMPTY, stackB)
            }
            newOffers += MerchantOffer(
                costA, costB, getClientSideStack(event.player, offer.result),
                offer.uses, offer.maxUses, offer.xp, offer.priceMultiplier, offer.demand
            )
        }
        
        event.offers = newOffers
    }
    //</editor-fold>
    
    //<editor-fold desc="server-side stack -> client-side stack", defaultstate="collapsed">
    fun getClientSideStack(player: Player?, itemStack: MojangStack, storeServerSideTag: Boolean = true): MojangStack {
        if (itemStack.isEmpty)
            return itemStack
        
        return if (itemStack.unsafeNovaTag != null) {
            getClientSideNovaStack(player, itemStack, storeServerSideTag)
        } else getClientSideVanillaStack(player, itemStack, storeServerSideTag)
    }
    
    //<editor-fold desc="Nova", defaultstate="collapsed">
    private fun getClientSideNovaStack(player: Player?, itemStack: MojangStack, storeServerSideTag: Boolean): MojangStack {
        val novaTag = itemStack.unsafeNovaTag // read-only!
            ?: return itemStack
        val id = novaTag.getStringOrNull("id")
            ?: return getUnknownItem(itemStack, null)
        val novaItem = NovaRegistries.ITEM[id]
            ?: return getUnknownItem(itemStack, id)
        
        // client-side item stack copy
        val newItemType = CraftMagicNumbers.getItem(novaItem.vanillaMaterial)
        val newItemHolder = BuiltInRegistries.ITEM.wrapAsHolder(newItemType)
        var newItemStack = MojangStack(
            newItemHolder, itemStack.count,
            buildClientSideDataComponentsPatch(newItemType, novaItem, itemStack.componentsPatch)
        )
        
        // custom model data
        val namedModelId = novaTag.getStringOrNull("modelId")
        val customModelData = when {
            namedModelId != null -> novaItem.model.getCustomModelData(namedModelId)
                ?: return getUnknownItem(itemStack, id, namedModelId)
            
            else -> novaItem.model.getCustomModelData("default")
                ?: return getUnknownItem(itemStack, id)
        }
        newItemStack.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(customModelData))
        
        // further customization through item behaviors
        val novaCompound = itemStack.novaCompound ?: NamespacedCompound.EMPTY
        newItemStack = novaItem.modifyClientSideStack(player, newItemStack.asBukkitMirror(), novaCompound).unwrap()
        
        if (shouldHideEntireTooltip(itemStack)) {
            newItemStack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE)
        } else { // generate tooltip server-side and apply as lore
            // we do not want data component modifications done by item behaviors in modifyClientSideStack
            // to be reflected in the tooltip, except for the item lore itself
            val itemStackToGenerateTooltipOf = itemStack.copy()
            itemStackToGenerateTooltipOf.set(DataComponents.LORE, newItemStack.get(DataComponents.LORE))
            applyServerSideTooltip(newItemStack, generateNovaTooltipLore(player, novaItem, novaCompound.keys.size, itemStackToGenerateTooltipOf))
        }
        
        // save server-side nbt data (for creative mode)
        // this also drops existing custom data, which is ignored by the client anyway
        if (storeServerSideTag)
            storeServerSideTag(newItemStack, itemStack)
        
        return newItemStack
    }
    
    private fun buildClientSideDataComponentsPatch(vanilla: Item, nova: NovaItem, patch: DataComponentPatch): DataComponentPatch {
        val builder = DataComponentPatch.builder()
        
        // remove vanilla default base components
        for (vanillaBase in vanilla.components()) {
            builder.remove(vanillaBase.type)
        }
        
        // add nova default base components
        mergeIntoClientSidePatch(builder, nova.baseDataComponents)
        // add nova default patch components
        mergeIntoClientSidePatch(builder, nova.defaultPatch)
        // add actual item stack patch components
        mergeIntoClientSidePatch(builder, patch)
        
        return builder.build()
    }
    
    private fun mergeIntoClientSidePatch(builder: DataComponentPatch.Builder, base: DataComponentMap) {
        for (customBase in base) {
            if (isIrrelevantClientSideComponent(customBase.type))
                continue
            
            builder.set(customBase)
        }
    }
    
    private fun mergeIntoClientSidePatch(builder: DataComponentPatch.Builder, patch: DataComponentPatch) {
        for ((type, valueOpt) in patch.entrySet()) {
            if (isIrrelevantClientSideComponent(type))
                continue
            
            if (valueOpt.isPresent) {
                builder.set(TypedDataComponent.createUnchecked(type, valueOpt.get()))
            } else {
                builder.remove(type)
            }
        }
    }
    
    private fun isIrrelevantClientSideComponent(type: DataComponentType<*>): Boolean {
        return type == DataComponents.CUSTOM_DATA
    }
    
    private fun getUnknownItem(itemStack: MojangStack, id: String?, modelId: String = "default"): MojangStack {
        return MojangStack(Items.BARRIER).apply {
            set(
                DataComponents.ITEM_NAME,
                Component.literal(
                    "Unknown item: $id" + if (modelId != "default") ":$modelId" else ""
                ).withStyle(ChatFormatting.RED)
            )
            storeServerSideTag(this, itemStack)
        }
    }
    
    private fun generateNovaTooltipLore(player: Player?, novaItem: NovaItem, cbfTagCount: Int, itemStack: MojangStack): List<Component> {
        val isAdvanced = player?.let(AdvancedTooltips::hasNovaTooltips) == true
        val lore = generateTooltipLore(player, isAdvanced, itemStack).toMutableList()
        
        // entire tooltip is hidden
        if (lore.isEmpty())
            return emptyList()
        
        if (isAdvanced) {
            // nova item id
            lore[lore.size - 2] = Component.literal(novaItem.id.toString()).withStyle(ChatFormatting.DARK_GRAY)
            
            // cbf tag count
            if (cbfTagCount > 0) {
                lore.add(
                    Component.translatable(
                        "item.cbf_tags",
                        Component.literal(cbfTagCount.toString())
                    ).withStyle(ChatFormatting.DARK_GRAY)
                )
            }
        }
        
        return lore
    }
    //</editor-fold>
    
    //<editor-fold desc="Vanilla", defaultstate="collapsed">
    private fun getClientSideVanillaStack(player: Player?, itemStack: MojangStack, storeServerSideTag: Boolean): MojangStack {
        val newItemStack = itemStack.copy()
        var modified = false
        
        if (correctArmorColor(newItemStack)) {
            modified = true
        }
        if (shouldHideEntireTooltip(itemStack)) {
            newItemStack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE)
            modified = true
        } else if (itemStack.unsafeCustomData?.contains(SKIP_SERVER_SIDE_TOOLTIP) != true) {
            val isAdvanced = player?.let(AdvancedTooltips::hasVanillaTooltips) == true
            if (isAdvanced || modified) { // server-side tooltip is only required if the server-side stack differs from the client-side stack
                applyServerSideTooltip(newItemStack, generateTooltipLore(player, isAdvanced, itemStack))
                modified = true
            }
        } else {
            disableClientSideTooltip(newItemStack)
            modified = true
        }
        
        // save server-side nbt data (for creative mode)
        // this also drops existing custom data, which is ignored by the client anyway
        if (modified && storeServerSideTag)
            storeServerSideTag(newItemStack, itemStack)
        
        return newItemStack
    }
    
    /**
     * Fixes the [DataComponents.DYED_COLOR] rgb value of vanilla armor items to prevent accidental use of
     * custom textures and returns whether the item stack was modified.
     */
    private fun correctArmorColor(itemStack: MojangStack): Boolean {
        val color = itemStack.get(DataComponents.DYED_COLOR)
            ?: return false
        val rgb = color.rgb
        
        if (rgb % 2 == 0)
            return false
        
        if (CustomItemServiceManager.getId(itemStack.asBukkitMirror()) != null)
            return false
        
        itemStack.set(DataComponents.DYED_COLOR, DyedItemColor(rgb - 1, color.showInTooltip))
        
        return true
    }
    //</editor-fold>
    
    //<editor-fold desc="tooltip", defaultstate="collapsed">
    private fun applyServerSideTooltip(itemStack: ItemStack, tooltip: List<Component>) {
        val lore = tooltip.fold(ItemLore.EMPTY) { l, line ->
            l.withLineAdded(line.withoutPreFormatting())
        }
        itemStack.set(DataComponents.LORE, lore)
        disableClientSideTooltip(itemStack)
    }
    
    private fun generateTooltipLore(player: Player?, advancedTooltips: Boolean, itemStack: MojangStack): List<Component> {
        val lore = itemStack.getTooltipLines(
            Item.TooltipContext.of(REGISTRY_ACCESS),
            player?.serverPlayer,
            if (advancedTooltips) TooltipFlag.ADVANCED else TooltipFlag.NORMAL
        )
        
        // entire tooltip is hidden
        if (lore.isEmpty())
            return emptyList()
        
        lore.removeAt(0) // item name
        
        return lore
    }
    
    private fun disableClientSideTooltip(itemStack: MojangStack) {
        itemStack.update(DataComponents.ATTRIBUTE_MODIFIERS) { it.withTooltip(false) }
        itemStack.update(DataComponents.CAN_BREAK) { it.withTooltip(false) }
        itemStack.update(DataComponents.CAN_PLACE_ON) { it.withTooltip(false) }
        itemStack.update(DataComponents.DYED_COLOR) { it.withTooltip(false) }
        itemStack.update(DataComponents.ENCHANTMENTS) { it.withTooltip(false) }
        itemStack.update(DataComponents.JUKEBOX_PLAYABLE) { it.withTooltip(false) }
        itemStack.update(DataComponents.STORED_ENCHANTMENTS) { it.withTooltip(false) }
        itemStack.update(DataComponents.TRIM) { it.withTooltip(false) }
        itemStack.update(DataComponents.UNBREAKABLE) { it.withTooltip(false) }
        // this disables all other tooltips that cannot be disabled through a specific component
        // it also disables the bundle preview image, but since bundles are still experimental, this is fine
        itemStack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE)
    }
    
    private fun shouldHideEntireTooltip(itemStack: MojangStack): Boolean {
        // all items in InvUI guis have a "slot" tag in the pdc
        // the goal here is to hide the tooltip of all gui items with no name
        return itemStack.asBukkitMirror().persistentDataContainer.has(INVUI_SLOT_KEY) && itemStack.hoverName.isEmpty()
    }
    //</editor-fold>
    //</editor-fold>
    
    //<editor-fold desc="client-side stack -> server-side stack", defaultstate="collapsed">
    fun getServerSideStack(itemStack: MojangStack): MojangStack {
        val serversideTag = itemStack.get(DataComponents.CUSTOM_DATA)?.unsafe
            ?.getCompoundOrNull("NovaServerSideTag")
            ?: return itemStack
        val serversideComponents = decodeComponents(serversideTag)
        
        // use server-side item for all Nova items, otherwise keep current item
        val serversideCustomData = serversideComponents.get(DataComponents.CUSTOM_DATA)?.orElse(null)
        val item = if (serversideCustomData?.unsafe?.contains("nova", NBTUtils.TAG_COMPOUND) == true)
            SERVER_SIDE_ITEM_HOLDER
        else itemStack.itemHolder
        
        return MojangStack(item, itemStack.count, serversideComponents)
    }
    //</editor-fold>
    
    private fun storeServerSideTag(clientSide: MojangStack, serverSide: MojangStack) {
        clientSide.set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply {
            put("NovaServerSideTag", encodeComponents(serverSide))
        }))
    }
    
    private fun encodeComponents(itemStack: MojangStack): Tag {
        val patch = itemStack.componentsPatch
        return DataComponentPatch.CODEC.encodeStart(
            RegistryOps.create(NbtOps.INSTANCE, REGISTRY_ACCESS),
            patch
        ).getOrThrow()
    }
    
    private fun decodeComponents(components: Tag): DataComponentPatch {
        return DataComponentPatch.CODEC.decode(Dynamic(
            RegistryOps.create(NbtOps.INSTANCE, REGISTRY_ACCESS),
            components
        )).getFirstOrThrow()
    }
    
}