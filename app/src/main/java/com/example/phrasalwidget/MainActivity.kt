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

        // Row 2: load your own file for the list being shown, or Reset = download from GitHub
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val choose = Button(this).apply { text = "Choose file"; isAllCaps = false }
        val reset = Button(this).apply { text = "Reset"; isAllCaps = false }
        actions.addView(choose, bw)
        actions.addView(reset, bw)
        choose.setOnClickListener { pickFile() }
        reset.setOnClickListener { resetToOnline() }

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
    private fun refreshOnline() {
        Thread {
            val verbsOk = try { download(VERBS_FILE, PhrasalWidget.VERBS_URL) } catch (e: Exception) { false }
            val wordsOk = try { download(WORDS_FILE, WordsWidget.WORDS_URL) } catch (e: Exception) { false }
            val fresh = readLocal()
            runOnUiThread {
                all = fresh
                applyFilter()
                refreshWidgets()
                val msg = if (verbsOk && wordsOk) "Updated from GitHub" else "Download failed"
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

    // Opens the system file picker; the chosen file replaces the list currently shown
    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).readText()
            if (text.isBlank()) {
                Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show()
                return
            }
            File(filesDir, current).writeText(text, Charsets.UTF_8)
            PhrasalWidget.setCustom(this, current, true)
            Toast.makeText(this, "$current loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show()
            return
        }
        all = readLocal()
        applyFilter()
        refreshWidgets()
    }

    // Reset: forget chosen files and download the latest lists from GitHub
    private fun resetToOnline() {
        for (name in listOf(VERBS_FILE, WORDS_FILE)) {
            PhrasalWidget.setCustom(this, name, false)
            File(filesDir, name).delete()
        }
        Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
        all = readLocal()
        applyFilter()
        refreshOnline()
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

    // Returns true if the file is up to date (downloaded, or you chose your own)
    private fun download(name: String, url: String): Boolean {
        if (PhrasalWidget.isCustom(this, name)) return true
        val conn = URL(url).openConnection() as HttpURLConnection
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
        private const val REQ_PICK = 1
        private const val VERBS_FILE = "phrasal_verbs.txt"
        private const val WORDS_FILE = "words.txt"
    }
}
