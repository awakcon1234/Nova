package xyz.xenondevs.nova.resources.builder.model

import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector4d
import org.joml.Vector4dc
import org.joml.primitives.AABBd
import xyz.xenondevs.nova.resources.ResourcePath
import xyz.xenondevs.nova.resources.ResourceType
import xyz.xenondevs.nova.resources.builder.task.model.ModelContent
import java.util.*

/**
 * A [Minecraft model](https://minecraft.wiki/w/Model).
 *
 * @param parent The path to the parent model or `null` if there is no parent.
 * @param textures A map of texture names to texture paths.
 * @param elements A list of voxels that make up the model.
 * @param ambientOcclusion (Only relevant for block models) Whether ambient occlusion is enabled for the model.
 * @param guiLight (Only relevant for item models) The direction of the light for the item model in the GUI.
 * @param display (Only relevant for item models) A map of display positions to display settings.
 * @param overrides (Only relevant for item models) A list of overrides for the item model. Every override has one
 * or more predicates that determine whether the override model should be used.
 */
data class Model(
    val parent: ResourcePath<ResourceType.Model>? = null,
    val textures: Map<String, String> = emptyMap(),
    val elements: List<Element>? = null,
    
    // block-specific
    val ambientOcclusion: Boolean? = null,
    
    // item-specific
    val guiLight: GuiLight? = null,
    val display: Map<Display.Position, Display> = emptyMap(),
    val overrides: List<Override> = emptyList()
) {
    
    /**
     * Specifies the direction of the light for the item model in the GUI.
     */
    enum class GuiLight { FRONT, SIDE }
    
    /**
     * Determines a case in which a different [model] should be used based on the given [predicate].
     */
    data class Override(val predicate: Map<String, Number>, val model: ResourcePath<ResourceType.Model>)
    
    /**
     * An axis in 3D space.
     */
    enum class Axis { X, Y, Z }
    
    /**
     * A direction in 3D space.
     */
    enum class Direction(val axis: Axis) {
        NORTH(Axis.Z),
        EAST(Axis.X),
        SOUTH(Axis.Z),
        WEST(Axis.X),
        UP(Axis.Y),
        DOWN(Axis.Y)
    }
    
    /**
     * A voxel of a [Model].
     *
     * @param from The start position of the voxel.
     * @param to The end position of the voxel.
     * @param rotation The rotation of the voxel.
     * @param faces A map of the voxel's faces.
     * @params shade Whether shadows are rendered.
     */
    data class Element(
        val from: Vector3dc,
        val to: Vector3dc,
        val rotation: Rotation?,
        val faces: Map<Direction, Face>,
        val shade: Boolean
    ) {
        
        /**
         * A face of an [Element].
         *
         * @param uv The area of the texture in the format (fromX, fromY, toX, toY).
         * @param texture The name of the texture. Resolved from the [Model.textures] map.
         * @param cullface Used to check whether the face does not need to be rendered if an occluding block is present
         * in the given direction.
         * @param rotation The rotation of the texture.
         * @param tintIndex Specifies the tint color for certain block- and item types.
         */
        data class Face(
            val uv: Vector4dc?,
            val texture: String,
            val cullface: Direction?,
            val rotation: Int,
            val tintIndex: Int
        )
        
        /**
         * The rotation of an [Element].
         *
         * @param angle The angle of the rotation. Can be 45.0, 22.5, -22.5, -45.0
         * @param axis The axis of the rotation.
         * @param origin The origin / pivot point of the rotation.
         * @param rescale Whether the model should be rescaled to fit the new size.
         * (for example a 45° rotation stretches the element by sqrt(2))
         */
        data class Rotation(
            val angle: Double,
            val axis: Axis,
            val origin: Vector3dc,
            val rescale: Boolean
        )
        
        /**
         * Generates the UV coordinates for a face based the position of this [Element] for the given [direction].
         * This results in the same UV coordinates as those that are automatically generated by the client
         * for faces that do not specify UV coordinates.
         */
        fun generateUV(direction: Direction): Vector4dc =
            when (direction) {
                Direction.NORTH, Direction.SOUTH -> Vector4d(from.x(), from.y(), to.x(), to.y())
                Direction.EAST, Direction.WEST -> Vector4d(from.z(), from.y(), to.z(), to.y())
                Direction.UP, Direction.DOWN -> Vector4d(from.x(), from.z(), to.x(), to.z())
            }
        
    }
    
    /**
     * The display settings for an item model.
     *
     * @param rotation The rotation of the model.
     * @param translation The translation of the model.
     * @param scale The scale of the model.
     */
    data class Display(
        val rotation: Vector3dc,
        val translation: Vector3dc,
        val scale: Vector3dc
    ) {
        
        /**
         * The different places where an item model can be displayed.
         */
        enum class Position {
            THIRDPERSON_RIGHTHAND,
            THIRDPERSON_LEFTHAND,
            FIRSTPERSON_RIGHTHAND,
            FIRSTPERSON_LEFTHAND,
            HEAD,
            GUI,
            GROUND,
            FIXED
        }
        
    }
    
    // TODO: verify whether this is the correct overwriting behavior for things like textures, display, guiLight, etc.
    /**
     * Creates a flattened copy of this model using the given [context] to resolve parent models.
     */
    fun flattened(context: ModelContent): Model {
        if (parent == null)
            return this
        
        val textures = HashMap<String, String>()
        var elements: List<Element>? = null
        var ambientOcclusion = true
        var guiLight = GuiLight.SIDE
        val display = HashMap<Display.Position, Display>()
        val overrides = ArrayList<Override>()
        
        val hierarchy = LinkedList<Model>()
        var parent: Model? = this
        while (parent != null) {
            hierarchy.addFirst(parent)
            parent = parent.parent?.let(context::get)
        }
        
        while (hierarchy.isNotEmpty()) {
            val model = hierarchy.removeFirst()
            
            textures.putAll(model.textures)
            if (model.elements != null) elements = model.elements
            if (model.ambientOcclusion != null) ambientOcclusion = model.ambientOcclusion
            if (model.guiLight != null) guiLight = model.guiLight
            display.putAll(model.display)
            overrides.addAll(model.overrides)
        }
        
        return Model(
            textures = textures,
            elements = elements,
            ambientOcclusion = ambientOcclusion,
            guiLight = guiLight,
            display = display,
            overrides = overrides
        )
    }
    
    /**
     * Gets the bounds of this model's elements. Uses [context] to resolve parent models.
     */
    fun getBounds(context: ModelContent?): AABBd {
        var elements: List<Element>? = null
        var parent: Model? = this
        while (elements == null && parent != null) {
            elements = parent.elements
            parent = parent.parent?.let { context?.get(it) }
        }
        
        if (elements == null)
            return AABBd()
        
        val min = Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        val max = Vector3d(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE)
        for (element in elements) {
            min.min(element.from)
            max.max(element.to)
        }
        
        return AABBd(min, max)
    }
    
}