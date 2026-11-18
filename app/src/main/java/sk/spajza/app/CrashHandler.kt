package sk.spajza.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Zapise pad aplikacie do suboru, aby sa dal pri dalsom spusteni zobrazit a skopirovat. */
object CrashHandler {
    private fun file(context: Context) = File(context.filesDir, "crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText(
                    "Vlákno: " + thread.name + "\nZariadenie: " + android.os.Build.MANUFACTURER + " " +
                        android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE + "\n\n" + sw.toString()
                )
            } catch (ignored: Exception) {
            }
            if (previous != null) previous.uncaughtException(thread, e)
        }
    }

    fun read(context: Context): String? {
        val f = file(context)
        return try {
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (ignored: Exception) {
        }
    }
}
