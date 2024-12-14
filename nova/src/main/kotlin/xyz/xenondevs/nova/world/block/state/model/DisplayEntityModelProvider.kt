package xyz.xenondevs.nova.world.block.state.model

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.minecraft.world.level.block.state.BlockState
import org.bukkit.Material
import org.bukkit.entity.Display.Brightness
import org.bukkit.inventory.ItemStack
import org.joml.Matrix4fc
import xyz.xenondevs.nova.util.item.requiresLight
import xyz.xenondevs.nova.util.setBlockState
import xyz.xenondevs.nova.util.setBlockStateNoUpdate
import xyz.xenondevs.nova.util.setBlockStateSilently
import xyz.xenondevs.nova.util.withoutBlockMigration
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.fakeentity.FakeEntity
import xyz.xenondevs.nova.world.fakeentity.impl.FakeItemDisplay
import xyz.xenondevs.nova.world.fakeentity.metadata.impl.ItemDisplayMetadata
import java.util.concurrent.ConcurrentHashMap

internal data class DisplayEntityBlockModelData(
    val models: List<Model>,
    val hitboxType: BlockState
) {
    
    internal data class Model(val model: Key, val transform: Matrix4fc) {
        
        val itemStack: ItemStack
            get() = ItemStack(Material.PAPER).apply {
                @Suppress("UnstableApiUsage")
                setData(DataComponentTypes.ITEM_MODEL, model)
            }
        
    }
    
}

/**
 * A block model provider that uses display entities to display the block model.
 */
internal data object DisplayEntityBlockModelProvider : BlockModelProvider<DisplayEntityBlockModelData> {
    
    val entities = ConcurrentHashMap<BlockPos, List<FakeItemDisplay>>()
    
    override fun set(pos: BlockPos, info: DisplayEntityBlockModelData, method: BlockUpdateMethod) {
        placeHitbox(pos, info, method)
        load(pos, info)
    }
    
    private fun placeHitbox(pos: BlockPos, info: DisplayEntityBlockModelData, method: BlockUpdateMethod) {
        withoutBlockMigration(pos) {
            when (method) {
                BlockUpdateMethod.DEFAULT -> pos.setBlockState(info.hitboxType)
                BlockUpdateMethod.NO_UPDATE -> pos.setBlockStateNoUpdate(info.hitboxType)
                BlockUpdateMethod.SILENT -> pos.setBlockStateSilently(info.hitboxType)
            }
        }
    }
    
    override fun load(pos: BlockPos, info: DisplayEntityBlockModelData) {
        createItemDisplays(pos, info)
    }
    
    override fun remove(pos: BlockPos, method: BlockUpdateMethod) {
        super.remove(pos, method)
        entities.remove(pos)?.forEach(FakeEntity<*>::remove)
    }
    
    override fun unload(pos: BlockPos) {
        entities.remove(pos)?.forEach(FakeEntity<*>::remove)
    }
    
    override fun replace(pos: BlockPos, info: DisplayEntityBlockModelData, method: BlockUpdateMethod) {
        placeHitbox(pos, info, method)
        
        // re-use as many existing entities as possible
        val prevEntities = entities[pos] ?: emptyList()
        entities[pos] = info.models.mapIndexed { i, model ->
            prevEntities.getOrNull(i)
                ?.also { prevEntity -> prevEntity.updateEntityData(true) { setMetadata(this, info, model) } }
                ?: FakeItemDisplay(pos.location.add(0.5, 0.5, 0.5)) { _, data -> setMetadata(data, info, model) }
        }
    }
    
    private fun createItemDisplays(pos: BlockPos, info: DisplayEntityBlockModelData) {
        if (pos in entities.keys)
            throw IllegalStateException("ItemDisplay already exists at $pos")
        
        entities[pos] = info.models.map { model ->
            FakeItemDisplay(pos.location.add(0.5, 0.5, 0.5)) { _, data -> setMetadata(data, info, model) }
        }
    }
    
    private fun setMetadata(data: ItemDisplayMetadata, info: DisplayEntityBlockModelData, model: DisplayEntityBlockModelData.Model) {
        // TODO: proper light level
        if (info.hitboxType.bukkitMaterial.requiresLight) {
            data.brightness = Brightness(15, 15)
        }
        
        data.itemStack = model.itemStack
        data.transform = model.transform
    }
    
}