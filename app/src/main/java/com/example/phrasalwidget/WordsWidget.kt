package com.example.phrasalwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WordsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread {
            try { refreshCache(context) } catch (e: Exception) { }
            for (id in ids) manager.updateAppWidget(id, buildViews(context))
            pending.finish()
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WORDS) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordsWidget::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun refreshCache(context: Context) {
        if (PhrasalWidget.isCustom(context, "words.txt")) return
        val cache = File(context.filesDir, "words.txt")
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 5 * 60 * 1000) return
        val conn = URL(WORDS_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                if (text.isNotBlank()) cache.writeText(text, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.phrasal_widget)

        val parts = loadRandomLine(context)
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val word = parts.firstOrNull() ?: "No words found"
        val rest = parts.drop(1).joinToString("\n\n") { "• $it" }

        views.setTextViewText(R.id.verb, word)
        views.setTextViewText(R.id.examples, rest)

        val intent = Intent(context, WordsWidget::class.java).apply { action = ACTION_REFRESH_WORDS }
        val pending = PendingIntent.getBroadcast(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.root, pending)
        return views
    }

    private fun loadRandomLine(context: Context): String {
        return try {
            val cache = File(context.filesDir, "words.txt")
            val text = if (cache.exists()) {
                cache.readText(Charsets.UTF_8)
            } else {
                context.assets.open("words.txt").bufferedReader(Charsets.UTF_8).readText()
            }
            text.lines().filter { it.isNotBlank() }.random()
        } catch (e: Exception) {
            "Error; ${e.message ?: "could not read words.txt"}"
        }
    }

    companion object {
        const val ACTION_REFRESH_WORDS = "com.example.phrasalwidget.REFRESH_WORDS"
        val WORDS_URL: String = PhrasalWidget.VERBS_URL.replace("phrasal_verbs.txt", "words.txt")
    }
}
