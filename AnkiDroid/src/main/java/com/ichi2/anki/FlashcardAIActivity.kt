package com.ichi2.anki

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class FlashcardRow(var q: String, var a: String, var imgUrl: String = "PENDIENTE")

class FlashcardAIActivity : AppCompatActivity() {
    private val API_KEYS_GEMINI = listOf("AIzaSyBH-4FcDt9vpEJlDwxMeCW1QigrtF3Zt2k","AIzaSyDnl11aCnK7qOE-K9viBBJim0QD_UrF5uM")
    private val GOOGLE_API_KEY = "AIzaSyBuzM-cCtTJ9cplHi7OTbEaSBhrAGx7dBA"
    private val GOOGLE_CX = "96728b66d8b3841df"
    private val CLOUD_NAME = "dy6gi3ft4"
    private val CLOUDINARY_UPLOAD_PRESET = "ml_default"
    private val CLOUDINARY_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"
    private val currentData = mutableListOf<FlashcardRow>()
    private var colQState = 0; private var colAState = 0; private var useGPT4 = false
    private lateinit var adapter: RowsAdapter
    private lateinit var tvLog: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGenImages: Button
    private lateinit var headerQ: TextView
    private lateinit var headerA: TextView
    private lateinit var etInput: EditText
    private lateinit var etCount: EditText
    private lateinit var btnModel: Button
    private val prefs by lazy { getSharedPreferences("flashcard_ai_prefs", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val BG_DARK = Color.parseColor("#1a1a1a")
    private val BG_CARD = Color.parseColor("#2d2d2d")
    private val TEXT_WHITE = Color.WHITE
    private val TEXT_HINT = Color.parseColor("#aaaaaa")
    private val BORDER = Color.parseColor("#555555")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { buildUI() } catch (e: Exception) {
            setContentView(TextView(this).apply { text = "ERROR: ${e.javaClass.simpleName} - ${e.message}"; setTextColor(Color.RED); setPadding(20,20,20,20) })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }

    private fun inputBg() = GradientDrawable().apply { setColor(BG_CARD); setStroke(2, BORDER); cornerRadius = 8f }
    private fun mpWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun space(dp: Int) = View(this).apply { minimumHeight = (dp * resources.displayMetrics.density).toInt() }

    private fun buildUI() {
        supportActionBar?.title = "Crear Flashcards con IA"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG_DARK); setPadding(16,16,16,32) }
        etInput = EditText(this).apply { hint = "Tema o pega tabla con | separadores"; minLines = 3; maxLines = 6; setTextColor(TEXT_WHITE); setHintTextColor(TEXT_HINT); background = inputBg(); setPadding(12,12,12,12) }
        val etInputParams = mpWrap()
        etInputParams.topMargin = (150 * resources.displayMetrics.density).toInt()
        root.addView(etInput, etInputParams); root.addView(space(8))
        val rowCount = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val countLabel = TextView(this).apply { text = "Cant: "; setPadding(0,14,8,0); setTextColor(TEXT_WHITE) }
        etCount = EditText(this).apply { setText("10"); inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(TEXT_WHITE); background = inputBg(); setPadding(8,8,8,8) }
        val btnAI = Button(this).apply { text = "CREAR CON IA"; setBackgroundColor(Color.parseColor("#E67E22")); setTextColor(TEXT_WHITE) }
        rowCount.addView(countLabel); rowCount.addView(etCount, LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT)); rowCount.addView(btnAI, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(rowCount, mpWrap()); root.addView(space(8))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnUpload = Button(this).apply { text = "1. SUBIR TABLA"; setBackgroundColor(Color.parseColor("#2980B9")); setTextColor(TEXT_WHITE) }
        val btnClear = Button(this).apply { text = "BORRAR"; setBackgroundColor(Color.parseColor("#E74C3C")); setTextColor(TEXT_WHITE) }
        row2.addView(btnUpload, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)); row2.addView(btnClear, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row2, mpWrap())
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        headerQ = TextView(this).apply { text = "PREGUNTA"; setTextColor(TEXT_WHITE); setBackgroundColor(Color.BLACK); setPadding(8,8,8,8); gravity = android.view.Gravity.CENTER }
        headerA = TextView(this).apply { text = "RESPUESTA"; setTextColor(TEXT_WHITE); setBackgroundColor(Color.BLACK); setPadding(8,8,8,8); gravity = android.view.Gravity.CENTER }
        val headerImg = TextView(this).apply { text = "IMG"; setTextColor(TEXT_WHITE); setBackgroundColor(Color.BLACK); setPadding(8,8,8,8); gravity = android.view.Gravity.CENTER }
        headerRow.addView(headerQ, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)); headerRow.addView(headerA, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)); headerRow.addView(headerImg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(headerRow, mpWrap())
        val recycler = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@FlashcardAIActivity); setBackgroundColor(BG_CARD) }
        adapter = RowsAdapter(currentData); recycler.adapter = adapter
        root.addView(recycler, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)); root.addView(space(8))
        btnGenImages = Button(this).apply { text = "2. GENERAR 3a COLUMNA"; setBackgroundColor(Color.parseColor("#7F8C8D")); setTextColor(TEXT_WHITE) }
        root.addView(btnGenImages, mpWrap())
        btnModel = Button(this).apply { text = "Modelo: Gemini 2.0"; setBackgroundColor(Color.parseColor("#1A6B3A")); setTextColor(TEXT_WHITE) }
        root.addView(btnModel, mpWrap())
        val btnExport = Button(this).apply { text = "IMPORTAR EN ANKI"; setBackgroundColor(Color.parseColor("#27AE60")); setTextColor(TEXT_WHITE) }
        root.addView(btnExport, mpWrap()); root.addView(space(8))
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = false }
        root.addView(progressBar, mpWrap())
        tvLog = TextView(this).apply { setTextColor(Color.GREEN); setBackgroundColor(Color.BLACK); setPadding(8,8,8,8); minLines = 3 }
        root.addView(tvLog, mpWrap())
        val scroll = ScrollView(this).apply { setBackgroundColor(BG_DARK) }
        scroll.addView(root); setContentView(scroll)
        checkPermissions()
        btnAI.setOnClickListener { val topic = etInput.text.toString(); val count = etCount.text.toString().toIntOrNull() ?: 10; if (topic.isNotEmpty()) startAiCreation(topic, count) else log("Error: Escribe un tema.") }
        btnUpload.setOnClickListener { val txt = etInput.text.toString(); if (txt.isNotEmpty()) parseManualUpload(txt) else log("Error: Input vacio.") }
        btnClear.setOnClickListener { currentData.clear(); adapter.notifyDataSetChanged(); colQState = 0; colAState = 0; updateHeaderColors(); log("Borrado."); progressBar.progress = 0 }
        btnGenImages.setOnClickListener { startImageGenerationProcess() }
        btnExport.setOnClickListener { exportAndImportToAnki() }
        btnModel.setOnClickListener { useGPT4 = !useGPT4; btnModel.text = if (useGPT4) "Modelo: GPT-4o" else "Modelo: Gemini 2.0"; btnModel.setBackgroundColor(Color.parseColor(if (useGPT4) "#5A2D82" else "#1A6B3A")) }
        headerQ.setOnClickListener { colQState = (colQState + 1) % 3; updateHeaderColors() }
        headerA.setOnClickListener { colAState = (colAState + 1) % 3; updateHeaderColors() }
    }

    private fun readAssetToken(f: String): String = try { assets.open(f).bufferedReader().readText().trim() } catch (e: Exception) { "" }
    private fun callAI(prompt: String): String = if (useGPT4) callGPT4(prompt) else callGemini(prompt)

    private fun callGPT4(prompt: String): String {
        val token = readAssetToken("github_token.txt"); if (token.isEmpty()) return ""
        try {
            val bodyObj = org.json.JSONObject(); bodyObj.put("model", "gpt-4o"); bodyObj.put("messages", org.json.JSONArray().put(org.json.JSONObject().put("role","user").put("content",prompt))); bodyObj.put("max_tokens", 1000); bodyObj.put("stream", false)
            val res = client.newCall(Request.Builder().url("https://models.inference.ai.azure.com/chat/completions").addHeader("Authorization","Bearer $token").addHeader("Content-Type","application/json").post(bodyObj.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute()
            if (res.isSuccessful) return org.json.JSONObject(res.body.string()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) { }
        return ""
    }

    private fun callGemini(prompt: String): String {
        for (key in API_KEYS_GEMINI) {
            try {
                val bodyObj = org.json.JSONObject(); bodyObj.put("contents", org.json.JSONArray().put(org.json.JSONObject().put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", prompt)))))
                val res = client.newCall(Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key").post(bodyObj.toString().toRequestBody("application/json".toMediaTypeOrNull())).build()).execute()
                if (res.isSuccessful) return org.json.JSONObject(res.body.string()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
            } catch (e: Exception) { continue }
        }
        return ""
    }

    private fun updateHeaderColors() {
        val colors = listOf(Color.BLACK, Color.parseColor("#165072"), Color.parseColor("#BF382B"))
        headerQ.setBackgroundColor(colors[colQState]); headerA.setBackgroundColor(colors[colAState])
        if (colQState > 0 || colAState > 0) { btnGenImages.setBackgroundColor(Color.parseColor("#2ECC71")); btnGenImages.text = "BUSCAR IMAGENES (${if (colQState==2||colAState==2) "DIRECTO" else "IA"})!" }
        else { btnGenImages.setBackgroundColor(Color.parseColor("#7F8C8D")); btnGenImages.text = "2. GENERAR 3a COLUMNA" }
    }

    private fun log(msg: String) { runOnUiThread { tvLog.append("\n> $msg") } }

    private fun startAiCreation(topic: String, qty: Int) {
        progressBar.progress = 10; log("Solicitando a ${if (useGPT4) "GPT-4o" else "Gemini"} $qty cartas...")
        lifecycleScope.launch(Dispatchers.IO) {
            val resultText = callAI("Actua como experto educativo. Genera EXACTAMENTE $qty flashcards del tema: $topic. Formato CSV punto y coma: Pregunta ; Respuesta. SOLO DOS COLUMNAS. Sin comillas, sin encabezados.")
            if (resultText.isNotEmpty()) {
                val newRows = mutableListOf<FlashcardRow>()
                resultText.lines().forEach { line -> if (line.contains(";")) { val p = line.split(";"); if (p.size >= 2) newRows.add(FlashcardRow(p[0].trim(), p[1].trim())) } }
                withContext(Dispatchers.Main) { currentData.clear(); currentData.addAll(newRows); adapter.notifyDataSetChanged(); log("Exito! ${newRows.size} filas."); progressBar.progress = 100 }
            } else { withContext(Dispatchers.Main) { log("Error: IA no respondio.") } }
        }
    }

    private fun parseManualUpload(text: String) {
        val lines = text.lines().filter { it.isNotBlank() }
        val newRows = mutableListOf<FlashcardRow>()
        val hasHeader = lines.getOrElse(0) { "" }.lowercase().let { it.contains("pregunta") || it.contains("numero") }
        for (i in (if (hasHeader) 2 else 0) until lines.size) { val parts = lines[i].split("|"); if (parts.size >= 2) newRows.add(FlashcardRow(parts[0].trim(), parts[1].trim(), if (parts.size > 2) parts[2].trim() else "PENDIENTE")) }
        currentData.clear(); currentData.addAll(newRows); adapter.notifyDataSetChanged(); log("Tabla cargada (${newRows.size} filas).")
    }

    private fun startImageGenerationProcess() {
        if (currentData.isEmpty()) return
        if (colQState == 0 && colAState == 0) { log("Selecciona encabezados!"); return }
        progressBar.max = currentData.size; progressBar.progress = 0
        lifecycleScope.launch(Dispatchers.IO) {
            currentData.forEachIndexed { index, row ->
                var q = ""; if (colQState == 2) q += "${row.q} "; if (colAState == 2) q += "${row.a} "
                if (q.isEmpty() && (colQState == 1 || colAState == 1)) q = callAI("P: ${row.q}, R: ${row.a}. Dame SOLO 3 palabras clave para imagen en Google.").trim()
                q = q.trim().ifEmpty { row.q }
                withContext(Dispatchers.Main) { log("[$index] Buscando: $q") }
                val imgUrl = searchGoogleImage(q)
                row.imgUrl = if (imgUrl.startsWith("http")) uploadToCloudinary(imgUrl) else "No Found"
                withContext(Dispatchers.Main) { adapter.notifyItemChanged(index); progressBar.progress = index + 1 }
            }
            withContext(Dispatchers.Main) { log("Proceso finalizado!") }
        }
    }

    private fun searchGoogleImage(query: String): String {
        return try {
            val response = client.newCall(Request.Builder().url("https://www.googleapis.com/customsearch/v1?key=$GOOGLE_API_KEY&cx=$GOOGLE_CX&q=$query&searchType=image&num=3").build()).execute()
            if (response.isSuccessful) { val items = org.json.JSONObject(response.body.string()).optJSONArray("items"); if (items != null && items.length() > 0) items.getJSONObject(Random.nextInt(minOf(items.length(), 3))).getString("link") else "" } else ""
        } catch (e: Exception) { "" }
    }

    private fun uploadToCloudinary(remoteUrl: String): String {
        return try {
            val response = client.newCall(Request.Builder().url(CLOUDINARY_URL).post(MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", remoteUrl).addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET).build()).build()).execute()
            if (response.isSuccessful) org.json.JSONObject(response.body.string()).getString("secure_url") else remoteUrl
        } catch (e: Exception) { remoteUrl }
    }

    private fun exportAndImportToAnki() {
        if (currentData.isEmpty()) { log("Nada que exportar."); return }
        val n = prefs.getInt("csv_counter", 0) + 1; prefs.edit().putInt("csv_counter", n).apply()
        val filename = "Flashcards_AI_$n.txt"
        val sb = StringBuilder(); currentData.forEach { sb.append("${it.q}\t${it.a}\t${it.imgUrl}\n") }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, filename); put(MediaStore.MediaColumns.MIME_TYPE, "text/plain"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) })
                uri?.let { contentResolver.openOutputStream(it)?.use { s -> s.write(sb.toString().toByteArray()) } }; if (uri != null) openAnkiImporter(uri)
            } else { @Suppress("DEPRECATION") val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename); file.writeText(sb.toString()); openAnkiImporter(Uri.fromFile(file)) }
        } catch (e: Exception) { log("Error: ${e.message}") }
    }

    private fun openAnkiImporter(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "text/plain"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setPackage("com.ichi2.anki") }) }
        catch (e: Exception) { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "text/plain"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Importar en Anki")) }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
    }

    inner class RowsAdapter(private val rows: List<FlashcardRow>) : RecyclerView.Adapter<RowsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val tvQ: TextView = v.findViewWithTag("q"); val tvA: TextView = v.findViewWithTag("a"); val tvImg: TextView = v.findViewWithTag("img") }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(BG_CARD); setPadding(4,4,4,4) }
            val q = TextView(parent.context).apply { tag = "q"; setPadding(4,4,4,4); setTextColor(TEXT_WHITE) }
            val a = TextView(parent.context).apply { tag = "a"; setPadding(4,4,4,4); setTextColor(TEXT_WHITE) }
            val img = TextView(parent.context).apply { tag = "img"; setPadding(4,4,4,4); setTextColor(TEXT_WHITE) }
            row.addView(q, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)); row.addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)); row.addView(img, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return VH(row)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = rows[position]; holder.tvQ.text = item.q; holder.tvA.text = item.a
            holder.tvImg.text = if (item.imgUrl.length > 15) "OK" else item.imgUrl
            if (item.imgUrl.startsWith("http")) holder.tvImg.setTextColor(Color.GREEN)
        }
        override fun getItemCount() = rows.size
    }
}
