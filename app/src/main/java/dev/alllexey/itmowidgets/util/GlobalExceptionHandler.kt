package dev.alllexey.itmowidgets.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import kotlin.system.exitProcess

class GlobalExceptionHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            saveCrashReportSync(throwable)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(2)
        }
    }

    private fun saveCrashReportSync(throwable: Throwable) {
        val stackTrace = Log.getStackTraceString(throwable)
        val timestamp = LocalDateTime.now().toString()
        val reportContent = "$timestamp|SPLIT|$stackTrace"

        val file = File(context.filesDir, "pending_crash.log")

        val writer = PrintWriter(FileWriter(file, true))
        writer.println(reportContent)
        writer.flush()
        writer.close()
    }
}