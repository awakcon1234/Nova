@file:Suppress("UNCHECKED_CAST")

package xyz.xenondevs.nova.world.item.recipe

import net.kyori.adventure.key.Key
import org.bukkit.inventory.BlastingRecipe
import org.bukkit.inventory.CampfireRecipe
import org.bukkit.inventory.FurnaceRecipe
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.SmokingRecipe
import org.bukkit.inventory.StonecuttingRecipe
import xyz.xenondevs.nova.initialize.InternalInit
import xyz.xenondevs.nova.initialize.InternalInitStage
import xyz.xenondevs.nova.registry.NovaRegistries
import xyz.xenondevs.nova.resources.ResourceGeneration
import xyz.xenondevs.nova.serialization.json.serializer.BlastingRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.CampfireRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.FurnaceRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.RecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.ShapedRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.ShapelessRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.SmithingTransformRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.SmokingRecipeDeserializer
import xyz.xenondevs.nova.serialization.json.serializer.StonecutterRecipeDeserializer
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.BlastingRecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.CampfireRecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.SmeltingRecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.SmithingTransformRecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.SmokingRecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.StonecutterRecipeGroup
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.TableRecipeGroup
import xyz.xenondevs.nova.util.set
import kotlin.reflect.KClass
import kotlin.reflect.full.superclasses

class RecipeType<T : Any> internal constructor(
    val id: Key,
    val recipeClass: KClass<T>,
    val group: RecipeGroup<in T>,
    val deserializer: RecipeDeserializer<T>?
) {
    
    val dirName get() = id.namespace() + "/" + id.value()
    
    companion object {
        
        fun <T : Any> of(recipe: T): RecipeType<out T>? {
            val clazz = recipe::class
            return NovaRegistries.RECIPE_TYPE.firstOrNull { clazz == it.recipeClass || clazz.superclasses.contains(it.recipeClass) } as RecipeType<out T>?
        }
        
    }
    
}

@InternalInit(stage = InternalInitStage.POST_WORLD, dependsOn = [ResourceGeneration.PreWorld::class])
object VanillaRecipeTypes {
    
    val SHAPED = register("shaped", ShapedRecipe::class, TableRecipeGroup, ShapedRecipeDeserializer)
    val SHAPELESS = register("shapeless", ShapelessRecipe::class, TableRecipeGroup, ShapelessRecipeDeserializer)
    val FURNACE = register("furnace", FurnaceRecipe::class, SmeltingRecipeGroup, FurnaceRecipeDeserializer)
    val BLAST_FURNACE = register("blast_furnace", BlastingRecipe::class, BlastingRecipeGroup, BlastingRecipeDeserializer)
    val SMOKER = register("smoker", SmokingRecipe::class, SmokingRecipeGroup, SmokingRecipeDeserializer)
    val CAMPFIRE = register("campfire", CampfireRecipe::class, CampfireRecipeGroup, CampfireRecipeDeserializer)
    val STONECUTTER = register("stonecutter", StonecuttingRecipe::class, StonecutterRecipeGroup, StonecutterRecipeDeserializer)
    val SMITING_TRANSFORM = register("smithing_transform", SmithingTransformRecipe::class, SmithingTransformRecipeGroup, SmithingTransformRecipeDeserializer)
    
    private fun <T : Any> register(name: String, recipeClass: KClass<T>, group: RecipeGroup<in T>, deserializer: RecipeDeserializer<T>?): RecipeType<T> {
        val id = Key.key(name)
        val recipeType = RecipeType(id, recipeClass, group, deserializer)
        NovaRegistries.RECIPE_TYPE[id] = recipeType
        return recipeType
    }
    
}