package com.example.phrasalwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.Color

class MainActivity : Activity() {

    private var all: List<String> = emptyList()
    private var query = ""
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
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pickVerbs = Button(this).apply { text = "Verbs file"; isAllCaps = false }
        val pickWords = Button(this).apply { text = "Words file"; isAllCaps = false }
        val reset = Button(this).apply { text = "Reset"; isAllCaps = false }
        val bw = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        buttons.addView(pickVerbs, bw)
        buttons.addView(pickWords, bw)
        buttons.addView(reset, bw)
        pickVerbs.setOnClickListener { pickFile(REQ_VERBS) }
        pickWords.setOnClickListener { pickFile(REQ_WORDS) }
        reset.setOnClickListener { resetToOnline() }

        val search = EditText(this).apply { hint = "Search"; setSingleLine() }
        val list = ListView(this)
        list.isFastScrollEnabled = true
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter

        root.addView(title)
        root.addView(buttons)
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

        Thread {
            try { download() } catch (e: Exception) { }
            val fresh = readLocal()
            runOnUiThread { all = fresh; applyFilter() }
        }.start()
    }

    private fun applyFilter() {
        val shown = all.filter { it.contains(query, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(shown.map { format(it) })
        title.text = "${shown.size} of ${all.size} phrasal verbs"
    }

    private fun format(line: String): String {
        val parts = line.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val verb = parts.firstOrNull() ?: line
        val rest = parts.drop(1).joinToString("\n") { "• $it" }
        return if (rest.isEmpty()) verb else "$verb\n$rest"
    }

    private fun readLocal(): List<String> {
        return try {
            val cache = File(filesDir, "phrasal_verbs.txt")
            val text = if (cache.exists()) cache.readText(Charsets.UTF_8)
            else assets.open("phrasal_verbs.txt").bufferedReader(Charsets.UTF_8).readText()
            text.lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun pickFile(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val name = if (requestCode == REQ_VERBS) "phrasal_verbs.txt" else "words.txt"
        try {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).readText()
            if (text.isBlank()) {
                Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show()
                return
            }
            File(filesDir, name).writeText(text, Charsets.UTF_8)
            PhrasalWidget.setCustom(this, name, true)
            Toast.makeText(this, "$name loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show()
            return
        }
        all = readLocal()
        applyFilter()
        refreshWidgets()
    }

    private fun resetToOnline() {
        for (name in listOf("phrasal_verbs.txt", "words.txt")) {
            PhrasalWidget.setCustom(this, name, false)
            File(filesDir, name).delete()
        }
        Toast.makeText(this, "Using online files", Toast.LENGTH_SHORT).show()
        all = readLocal()
        applyFilter()
        Thread {
            try { download() } catch (e: Exception) { }
            val fresh = readLocal()
            runOnUiThread { all = fresh; applyFilter() }
        }.start()
        refreshWidgets()
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

    private fun download() {
        if (PhrasalWidget.isCustom(this, "phrasal_verbs.txt")) return
        val conn = URL(PhrasalWidget.VERBS_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                if (text.isNotBlank()) File(filesDir, "phrasal_verbs.txt").writeText(text, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val REQ_VERBS = 1
        private const val REQ_WORDS = 2
    }
}
