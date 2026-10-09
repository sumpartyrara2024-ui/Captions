package com.example.captions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Bookmarks(context: Context) {

    private val prefs = context.getSharedPreferences("captions", Context.MODE_PRIVATE)

    private fun item(title: String, url: String, folder: String): JSONObject =
        JSONObject().put("title", title).put("url", url).put("folder", folder)

    fun items(): JSONArray {
        val raw = prefs.getString("bookmarks", null)
        if (raw == null) {
            val seed = JSONArray()
            seed.put(item("English test clip", "https://www.w3schools.com/html/mov_bbb.mp4", ""))
            seed.put(item("Google", "https://www.google.com", ""))
            saveItems(seed)
            return seed
        }
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun saveItems(list: JSONArray) {
        prefs.edit().putString("bookmarks", list.toString()).apply()
    }

    fun folders(): List<String> {
        val raw = prefs.getString("folders", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) out.add(arr.getString(i))
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveFolders(list: List<String>) {
        val arr = JSONArray()
        for (f in list) arr.put(f)
        prefs.edit().putString("folders", arr.toString()).apply()
    }

    fun addFolder(name: String): Boolean {
        val n = name.trim().take(30)
        val current = folders()
        if (n.isEmpty() || current.contains(n)) return false
        saveFolders(current + n)
        return true
    }

    fun deleteFolder(name: String) {
        val list = items()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.optString("folder") == name) o.put("folder", "")
        }
        saveItems(list)
        saveFolders(folders().filter { it != name })
    }

    fun add(title: String, url: String, folder: String): Boolean {
        val list = items()
        for (i in 0 until list.length()) {
            if (list.getJSONObject(i).optString("url") == url) return false
        }
        list.put(item(title.take(60), url, folder))
        saveItems(list)
        return true
    }

    fun remove(index: Int) {
        val list = items()
        if (index < 0 || index >= list.length()) return
        val out = JSONArray()
        for (i in 0 until list.length()) {
            if (i != index) out.put(list.get(i))
        }
        saveItems(out)
    }

    fun move(index: Int, folder: String) {
        val list = items()
        if (index < 0 || index >= list.length()) return
        list.getJSONObject(index).put("folder", folder)
        saveItems(list)
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun countIn(list: JSONArray, folder: String): Int {
        var n = 0
        for (i in 0 until list.length()) {
            if (list.getJSONObject(i).optString("folder") == folder) n++
        }
        return n
    }

    fun homeHtml(openFolder: String): String {
        val list = items()
        val folders = folders()
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<style>").append(CSS).append("</style></head><body>")
        if (openFolder.isEmpty()) {
            sb.append("<h1>Captions</h1>")
            sb.append("<p>Pick with the arrow keys. To add a site, open it and press Save in the top bar. Menu button: switch Japanese / Filipino. Any button press shows the toolbar.</p>")
            sb.append("<div class=\"row\"><a class=\"tool\" href=\"app://newfolder\">+ New folder</a></div>")
            for (f in folders.indices) {
                sb.append("<div class=\"row\"><a class=\"site\" href=\"app://folder/").append(f).append("\">")
                sb.append("\uD83D\uDCC1 ").append(esc(folders[f])).append("<small>")
                sb.append(countIn(list, folders[f])).append(" saved</small></a></div>")
            }
        } else {
            val fi = folders.indexOf(openFolder)
            sb.append("<h1>\uD83D\uDCC1 ").append(esc(openFolder)).append("</h1>")
            sb.append("<div class=\"row\"><a class=\"tool\" href=\"app://root\">\u2190 All bookmarks</a>")
            sb.append("<a class=\"tool del\" href=\"app://delfolder/").append(fi).append("\">Delete folder</a></div>")
        }
        var shown = 0
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.optString("folder") != openFolder) continue
            shown++
            val title = esc(o.optString("title"))
            val url = esc(o.optString("url"))
            sb.append("<div class=\"row\"><a class=\"site\" href=\"").append(url).append("\">")
            sb.append(title).append("<small>").append(url).append("</small></a>")
            sb.append("<a class=\"act\" href=\"app://move/").append(i).append("\">Move</a>")
            sb.append("<a class=\"act del\" href=\"app://remove/").append(i).append("\">Remove</a></div>")
        }
        if (shown == 0 && (openFolder.isNotEmpty() || folders.isEmpty())) {
            sb.append("<p>Nothing saved here yet.</p>")
        }
        sb.append("<script>var f=document.querySelector('a.site')||document.querySelector('a.tool');if(f)f.focus();</script>")
        sb.append("</body></html>")
        return sb.toString()
    }

    companion object {
        const val CSS = """
body{margin:0;padding:130px 40px 40px;background:#101418;color:#fff;font-family:sans-serif}
h1{font-size:36px;margin:0 0 8px}
p{color:#9aa4ad;font-size:20px;margin:0 0 28px}
.row{display:flex;gap:16px;margin:0 0 20px}
a{display:block;padding:24px 28px;background:#1e252c;color:#fff;text-decoration:none;font-size:30px;border-radius:14px;border:4px solid transparent}
a.site{flex:1;min-width:0;overflow:hidden}
a.act,a.tool{font-size:24px;display:flex;align-items:center;justify-content:center}
a.act{width:150px}
a.del{color:#ff9a9a}
a:focus{border-color:#ffd54a;background:#2a343d;outline:none}
small{display:block;color:#9aa4ad;font-size:16px;margin-top:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
"""
    }
}
