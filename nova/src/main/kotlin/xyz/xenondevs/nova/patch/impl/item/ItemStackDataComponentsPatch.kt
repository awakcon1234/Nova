@file:Suppress("DEPRECATION")

package xyz.xenondevs.nova.patch.impl.item

import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.component.PatchedDataComponentMap
import net.minecraft.world.item.ItemStack
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodInsnNode
import xyz.xenondevs.bytebase.asm.buildInsnList
import xyz.xenondevs.bytebase.jvm.VirtualClassPath
import xyz.xenondevs.bytebase.util.calls
import xyz.xenondevs.bytebase.util.replaceFirst
import xyz.xenondevs.nova.LOGGER
import xyz.xenondevs.nova.patch.MultiTransformer
import xyz.xenondevs.nova.registry.NovaRegistries
import xyz.xenondevs.nova.util.data.getCompoundOrNull
import xyz.xenondevs.nova.util.data.getStringOrNull
import xyz.xenondevs.nova.util.getValue
import xyz.xenondevs.nova.util.reflection.ReflectionUtils
import xyz.xenondevs.nova.world.item.NovaItem
import xyz.xenondevs.nova.world.item.legacy.ItemStackLegacyConversion
import kotlin.jvm.optionals.getOrNull

private val ITEM_STACK_CONSTRUCTOR = ReflectionUtils.getConstructor(
    ItemStack::class,
    Holder::class, Int::class, DataComponentPatch::class
)

internal object ItemStackDataComponentsPatch : MultiTransformer(ItemStack::class) {
    
    override fun transform() {
        VirtualClassPath[ITEM_STACK_CONSTRUCTOR].replaceFirst(0, 0, buildInsnList {
            invokeStatic(::fromPatch)
        }) { it.opcode == Opcodes.INVOKESTATIC && (it as MethodInsnNode).calls(PatchedDataComponentMap::fromPatch) }
    }
    
    @JvmStatic
    fun fromPatch(base: DataComponentMap, changes: DataComponentPatch): PatchedDataComponentMap {
        val patch = ItemStackLegacyConversion.convert(changes)
        
        val novaItem = patch.get(DataComponents.CUSTOM_DATA)?.getOrNull()?.unsafe
            ?.getCompoundOrNull("nova")
            ?.getStringOrNull("id")
            ?.let(NovaRegistries.ITEM::getValue)
        
        return PatchedDataComponentMap.fromPatch(
            if (novaItem != null) NovaDataComponentMap(novaItem) else base,
            patch
        )
    }
    
}

// this delegating structure is necessary to allow config reloading
internal class NovaDataComponentMap(private val novaItem: NovaItem) : DataComponentMap {
    
    override fun <T : Any?> get(type: DataComponentType<out T>): T? {
        try {
            return novaItem.baseDataComponents.handle.get(type)
        } catch (e: Exception) {
            LOGGER.error("Failed to retrieve base data components for $novaItem", e)
        }
        
        return null
    }
    
    override fun keySet(): Set<DataComponentType<*>> {
        try {
            return novaItem.baseDataComponents.handle.keySet()
        } catch (e: Exception) {
            LOGGER.error("Failed to retrieve base data components for $novaItem", e)
        }
        
        return emptySet()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as NovaDataComponentMap
        
        return novaItem == other.novaItem
    }
    
    override fun hashCode(): Int {
        return novaItem.hashCode()
    }
    
}