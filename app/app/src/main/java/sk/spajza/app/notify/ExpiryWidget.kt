package sk.spajza.app.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import sk.spajza.app.App
import sk.spajza.app.MainActivity
import sk.spajza.app.R
import sk.spajza.app.data.Calc

/** Widget na ploche: co sa kazi v najblizsich dnoch. */
class ExpiryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            try {
                manager.updateAppWidget(id, build(context))
            } catch (e: Exception) {
                // widget sa aktualizuje pri dalsej zmene
            }
        }
    }

    companion object {
        fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_expiry)
            val db = (context.applicationContext as App).store.db.value
            val names = HashMap<Long, String>()
            for (i in db.items) names[i.id] = i.name
            val today = Calc.today()
            val soon = db.batches
                .filter { it.expiry != null && it.qty > 0 && names.containsKey(it.itemId) && (it.expiry - today) <= 7 }
                .sortedBy { it.expiry }
            val text = if (soon.isEmpty()) {
                "Nič sa v najbližších dňoch nekazí."
            } else {
                val lines = soon.take(7).map { b ->
                    val d = (b.expiry ?: today) - today
                    val dot = if (d < 0) "🔴" else if (d <= 3) "🟠" else "🟡"
                    dot + " " + names[b.itemId] + " – " + Calc.expiryText(b.expiry)
                }
                val more = soon.size - lines.size
                lines.joinToString("\n") + (if (more > 0) "\n… a ďalších $more" else "")
            }
            views.setTextViewText(R.id.widget_body, text)
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            return views
        }

        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, ExpiryWidget::class.java))
                for (id in ids) manager.updateAppWidget(id, build(context))
            } catch (e: Exception) {
                // nic
            }
        }
    }
}
