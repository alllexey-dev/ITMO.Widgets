package dev.alllexey.itmowidgets.util

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlin.collections.iterator

class RuntimeTypeAdapterFactory<T> private constructor(
    private val baseType: Class<*>,
    private val typeFieldName: String,
    private val maintainType: Boolean
) : TypeAdapterFactory {
    private val labelToSubtype: MutableMap<String, Class<*>> = LinkedHashMap()
    private val subtypeToLabel: MutableMap<Class<*>, String> = LinkedHashMap()

    companion object {
        fun <T> of(baseType: Class<T>, typeFieldName: String = "type", maintainType: Boolean = false): RuntimeTypeAdapterFactory<T> {
            return RuntimeTypeAdapterFactory(baseType, typeFieldName, maintainType)
        }
    }

    fun registerSubtype(subtype: Class<out T>, label: String): RuntimeTypeAdapterFactory<T> {
        if (subtypeToLabel.containsKey(subtype) || labelToSubtype.containsKey(label)) {
            throw IllegalArgumentException("types and labels must be unique")
        }
        labelToSubtype[label] = subtype
        subtypeToLabel[subtype] = label
        return this
    }

    override fun <R> create(gson: Gson, type: TypeToken<R>): TypeAdapter<R>? {
        if (type.rawType != baseType) {
            return null
        }

        val labelToDelegate: MutableMap<String, TypeAdapter<*>> = LinkedHashMap()
        val subtypeToDelegate: MutableMap<Class<*>, TypeAdapter<*>> = LinkedHashMap()
        for ((key, value) in labelToSubtype) {
            val delegate = gson.getDelegateAdapter(this, TypeToken.get(value))
            labelToDelegate[key] = delegate
            subtypeToDelegate[value] = delegate
        }

        return object : TypeAdapter<R>() {
            override fun read(input: JsonReader): R {
                val jsonElement = gson.fromJson<JsonElement>(input, JsonElement::class.java)
                val labelJsonElement: JsonElement?
                labelJsonElement = if (maintainType) {
                    jsonElement.asJsonObject.get(typeFieldName)
                } else {
                    jsonElement.asJsonObject.remove(typeFieldName)
                }
                if (labelJsonElement == null) {
                    throw JsonParseException("cannot deserialize ${baseType} because it does not define a field named ${typeFieldName}")
                }
                val label = labelJsonElement.asString
                val delegate = labelToDelegate[label]
                    ?: throw JsonParseException("cannot deserialize ${baseType} subtype named ${label}; did you forget to register a subtype?")
                return delegate.fromJsonTree(jsonElement) as R
            }

            override fun write(out: JsonWriter, value: R) {
                val srcType = value!!::class.java
                val label = subtypeToLabel[srcType]
                    ?: throw JsonParseException("cannot serialize ${srcType.name}; did you forget to register a subtype?")
                val delegate = subtypeToDelegate[srcType] as TypeAdapter<R>
                var jsonObject = delegate.toJsonTree(value).asJsonObject
                if (maintainType) {
                    gson.toJson(jsonObject, out)
                    return
                }
                val clone = JsonObject()
                if (jsonObject.has(typeFieldName)) {
                    throw JsonParseException("cannot serialize ${srcType.name} because it already defines a field named ${typeFieldName}")
                }
                clone.add(typeFieldName, JsonPrimitive(label))
                for ((key, value1) in jsonObject.entrySet()) {
                    clone.add(key, value1)
                }
                gson.toJson(clone, out)
            }
        }.nullSafe() as TypeAdapter<R>
    }
}