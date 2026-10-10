package com.example.captions

import android.app.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.Rational
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    lateinit var web: WebView; lateinit var cap: TextView; lateinit var status: TextView
    lateinit var top: LinearLayout; lateinit var urlBox: EditText; lateinit var full: FrameLayout; lateinit var js: String
    val bm by lazy { Bookmarks(this) }
    val net = Executors.newSingleThreadExecutor()
    val ui = Handler(Looper.getMainLooper())
    val hideCap = Runnable { cap.visibility = View.INVISIBLE }
    val hideBars = Runnable { hideToolbar() }
    var level = ""; var note = ""; var lang = "ja"; var worker = ""
    var onHome = true; var folder = ""; var saveIn = ""; var curSite = ""; var navMs = 0L
    var custom: View? = null; var customCb: WebChromeClient.CustomViewCallback? = null; var dlg: AlertDialog? = null

    inner class Bridge {
        @JavascriptInterface
        fun event(type: String, value: String) {
            runOnUiThread { if (type == "audio level") level = value else note = "$type: $value"; refresh() }
        }
        @JavascriptInterface
        fun audio(b64: String) { send(b64) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        js = assets.open("inject.js").bufferedReader().use { it.readText() }
        worker = try { assets.open("config.txt").bufferedReader().use { it.readText().trim() } } catch (e: Exception) { "" }
        if (!worker.startsWith("http")) { worker = ""; note = "config.txt missing!" }
        val m = ViewGroup.LayoutParams.MATCH_PARENT
        val w = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = FrameLayout(this)
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(m, m))
        full = FrameLayout(this)
        full.setBackgroundColor(Color.BLACK)
        full.visibility = View.GONE
        root.addView(full, FrameLayout.LayoutParams(m, m))
        urlBox = EditText(this)
        urlBox.setSingleLine()
        urlBox.hint = "Type a web address"
        urlBox.setTextColor(Color.WHITE)
        urlBox.setBackgroundColor(Color.argb(170, 0, 0, 0))
        urlBox.imeOptions = EditorInfo.IME_ACTION_GO
        urlBox.setOnEditorActionListener { v, _, _ -> go(v.text.toString()); true }
        val row = LinearLayout(this)
        row.addView(btn("\uD83C\uDFE0 Home") { folder = ""; showHome() })
        row.addView(btn("\u2605 Save") { saveCurrent() })
        row.addView(btn("PiP") { enterPip() })
        row.addView(btn("\u2716 Exit") { finish() })
        row.addView(urlBox, LinearLayout.LayoutParams(0, w, 1f))
        status = TextView(this)
        status.setTextColor(Color.YELLOW)
        status.setBackgroundColor(Color.argb(170, 0, 0, 0))
        top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.addView(row)
        top.addView(status)
        root.addView(top, FrameLayout.LayoutParams(m, w, Gravity.TOP))
        cap = TextView(this)
        cap.setTextColor(Color.WHITE)
        cap.setBackgroundColor(Color.argb(170, 0, 0, 0))
        cap.textSize = 28f
        cap.gravity = Gravity.CENTER
        root.addView(cap, FrameLayout.LayoutParams(m, w, Gravity.BOTTOM))
        showCaption("English captions will appear here")
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.setSupportMultipleWindows(true)
        web.addJavascriptInterface(Bridge(), "Captions")
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView?, d: Boolean, g: Boolean, r: Message?): Boolean = false
            override fun onJsAlert(v: WebView?, u: String?, s: String?, r: JsResult?): Boolean { r?.cancel(); return true }
            override fun onJsConfirm(v: WebView?, u: String?, s: String?, r: JsResult?): Boolean { r?.cancel(); return true }
            override fun onShowCustomView(view: View?, cb: CustomViewCallback?) {
                if (view == null || custom != null) { cb?.onCustomViewHidden(); return }
                custom = view; customCb = cb
                full.addView(view, FrameLayout.LayoutParams(m, m))
                full.visibility = View.VISIBLE
            }
            override fun onHideCustomView() { exitFull() }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url
                val s = u.scheme ?: ""
                if (s == "app") { handleApp(u); return true }
                if (s != "http" && s != "https") return true
                if (!r.isForMainFrame) return false
                val now = System.currentTimeMillis()
                if (onHome || r.hasGesture()) {
                    if (onHome) saveIn = folder
                    curSite = site(u.host); navMs = now; onHome = false
                    return false
                }
                val fresh = r.isRedirect && now - navMs < 6000
                if (fresh || site(u.host) == curSite) { if (fresh) curSite = site(u.host); return false }
                note = "blocked redirect to " + u.host; refresh()
                return true
            }
            override fun onPageFinished(v: WebView, url: String?) {
                if (onHome) v.clearHistory()
                v.evaluateJavascript(js, null)
            }
        }
        setContentView(root)
        refresh(); showHome(); showBars()
    }

    fun site(h: String?) = (h ?: "").split('.').takeLast(2).joinToString(".")

    fun btn(label: String, act: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label; t.textSize = 18f
        t.setTextColor(Color.WHITE)
        t.setBackgroundColor(Color.argb(170, 40, 40, 40))
        t.setPadding(28, 14, 28, 14)
        t.isFocusable = true; t.isClickable = true
        t.setOnFocusChangeListener { v, f -> v.setBackgroundColor(if (f) Color.rgb(200, 140, 0) else Color.argb(170, 40, 40, 40)) }
        t.setOnClickListener { act() }
        return t
    }

    fun showBars() { top.visibility = View.VISIBLE; ui.removeCallbacks(hideBars); ui.postDelayed(hideBars, 7000) }

    fun hideToolbar() {
        if (urlBox.hasFocus() || dlg?.isShowing == true) { ui.postDelayed(hideBars, 3000); return }
        top.visibility = View.GONE
        if (top.hasFocus()) web.requestFocus()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) showBars()
        return super.dispatchKeyEvent(e)
    }

    fun exitFull() {
        val v = custom ?: return
        full.removeView(v); full.visibility = View.GONE
        custom = null; customCb?.onCustomViewHidden(); customCb = null
        web.requestFocus()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            note = "PiP not supported here"; refresh(); return
        }
        try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        catch (e: Exception) { note = "PiP failed: " + e.message; refresh() }
    }

    fun showHome() {
        onHome = true
        web.loadDataWithBaseURL(HOME, bm.homeHtml(folder), "text/html", "utf-8", HOME)
        web.requestFocus()
    }

    fun handleApp(u: Uri) {
        val n = u.lastPathSegment?.toIntOrNull() ?: -1
        when (u.host) {
            "remove" -> { bm.remove(n); ui.post { showHome() } }
            "move" -> ui.post { askMove(n) }
            "folder" -> { folder = bm.folders().getOrElse(n) { "" }; ui.post { showHome() } }
            "root" -> { folder = ""; ui.post { showHome() } }
            "newfolder" -> ui.post { askFolder() }
            "delfolder" -> { bm.folders().getOrNull(n)?.let { bm.deleteFolder(it) }; folder = ""; ui.post { showHome() } }
        }
    }

    fun askFolder() {
        val input = EditText(this)
        input.setSingleLine()
        dlg = AlertDialog.Builder(this).setTitle("New folder name").setView(input)
            .setPositiveButton("Create") { _, _ -> bm.addFolder(input.text.toString()); showHome() }
            .setNegativeButton("Cancel", null).show()
    }

    fun askMove(i: Int) {
        val names = ArrayList<String>()
        names.add("All bookmarks (no folder)"); names.addAll(bm.folders())
        dlg = AlertDialog.Builder(this).setTitle("Move to...")
            .setItems(names.toTypedArray()) { _, k -> bm.move(i, if (k == 0) "" else names[k]); showHome() }.show()
    }

    fun saveCurrent() {
        val url = web.url ?: ""
        if (onHome || !url.startsWith("http")) { note = "open a site first, then press Save"; refresh(); return }
        val title = (web.title ?: "").trim().ifEmpty { url.substringAfter("://").substringBefore('/') }
        note = if (bm.add(title, url, saveIn)) "saved: $title" else "already saved"
        refresh()
    }

    fun refresh() { status.text = (if (lang == "ja") "Japanese" else "Filipino") + " (Menu switches) | $level | $note" }

    fun showCaption(t: String) {
        cap.text = t; cap.visibility = View.VISIBLE
        ui.removeCallbacks(hideCap); ui.postDelayed(hideCap, 8000)
    }

    fun send(b64: String) {
        if (worker.isEmpty()) return
        val l = lang
        net.execute {
            val r = Translator.send(worker, b64, l)
            runOnUiThread {
                if (r.error.isNotEmpty()) note = r.error
                else { note = "heard: " + r.heard.take(60); if (r.english.isNotEmpty()) showCaption(r.english) }
                refresh()
            }
        }
    }

    fun go(input: String) {
        val u = if (input.startsWith("http")) input else "https://$input"
        onHome = false; saveIn = ""; curSite = site(Uri.parse(u).host); navMs = System.currentTimeMillis()
        web.loadUrl(u); web.requestFocus()
    }

    override fun onKeyDown(k: Int, e: KeyEvent?): Boolean {
        if (k == KeyEvent.KEYCODE_MENU) { lang = if (lang == "ja") "tl" else "ja"; note = "language switched"; refresh(); return true }
        return super.onKeyDown(k, e)
    }

    override fun onBackPressed() {
        if (custom != null) exitFull()
        else if (onHome) { if (folder.isNotEmpty()) { folder = ""; showHome() } else super.onBackPressed() }
        else if (web.copyBackForwardList().currentIndex <= 1) showHome()
        else web.goBack()
    }

    companion object { const val HOME = "https://captions.home/" }
}
