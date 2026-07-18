package dev.alllexey.itmowidgets.core.util

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.OffsetDateTime

class OffsetDateTimeAdapter : TypeAdapter<OffsetDateTime>() {

    override fun write(out: JsonWriter, value: OffsetDateTime?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.value(value.toString())
    }

    override fun read(reader: JsonReader): OffsetDateTime? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return OffsetDateTime.parse(reader.nextString())
    }
}
