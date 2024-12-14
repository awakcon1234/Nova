package xyz.xenondevs.nova.serialization.json.serializer

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.kyori.adventure.key.Key
import xyz.xenondevs.commons.gson.getInt
import xyz.xenondevs.commons.gson.getString
import xyz.xenondevs.nova.resources.builder.task.font.FontChar
import java.lang.reflect.Type

internal object FontCharSerialization : JsonSerializer<FontChar>, JsonDeserializer<FontChar> {
    
    override fun serialize(src: FontChar, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonObject().apply {
            addProperty("font", src.font.toString())
            addProperty("char", src.codePoint)
        }
    }
    
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FontChar {
        json as JsonObject
        return FontChar(Key.key(json.getString("font")), json.getInt("char"))
    }
    
}