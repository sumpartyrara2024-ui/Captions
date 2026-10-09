package com.example.captions

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
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

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var caption: TextView
    private lateinit var status: TextView
    private lateinit var injectJs: String
    private var webViewInfo = "WebView ?"

    // Called from inject.js as Captions.event(type, value)
    inner class Bridge {
        @JavascriptInterface
        fun event(type: String, value: String) {
            runOnUiThread {
                if (type == "caption") {
                    caption.text = value
                } else {
                    status.text = "$webViewInfo | $type: $value"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        injectJs = assets.open("inject.js").bufferedReader().use { it.readText() }

        if (Build.VERSION.SDK_INT >= 26) {
            val pkg = WebView.getCurrentWebViewPackage()
            webViewInfo = "WebView " + (pkg?.versionName ?: "?")
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
        status.text = webViewInfo

        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.addView(urlBox)
        top.addView(status)
        root.addView(top, FrameLayout.LayoutParams(match, wrap, Gravity.TOP))

        caption = TextView(this)
        caption.text = "English captions will appear here"
        caption.setTextColor(Color.WHITE)
        caption.setBackgroundColor(Color.argb(170, 0, 0, 0))
        caption.textSize = 28f
        caption.gravity = Gravity.CENTER
        caption.setPadding(24, 12, 24, 12)
        root.addView(caption, FrameLayout.LayoutParams(match, wrap, Gravity.BOTTOM))

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

        setContentView(root)
        web.loadUrl(START_URL)
        web.requestFocus()
    }

    private fun go(input: String) {
        val u = if (input.startsWith("http")) input else "https://$input"
        web.loadUrl(u)
        web.requestFocus()
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        // A direct video file: same-origin, so audio capture works for the first test.
        const val START_URL = "https://www.w3schools.com/html/mov_bbb.mp4"
    }
}
