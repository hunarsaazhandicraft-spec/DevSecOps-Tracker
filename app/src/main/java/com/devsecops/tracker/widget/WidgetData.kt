package com.devsecops.tracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.devsecops.tracker.MainActivity
import com.devsecops.tracker.R

// Shared cache + view builders for the three home-screen widgets.
// Everything comes from SharedPreferences written by the app; no network.
object WidgetData {
    private const val PREFS = "widget_prefs"
    private const val MINT = 0xFF4DFFA6.toInt()
    private const val WHITE = 0xFFEDEFF2.toInt()

    const val OPEN_REVISE = "revise"
    const val OPEN_STUDY = "study"
    const val OPEN_TODAY = "today"

    private class Snapshot(val due: Int, val streak: Int, val next: String, val updatedAt: Long)

    private fun read(context: Context): Snapshot {
        return try {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            Snapshot(
                due = p.getInt("due", 0).coerceAtLeast(0),
                streak = p.getInt("streak", 0).coerceAtLeast(0),
                next = p.getString("next", "") ?: "",
                updatedAt = p.getLong("updatedAt", 0L)
            )
        } catch (e: Exception) {
            Snapshot(0, 0, "", 0L)
        }
    }

    /** Keeps the previously saved "next step" text. */
    fun save(context: Context, due: Int, streak: Int) {
        val prev = read(context).next
        save(context, due, streak, prev)
    }

    fun save(context: Context, due: Int, streak: Int, next: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("due", due)
                .putInt("streak", streak)
                .putString("next", next)
                .putLong("updatedAt", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // ignore: widget is best-effort
        }
        updateAll(context)
    }

    fun updateAll(context: Context) {
        val app = context.applicationContext
        push(app, TodayWidgetSmall::class.java)
        push(app, TodayWidgetProvider::class.java)
        push(app, TodayWidgetStrip::class.java)
    }

    private fun push(context: Context, cls: Class<out AppWidgetProvider>) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            ids.forEach { id ->
                val views = when (cls) {
                    TodayWidgetSmall::class.java -> buildSmall(context)
                    TodayWidgetStrip::class.java -> buildStrip(context)
                    else -> buildMedium(context)
                }
                manager.updateAppWidget(id, views)
            }
        } catch (e: Exception) {
            // never crash the caller
        }
    }

    private fun pending(context: Context, target: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.devsecops.tracker.widget.OPEN_" + target.uppercase()
            putExtra("open", target)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun agoText(updatedAt: Long): String {
        if (updatedAt <= 0L) return "Open app to sync"
        val diff = System.currentTimeMillis() - updatedAt
        if (diff < 0L) return "updated just now"
        val min = diff / 60_000L
        return when {
            min < 1L -> "updated just now"
            min < 60L -> "updated ${min}m ago"
            min < 60L * 24L -> "updated ${min / 60L}h ago"
            else -> "updated ${min / (60L * 24L)}d ago"
        }
    }

    fun buildSmall(context: Context): RemoteViews {
        val s = read(context)
        val v = RemoteViews(context.packageName, R.layout.widget_small)
        v.setTextViewText(R.id.w_due, s.due.toString())
        v.setTextColor(R.id.w_due, if (s.due == 0) MINT else WHITE)
        v.setTextViewText(
            R.id.w_due_label,
            if (s.due == 0) context.getString(R.string.widget_caught_up)
            else context.getString(R.string.widget_due)
        )
        v.setTextViewText(R.id.w_streak, "${s.streak}-day streak")
        v.setOnClickPendingIntent(R.id.w_root, pending(context, OPEN_REVISE, 11))
        return v
    }

    fun buildMedium(context: Context): RemoteViews {
        val s = read(context)
        val v = RemoteViews(context.packageName, R.layout.widget_today)
        v.setTextViewText(R.id.w_due, s.due.toString())
        v.setTextViewText(
            R.id.w_due_label,
            if (s.due == 0) context.getString(R.string.widget_caught_up)
            else if (s.due == 1) "card due" else "cards due"
        )
        v.setTextViewText(R.id.w_streak, "${s.streak}-day streak")
        v.setTextViewText(R.id.w_updated, agoText(s.updatedAt))
        val next = s.next.trim()
        v.setTextViewText(
            R.id.w_next,
            if (next.isEmpty()) context.getString(R.string.widget_next_default) else "Next: $next"
        )
        v.setOnClickPendingIntent(R.id.w_root, pending(context, OPEN_TODAY, 21))
        v.setOnClickPendingIntent(R.id.w_btn_revise, pending(context, OPEN_REVISE, 22))
        v.setOnClickPendingIntent(R.id.w_btn_study, pending(context, OPEN_STUDY, 23))
        return v
    }

    fun buildStrip(context: Context): RemoteViews {
        val s = read(context)
        val v = RemoteViews(context.packageName, R.layout.widget_strip)
        val dueText = if (s.due == 0) context.getString(R.string.widget_caught_up) else "${s.due} due"
        v.setTextViewText(R.id.w_line, "$dueText · ${s.streak}-day streak")
        v.setTextViewText(R.id.w_continue, context.getString(R.string.widget_continue))
        v.setOnClickPendingIntent(R.id.w_root, pending(context, OPEN_TODAY, 31))
        return v
    }
}
