package com.example.phrasalwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.io.File

class PhrasalWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, buildViews(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PhrasalWidget::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.phrasal_widget)

        val parts = loadRandomLine(context)
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val verb = parts.firstOrNull() ?: "No verbs found"
        val rest = parts.drop(1).joinToString("\n\n") { "• $it" }

        views.setTextViewText(R.id.verb, verb)
        views.setTextViewText(R.id.examples, rest)

        val intent = Intent(context, PhrasalWidget::class.java).apply { action = ACTION_REFRESH }
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.root, pending)
        return views
    }

    private fun loadRandomLine(context: Context): String {
        return try {
            val cache = File(context.filesDir, "phrasal_verbs.txt")
            val text = if (cache.exists()) {
                cache.readText(Charsets.UTF_8)
            } else {
                context.assets.open("phrasal_verbs.txt").bufferedReader(Charsets.UTF_8).readText()
            }
            text.lines().filter { it.isNotBlank() }.random()
        } catch (e: Exception) {
            "Error; ${e.message ?: "could not read phrasal_verbs.txt"}"
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.phrasalwidget.REFRESH"
        const val VERBS_URL =
            "https://raw.githubusercontent.com/beridzeg45/DailyEnglish/main/DailyEnglish/app/src/main/assets/phrasal_verbs.txt"
    }
}
