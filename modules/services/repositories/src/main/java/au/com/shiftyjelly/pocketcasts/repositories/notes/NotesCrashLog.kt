package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.content.Context
import android.util.Log
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Keeps the last crash's stack trace on the phone, so it can be copied from 聽記設定 without a computer
 * or adb.
 */
object NotesCrashLog {
    private const val FILE_NAME = "notes/last_crash.txt"
    private const val MAX_LENGTH = 20_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = File(appContext.filesDir, FILE_NAME)
                file.parentFile?.mkdirs()
                val text = buildString {
                    append(DateFormat.getDateTimeInstance().format(Date()))
                    append(" · thread ").append(thread.name).append('\n')
                    append(Log.getStackTraceString(throwable))
                }
                file.writeText(text.take(MAX_LENGTH))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return if (file.exists()) runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
