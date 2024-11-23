package xyz.xenondevs.nova.resources.layout.equipment

import xyz.xenondevs.nova.resources.ResourcePath
import xyz.xenondevs.nova.resources.ResourceType
import xyz.xenondevs.nova.resources.builder.data.EquipmentModel

internal sealed interface EquipmentLayout

internal data class StaticEquipmentLayout(
    val types: Map<EquipmentModel.Type, List<Layer<*>>>,
    val cameraOverlay: ResourcePath<ResourceType.Texture>?
) : EquipmentLayout {
    
    fun toEquipmentModel() = EquipmentModel(
        types.mapValues { (_, layers) ->
            layers.map { layer ->
                EquipmentModel.Layer(layer.texture, layer.usePlayerTexture, layer.dyeable)
            }
        }
    )
    
    data class Layer<out T : ResourceType.EquipmentTexture>(
        val resourceType: T,
        val texture: ResourcePath<T>,
        val usePlayerTexture: Boolean,
        val emissivityMap: ResourcePath<T>?,
        val dyeable: EquipmentModel.Layer.Dyeable?
    )
    
}

internal data class AnimatedEquipmentLayout(
    val types: Map<EquipmentModel.Type, List<Layer<*>>>,
    val cameraOverlay: Animation<ResourceType.Texture>?
) : EquipmentLayout {
    
    internal data class Layer<out T : ResourceType.EquipmentTexture>(
        val resourceType: T,
        val texture: Animation<T>,
        val emissivityMap: Animation<T>?,
        val dyeable: EquipmentModel.Layer.Dyeable?
    )
    
    internal data class Animation<out T : ResourceType.Texture>(
        val frames: List<ResourcePath<T>>,
        val ticksPerFrame: Int,
        val interpolationMode: InterpolationMode
    ) {
        
        init {
            require(frames.isNotEmpty()) { "Frame count must be greater than 0" }
            require(ticksPerFrame > 0) { "Ticks per frame must be greater than 0" }
        }
        
    }
    
}


/**
 * The interpolation mode for armor texture animations.
 */
enum class InterpolationMode {
    
    /**
     * Individual animation frames are displayed without any interpolation.
     */
    NONE,
    
    /**
     * A linear interpolation is used to animate between frames.
     */
    LINEAR
    
}