package sk.spajza.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import sk.spajza.app.App
import sk.spajza.app.MainActivity
import sk.spajza.app.R
import sk.spajza.app.data.Batch
import sk.spajza.app.data.Calc
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object Notify {
    const val CHANNEL = "expiry"
    private const val WORK = "daily-check"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "Expirácia a nízke zásoby", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Upozornenia na blížiacu sa expiráciu a docházajúce potraviny"
        nm.createNotificationChannel(ch)
    }

    fun canPost(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Naplanuje denne ranne vyhodnotenie (WorkManager prezije restart telefonu). */
    fun schedule(context: Context, hour: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
        val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.coerceAtLeast(60_000L), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // povolenie bolo medzitym odobrate
        }
    }

    fun test(context: Context) {
        createChannel(context)
        show(context, 99, "Špajza", "Upozornenia fungujú. Takto budete informovaný o expirácii.")
    }

    /** Vyhodnoti expiracie a nizke zasoby a odosle upozornenia. Kazde sa posle len raz za stupen. */
    fun check(context: Context) {
        val app = context.applicationContext as App
        val store = app.store
        val db = store.db.value
        val s = db.settings
        if (!s.notificationsEnabled) return
        createChannel(context)

        val names = HashMap<Long, String>()
        for (i in db.items) names[i.id] = i.name

        val newStage = HashMap<Long, Int>()
        val expired = ArrayList<String>()
        val d3 = ArrayList<String>()
        val d7 = ArrayList<String>()
        val d30 = ArrayList<String>()
        for (b: Batch in db.batches) {
            val ex = b.expiry ?: continue
            if (b.qty <= 0) continue
            val d = ex - Calc.today()
            val stage = when {
                d < 0 -> 4
                d <= 3 -> 3
                d <= 7 -> 2
                d <= 30 -> 1
                else -> 0
            }
            if (stage > b.notified) {
                newStage[b.id] = stage
                val nm = names[b.itemId] ?: continue
                when (stage) {
                    4 -> expired.add(nm)
                    3 -> if (s.notify3) d3.add(nm)
                    2 -> if (s.notify7) d7.add(nm)
                    1 -> if (s.notify30) d30.add(nm)
                }
            }
        }
        val low = ArrayList<String>()
        val lowIds = HashSet<Long>()
        if (s.notifyLow) {
            // Rovnake produkty (napr. mlieko z Tesca aj z Lidla) sa hodnotia spolu ako jedna skupina.
            for (g in sk.spajza.app.data.Pairing.lowGroups(db)) {
                if (g.members.any { !it.item.lowNotified }) {
                    low.add(if (g.grouped) g.name else g.primary.item.name)
                    lowIds.addAll(g.ids)
                }
            }
        }

        if (expired.isNotEmpty()) {
            show(context, 1, "Po expirácii (" + expired.distinct().size + ")", join(expired))
        }
        if (d3.isNotEmpty()) {
            show(context, 2, "Expiruje do 3 dní (" + d3.distinct().size + ")", join(d3))
        }
        if (d7.isNotEmpty()) {
            show(context, 3, "Expiruje do 7 dní (" + d7.distinct().size + ")", join(d7))
        }
        if (d30.isNotEmpty()) {
            show(context, 4, "Expiruje do 30 dní (" + d30.distinct().size + ")", join(d30))
        }
        if (s.notifyDaily) {
            val today = Calc.today()
            val dayNames = ArrayList<String>()
            for (bt in db.batches) {
                val ex = bt.expiry ?: continue
                if (bt.qty <= 0) continue
                val d = ex - today
                if (d in 0..1) {
                    val nm = names[bt.itemId] ?: continue
                    dayNames.add(nm + (if (d == 0L) " (dnes)" else " (zajtra)"))
                }
            }
            if (dayNames.isNotEmpty()) show(context, 6, "Dnes spotrebujte", join(dayNames))
        }
        if (low.isNotEmpty()) {
            show(context, 5, "Dochádzajú zásoby (" + low.size + ")", join(low))
        }

        if (newStage.isNotEmpty() || lowIds.isNotEmpty()) {
            store.update { d ->
                d.copy(
                    batches = d.batches.map { b ->
                        val st = newStage[b.id]
                        if (st != null) b.copy(notified = st) else b
                    },
                    items = d.items.map { i -> if (i.id in lowIds) i.copy(lowNotified = true) else i }
                )
            }
        }
    }

    private fun join(list: List<String>): String {
        val u = list.distinct()
        val head = u.take(8).joinToString(", ")
        return if (u.size > 8) head + " a ďalšie (" + (u.size - 8) + ")" else head
    }
}

class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            Notify.check(applicationContext)
            ExpiryWidget.refresh(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }
}
