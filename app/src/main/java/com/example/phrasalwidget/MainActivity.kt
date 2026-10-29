package com.example.phrasalwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private var all: List<String> = emptyList()
    private var query = ""
    private var current = VERBS_FILE          // which list is shown
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 16)
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        title = TextView(this).apply { textSize = 18f; text = "Loading..." }

        val bw = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        // Row 1: choose which list to view
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabVerbs = Button(this).apply { text = "Phrasal verbs"; isAllCaps = false }
        val tabWords = Button(this).apply { text = "Words"; isAllCaps = false }
        tabs.addView(tabVerbs, bw)
        tabs.addView(tabWords, bw)
        tabVerbs.setOnClickListener { showList(VERBS_FILE) }
        tabWords.setOnClickListener { showList(WORDS_FILE) }

        // Row 2: download the latest lists from GitHub
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val update = Button(this).apply { text = "Update"; isAllCaps = false }
        actions.addView(update, bw)
        update.setOnClickListener { updateFromGitHub() }

        val search = EditText(this).apply { hint = "Search"; setSingleLine() }
        val list = ListView(this)
        list.isFastScrollEnabled = true
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter

        root.addView(title)
        root.addView(tabs)
        root.addView(actions)
        root.addView(search)
        root.addView(list)
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        all = readLocal()
        applyFilter()
    }

    private fun showList(file: String) {
        current = file
        all = readLocal()
        applyFilter()
    }

    // Downloads both lists from GitHub, then updates the screen and the widgets
    private fun updateFromGitHub() {
        Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
        Thread {
            val verbsOk = try { download(VERBS_FILE, PhrasalWidget.VERBS_URL) } catch (e: Exception) { false }
            val wordsOk = try { download(WORDS_FILE, WordsWidget.WORDS_URL) } catch (e: Exception) { false }
            val fresh = readLocal()
            runOnUiThread {
                all = fresh
                applyFilter()
                refreshWidgets()
                val msg = when {
                    verbsOk && wordsOk -> "Updated from GitHub"
                    !verbsOk && !wordsOk -> "Both downloads failed"
                    !verbsOk -> "phrasal_verbs.txt failed"
                    else -> "words.txt failed"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun applyFilter() {
        val shown = all.filter { it.contains(query, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(shown.map { format(it) })
        val label = if (current == VERBS_FILE) "phrasal verbs" else "words"
        title.text = "${shown.size} of ${all.size} $label"
    }

    private fun format(line: String): String {
        val parts = line.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val verb = parts.firstOrNull() ?: line
        val rest = parts.drop(1).joinToString("\n") { "• $it" }
        return if (rest.isEmpty()) verb else "$verb\n$rest"
    }

    private fun readLocal(): List<String> {
        return try {
            val cache = File(filesDir, current)
            val text = if (cache.exists()) cache.readText(Charsets.UTF_8)
            else assets.open(current).bufferedReader(Charsets.UTF_8).readText()
            text.lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun refreshWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        for (cls in listOf(PhrasalWidget::class.java, WordsWidget::class.java)) {
            val ids = manager.getAppWidgetIds(ComponentName(this, cls))
            if (ids.isEmpty()) continue
            sendBroadcast(Intent(this, cls).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }
    }

    // Returns true if the file was downloaded and saved
    private fun download(name: String, url: String): Boolean {
        val conn = URL("$url?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.useCaches = false
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                if (text.isNotBlank()) {
                    File(filesDir, name).writeText(text, Charsets.UTF_8)
                    return true
                }
            }
            return false
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val VERBS_FILE = "phrasal_verbs.txt"
        private const val WORDS_FILE = "words.txt"
    }
}
