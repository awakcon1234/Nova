package xyz.xenondevs.nova.world.item

import net.kyori.adventure.key.Key
import xyz.xenondevs.nova.initialize.InternalInit
import xyz.xenondevs.nova.initialize.InternalInitStage
import xyz.xenondevs.nova.world.item.behavior.UnknownItemFilterBehavior

@InternalInit(stage = InternalInitStage.PRE_WORLD)
internal object DefaultItems {
    
    val UNKNOWN_ITEM_FILTER = item("unknown_item_filter") {
        behaviors(UnknownItemFilterBehavior)
        modelDefinition { model = buildModel { createLayeredModel("block/unknown") } }
    }
    
    private fun item(name: String, run: NovaItemBuilder.() -> Unit): NovaItem {
        val builder = NovaItemBuilder(Key.key("nova", name))
        builder.run()
        return builder.register()
    }
    
}