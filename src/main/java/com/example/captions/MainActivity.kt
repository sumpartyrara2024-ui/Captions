package com.example.captions

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var caption: TextView
    private lateinit var status: TextView
    private lateinit var injectJs: String

    private var webViewInfo = "Chrome ?"
    private var level = ""
    private var note = ""
    private var lang = "ja"
    private var workerUrl = ""

    private val net = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val hideCaption = Runnable { caption.visibility = View.INVISIBLE }

    // Called from inject.js as Captions.event(...) and Captions.audio(...)
    inner class Bridge {
        @JavascriptInterface
        fun event(type: String, value: String) {
            runOnUiThread {
                if (type == "audio level") {
                    level = "level $value"
                } else {
                    note = "$type: $value"
                }
                refreshStatus()
            }
        }

        @JavascriptInterface
        fun audio(base64Wav: String) {
            sendAudio(base64Wav)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        injectJs = assets.open("inject.js").bufferedReader().use { it.readText() }

        workerUrl = try {
            assets.open("config.txt").bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            ""
        }
        if (!workerUrl.startsWith("http")) workerUrl = ""
        note = if (workerUrl.isEmpty()) "config.txt missing!" else "ready"

        if (Build.VERSION.SDK_INT >= 26) {
            val pkg = WebView.getCurrentWebViewPackage()
            webViewInfo = "Chrome " + (pkg?.versionName ?: "?").substringBefore('.')
        }

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = FrameLayout(this)
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(match, match))

        val urlBox = EditText(this)
        urlBox.setSingleLine()
        urlBox.setText(START_URL)
        urlBox.setTextColor(Color.WHITE)
        urlBox.setBackgroundColor(Color.argb(170, 0, 0, 0))
        urlBox.imeOptions = EditorInfo.IME_ACTION_GO
        urlBox.setOnEditorActionListener { v, _, _ ->
            go(v.text.toString())
            true
        }

        status = TextView(this)
        status.setTextColor(Color.YELLOW)
        status.setBackgroundColor(Color.argb(170, 0, 0, 0))
        status.textSize = 14f

        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.addView(urlBox)
        top.addView(status)
        root.addView(top, FrameLayout.LayoutParams(match, wrap, Gravity.TOP))

        caption = TextView(this)
        caption.setTextColor(Color.WHITE)
        caption.setBackgroundColor(Color.argb(170, 0, 0, 0))
        caption.textSize = 28f
        caption.gravity = Gravity.CENTER
        caption.setPadding(24, 12, 24, 12)
        root.addView(caption, FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM))
        showCaption("English captions will appear here")

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        WebView.setWebContentsDebuggingEnabled(true)
        web.addJavascriptInterface(Bridge(), "Captions")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(injectJs, null)
            }
        }

        refreshStatus()
        setContentView(root)
        web.loadUrl(START_URL)
        web.requestFocus()
    }

    private fun refreshStatus() {
        val name = if (lang == "ja") "Japanese" else "Filipino"
        status.text = "$webViewInfo | $name (Menu button switches) | $level | $note"
    }

    private fun showCaption(text: String) {
        caption.text = text
        caption.visibility = View.VISIBLE
        ui.removeCallbacks(hideCaption)
        ui.postDelayed(hideCaption, 8000)
    }

    private fun sendAudio(b64: String) {
        if (workerUrl.isEmpty()) {
            runOnUiThread {
                note = "config.txt missing!"
                refreshStatus()
            }
            return
        }
        val langNow = lang
        net.execute {
            try {
                val body = JSONObject().put("audio", b64).put("lang", langNow).toString()
                val conn = URL(workerUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code in 200..299) {
                    val json = JSONObject(resp)
                    val text = json.optString("text", "").trim()
                    val heard = json.optString("original", "").trim()
                    runOnUiThread {
                        note = if (heard.isEmpty()) "heard nothing" else "heard: " + heard.take(60)
                        if (text.isNotEmpty()) showCaption(text)
                        refreshStatus()
                    }
                } else {
                    runOnUiThread {
                        note = "server $code: " + resp.take(80)
                        refreshStatus()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    note = "network error: " + e.message
                    refreshStatus()
                }
            }
        }
    }

    private fun go(input: String) {
        val u = if (input.startsWith("http")) input else "https://$input"
        web.loadUrl(u)
        web.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            lang = if (lang == "ja") "tl" else "ja"
            note = "language switched"
            refreshStatus()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        const val START_URL = "https://www.w3schools.com/html/mov_bbb.mp4"
    }
}
