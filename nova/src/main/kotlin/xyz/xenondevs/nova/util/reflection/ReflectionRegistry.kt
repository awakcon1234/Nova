package xyz.xenondevs.nova.util.reflection

import com.mojang.brigadier.tree.CommandNode
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.ItemCombinerMenu
import net.minecraft.world.item.enchantment.EnchantmentCategory
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.HashMapPalette
import net.minecraft.world.level.chunk.LinearPalette
import net.minecraft.world.level.chunk.PalettedContainer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemorySection
import org.bukkit.craftbukkit.v1_19_R1.block.CraftBlock
import org.bukkit.event.HandlerList
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import xyz.xenondevs.nova.util.reflection.ReflectionUtils.getCB
import xyz.xenondevs.nova.util.reflection.ReflectionUtils.getCBClass
import xyz.xenondevs.nova.util.reflection.ReflectionUtils.getClass
import xyz.xenondevs.nova.util.reflection.ReflectionUtils.getConstructor
import xyz.xenondevs.nova.util.reflection.ReflectionUtils.getField
import xyz.xenondevs.nova.util.reflection.ReflectionUtils.getMethod
import java.util.*
import java.util.function.Consumer
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KProperty1
import net.minecraft.world.entity.Entity as MojangEntity
import net.minecraft.world.entity.EquipmentSlot as MojangEquipmentSlot
import net.minecraft.world.entity.LivingEntity as MojangLivingEntity
import net.minecraft.world.entity.player.Inventory as MojangInventory
import net.minecraft.world.entity.player.Player as MojangPlayer
import net.minecraft.world.item.Item as MojangItem
import net.minecraft.world.item.ItemStack as MojangStack

@Suppress("MemberVisibilityCanBePrivate")
internal object ReflectionRegistry {
    
    // Paths
    val CB_PACKAGE_PATH = getCB()
    
    // Classes
    val CB_CRAFT_META_ITEM_CLASS = getCBClass("inventory.CraftMetaItem")
    val SECTION_PATH_DATA_CLASS = getClass("org.bukkit.configuration.SectionPathData")
    val PALETTED_CONTAINER_DATA_CLASS = getClass("SRC(net.minecraft.world.level.chunk.PalettedContainer\$Data)")
    val HOLDER_SET_DIRECT_CLASS = getClass("SRC(net.minecraft.core.HolderSet\$Direct)")
    
    // Constructors
    val ENUM_MAP_CONSTRUCTOR = getConstructor(EnumMap::class.java, false, Class::class.java)
    val MEMORY_SECTION_CONSTRUCTOR = getConstructor(MemorySection::class.java, true, ConfigurationSection::class.java, String::class.java)
    val CHUNK_ACCESS_CONSTRUCTOR = getConstructor(ChunkAccess::class.java, false, ChunkPos::class.java, UpgradeData::class.java, LevelHeightAccessor::class.java, Registry::class.java, Long::class.java, Array<LevelChunkSection>::class.java, BlendingData::class.java)
    val SECTION_PATH_DATA_CONSTRUCTOR = getConstructor(SECTION_PATH_DATA_CLASS, true, Any::class.java)
    val TARGET_BLOCK_STATE_CONSTRUCTOR = getConstructor(TargetBlockState::class.java, true, RuleTest::class.java, BlockState::class.java)
    
    // Methods
    val CB_CRAFT_META_APPLY_TO_METHOD = getMethod(CB_CRAFT_META_ITEM_CLASS, true, "applyToItem", CompoundTag::class.java)
    val FEATURE_SORTER_BUILD_FEATURES_PER_STEP_METHOD = getMethod(FeatureSorter::class.java, true, "SRM(net.minecraft.world.level.biome.FeatureSorter buildFeaturesPerStep)", List::class.java, java.util.function.Function::class.java, Boolean::class.java)
    val STATE_HOLDER_CODEC_METHOD = getMethod(StateHolder::class.java, true, "SRM(net.minecraft.world.level.block.state.StateHolder codec)", Codec::class.java, Function::class.java)
    val LEVEL_CHUNK_SECTION_SET_BLOCK_STATE_METHOD = getMethod(LevelChunkSection::class.java, true, "SRM(net.minecraft.world.level.chunk.LevelChunkSection setBlockState)", Int::class.java, Int::class.java, Int::class.java, BlockState::class.java, Boolean::class.java)
    val K_PROPERTY_1_GET_DELEGATE_METHOD = getMethod(KProperty1::class.java, false, "getDelegate", Any::class.java)
    val CLASS_LOADER_DEFINE_CLASS_METHOD = getMethod(ClassLoader::class.java, true, "defineClass", String::class.java, ByteArray::class.java, Int::class.java, Int::class.java, ProtectionDomain::class.java)
    val CRAFT_BLOCK_IS_PREFERRED_TOOL_METHOD = getMethod(CraftBlock::class.java, true, "isPreferredTool", BlockState::class.java, MojangStack::class.java)
    val ITEM_STACK_GET_ATTRIBUTE_MODIFIERS_METHOD = getMethod(MojangStack::class.java, false, "SRM(net.minecraft.world.item.ItemStack getAttributeModifiers)", MojangEquipmentSlot::class.java)
    val ITEM_STACK_HURT_AND_BREAK_METHOD = getMethod(MojangStack::class.java, false, "SRM(net.minecraft.world.item.ItemStack hurtAndBreak)", Int::class.java, MojangLivingEntity::class.java, Consumer::class.java)
    val ITEM_STACK_HURT_ENTITY_METHOD = getMethod(MojangStack::class.java, false, "SRM(net.minecraft.world.item.ItemStack hurtEnemy)", MojangLivingEntity::class.java, MojangPlayer::class.java)
    val PLAYER_ATTACK_METHOD = getMethod(MojangPlayer::class.java, false, "SRM(net.minecraft.world.entity.player.Player attack)", MojangEntity::class.java)
    val ANVIL_MENU_CREATE_RESULT_METHOD = getMethod(AnvilMenu::class.java, false, "SRM(net.minecraft.world.inventory.AnvilMenu createResult)")
    val ENCHANTMENT_HELPER_GET_AVAILABLE_ENCHANTMENT_RESULTS_METHOD = getMethod(EnchantmentHelper::class.java, false, "SRM(net.minecraft.world.item.enchantment.EnchantmentHelper getAvailableEnchantmentResults)", Int::class.java, MojangStack::class.java, Boolean::class.java)
    val ENCHANTMENT_HELPER_GET_ENCHANTMENT_COST_METHOD = getMethod(EnchantmentHelper::class.java, true, "SRM(net.minecraft.world.item.enchantment.EnchantmentHelper getEnchantmentCost)", RandomSource::class.java, Int::class.java, Int::class.java, MojangStack::class.java)
    val ENCHANTMENT_HELPER_SELECT_ENCHANTMENT_METHOD = getMethod(EnchantmentHelper::class.java, false, "SRM(net.minecraft.world.item.enchantment.EnchantmentHelper selectEnchantment)", RandomSource::class.java, MojangStack::class.java, Int::class.java, Boolean::class.java)
    val ENCHANTMENT_CATEGORY_CAN_ENCHANT_METHOD = getMethod(EnchantmentCategory::class.java, false, "SRM(net.minecraft.world.item.enchantment.EnchantmentCategory canEnchant)", MojangItem::class.java)
    val ITEM_IS_ENCHANTABLE_METHOD = getMethod(MojangItem::class.java, false, "SRM(net.minecraft.world.item.Item isEnchantable)", MojangStack::class.java)
    val ITEM_GET_ENCHANTMENT_VALUE_METHOD = getMethod(MojangItem::class.java, false, "SRM(net.minecraft.world.item.Item getEnchantmentValue)")
    val EXPERIENCE_ORB_REPAIR_PLAYER_ITEMS_METHOD = getMethod(ExperienceOrb::class.java, true, "SRM(net.minecraft.world.entity.ExperienceOrb repairPlayerItems)", MojangPlayer::class.java, Int::class.java)
    val ITEM_ENTITY_PLAYER_TOUCH_METHOD = getMethod(ItemEntity::class.java, false, "SRM(net.minecraft.world.entity.item.ItemEntity playerTouch)", MojangPlayer::class.java)
    val INVENTORY_ADD_METHOD = getMethod(MojangInventory::class.java, false, "SRM(net.minecraft.world.entity.player.Inventory add)", MojangStack::class.java)
    val ENTITY_PLAY_STEP_SOUND_METHOD = getMethod(MojangEntity::class.java, true, "SRM(net.minecraft.world.entity.Entity playStepSound)", BlockPos::class.java, BlockState::class.java)
    val LIVING_ENTITY_PLAY_BLOCK_FALL_SOUND_METHOD = getMethod(LivingEntity::class.java, true, "SRM(net.minecraft.world.entity.LivingEntity playBlockFallSound)")
    
    // Fields
    val CRAFT_META_ITEM_UNHANDLED_TAGS_FIELD = getField(CB_CRAFT_META_ITEM_CLASS, true, "unhandledTags")
    val COMMAND_NODE_CHILDREN_FIELD = getField(CommandNode::class.java, true, "children")
    val COMMAND_NODE_LITERALS_FIELD = getField(CommandNode::class.java, true, "literals")
    val COMMAND_NODE_ARGUMENTS_FIELD = getField(CommandNode::class.java, true, "arguments")
    val CALLABLE_REFERENCE_RECEIVER_FIELD = getField(CallableReference::class.java, true, "receiver")
    val PREPARE_ITEM_CRAFT_EVENT_MATRIX_FIELD = getField(PrepareItemCraftEvent::class.java, true, "matrix")
    val MEMORY_SECTION_MAP_FIELD = getField(MemorySection::class.java, true, "map")
    val HANDLER_LIST_HANDLERS_FIELD = getField(HandlerList::class.java, true, "handlers")
    val HANDLER_LIST_HANDLER_SLOTS_FIELD = getField(HandlerList::class.java, true, "handlerslots")
    val BLOCK_PHYSICS_EVENT_CHANGED_FIELD = getField(BlockPhysicsEvent::class.java, true, "changed")
    val PALETTED_CONTAINER_DATA_FIELD = getField(PalettedContainer::class.java, true, "SRF(net.minecraft.world.level.chunk.PalettedContainer data)")
    val PALETTED_CONTAINER_DATA_PALETTE_FIELD = getField(PALETTED_CONTAINER_DATA_CLASS, true, "SRF(net.minecraft.world.level.chunk.PalettedContainer\$Data palette)")
    val PALETTED_CONTAINER_DATA_STORAGE_FIELD = getField(PALETTED_CONTAINER_DATA_CLASS, true, "SRF(net.minecraft.world.level.chunk.PalettedContainer\$Data storage)")
    val LINEAR_PALETTE_VALUES_FIELD = getField(LinearPalette::class.java, true, "SRF(net.minecraft.world.level.chunk.LinearPalette values)")
    val HASH_MAP_PALETTE_VALUES_FIELD = getField(HashMapPalette::class.java, true, "SRF(net.minecraft.world.level.chunk.HashMapPalette values)")
    val BLOCK_DEFAULT_BLOCK_STATE_FIELD = getField(Block::class.java, true, "SRF(net.minecraft.world.level.block.Block defaultBlockState)")
    val BLOCK_STATE_CODEC_FIELD = getField(BlockState::class.java, false, "SRF(net.minecraft.world.level.block.state.BlockState CODEC)")
    val MAPPED_REGISTRY_FROZEN_FIELD = getField(MappedRegistry::class.java, true, "SRF(net.minecraft.core.MappedRegistry frozen)")
    val BIOME_GENERATION_SETTINGS_FEATURES_FIELD = getField(BiomeGenerationSettings::class.java, true, "SRF(net.minecraft.world.level.biome.BiomeGenerationSettings features)")
    val LEVEL_CHUNK_SECTION_STATES_FIELD = getField(LevelChunkSection::class.java, true, "SRF(net.minecraft.world.level.chunk.LevelChunkSection states)")
    val LEVEL_CHUNK_SECTION_J_FIELD = getField(LevelChunkSection::class.java, true, "j")
    val HOLDER_SET_DIRECT_CONTENTS_FIELD = getField(HOLDER_SET_DIRECT_CLASS, true, "SRF(net.minecraft.core.HolderSet\$Direct contents)")
    val HOLDER_SET_DIRECT_CONTENTS_SET_FIELD = getField(HOLDER_SET_DIRECT_CLASS, true, "SRF(net.minecraft.core.HolderSet\$Direct contentsSet)")
    val ITEM_COMBINER_MENU_INPUT_SLOTS_FIELD = getField(ItemCombinerMenu::class.java, true, "SRF(net.minecraft.world.inventory.ItemCombinerMenu inputSlots)")
    val ITEM_COMBINER_MENU_PLAYER_FIELD = getField(ItemCombinerMenu::class.java, true, "SRF(net.minecraft.world.inventory.ItemCombinerMenu player)")
    
}
