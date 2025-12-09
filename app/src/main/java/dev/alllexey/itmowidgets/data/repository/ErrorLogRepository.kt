package dev.alllexey.itmowidgets.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime

class ErrorLogRepository(val context: Context) {

    companion object {
        val Context.errorLogDataStore by preferencesDataStore(name = "error_log_data")

        private val KEY = stringPreferencesKey("error_log_json")
    }

    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
                override fun write(out: JsonWriter, value: LocalDateTime?) {
                    out.value(value?.toString())
                }

                override fun read(input: JsonReader): LocalDateTime? {
                    return LocalDateTime.parse(input.nextString())
                }
            })
            .create()
    }

    val data: Flow<ErrorLogWrapper> =
        context.errorLogDataStore.data.map { prefs ->
            prefs[KEY]?.let { gson.fromJson(it, ErrorLogWrapper::class.java) }
                ?: ErrorLogWrapper(mutableListOf())
        }

    suspend fun addLogEntry(entry: ErrorLogEntry) {
        context.errorLogDataStore.edit { prefs ->
            val currentJson = prefs[KEY]
            val wrapper = if (currentJson != null) {
                gson.fromJson(currentJson, ErrorLogWrapper::class.java)
            } else {
                ErrorLogWrapper(mutableListOf())
            }

            wrapper.logs.add(0, entry)

            while (wrapper.logs.size > 20) {
                wrapper.logs.removeAt(wrapper.logs.lastIndex)
            }

            prefs[KEY] = gson.toJson(wrapper)
        }
    }

    fun logThrowable(throwable: Throwable, module: String) {
        throwable.printStackTrace()
        val entry = ErrorLogEntry(Log.getStackTraceString(throwable), LocalDateTime.now(), module)
        runBlocking { addLogEntry(entry) }
    }

    suspend fun checkPendingCrashes() {
        val file = File(context.filesDir, "pending_crash.log")
        if (file.exists()) {
            try {
                val fullContent = file.readText()
                val reports = fullContent.split("|||CRASH_END|||")
                for (report in reports) {
                    if (report.isBlank()) continue
                    val parts = report.split("|SPLIT|")
                    if (parts.size >= 2) {
                        val timeStr = parts[0]
                        val stack = parts[1]

                        val entry = ErrorLogEntry(
                            stacktrace = stack,
                            time = try {
                                LocalDateTime.parse(timeStr)
                            } catch (_: Exception) {
                                LocalDateTime.now()
                            },
                            module = "Crash"
                        )
                        addLogEntry(entry)
                    }
                }
                file.delete()
            } catch (e: Exception) {
                Log.e("ErrorLogRepo", "Failed to parse pending crash log", e)
            }
        }
    }
}

data class ErrorLogEntry(val stacktrace: String, val time: LocalDateTime, val module: String)

data class ErrorLogWrapper(val logs: MutableList<ErrorLogEntry>)