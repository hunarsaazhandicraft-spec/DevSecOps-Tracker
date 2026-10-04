package com.devsecops.tracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class TodayWidgetSmall : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        WidgetData.updateAll(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        try {
            ids.forEach { manager.updateAppWidget(it, WidgetData.buildSmall(context)) }
        } catch (e: Exception) {
            // best-effort
        }
    }

    companion object {
        /** Backward compatible entry point used by older callers. */
        fun save(context: Context, due: Int, streak: Int) {
            WidgetData.save(context, due, streak)
        }
    }
}
