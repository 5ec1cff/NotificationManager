package io.github.a13e300.notificationmanager

import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class DetailSnapshot(
    val json: String = "",
    val images: List<DetailImage> = emptyList(),
    val error: String? = null,
)

internal data class DetailImage(val file: File, val title: String, val source: String, val width: Int, val height: Int) {
    val description: String get() = "$width × $height 像素\n来源：$source"
}

internal fun loadDetailSnapshot(directory: File): DetailSnapshot {
    val raw = runCatching { File(directory, "details.json").readText() }
        .getOrElse { return DetailSnapshot(error = "无法读取快照：${it.message}") }
    val json = runCatching { JSONObject(raw) }
        .getOrElse { return DetailSnapshot(json = raw, error = "快照格式错误：${it.message}") }
    val images = linkedMapOf<String, DetailImage>()
    val visitedFiles = mutableSetOf<String>()

    fun addImage(name: String, title: String, source: String) {
        if (!visitedFiles.add(name) || File(name).name != name) return
        val file = File(directory, name)
        if (!file.isFile) return
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash !in images) {
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, size)
                if (size.outWidth > 0 && size.outHeight > 0) {
                    images[hash] = DetailImage(file, title, source, size.outWidth, size.outHeight)
                }
            }
        }
    }

    fun visit(value: Any?, title: String, source: String) {
        when (value) {
            is JSONObject -> {
                value.optString("image").takeIf { it.isNotEmpty() }?.let { addImage(it, title, source) }
                value.keys().forEach { key -> visit(value.opt(key), title, "$source.$key") }
            }
            is JSONArray -> repeat(value.length()) { index -> visit(value.opt(index), title, "$source[$index]") }
        }
    }

    // Use named properties first, so duplicate copies keep a clear label.
    val properties = json.optJSONObject("properties")
    visit(properties?.opt("smallIcon"), "通知小图标", "smallIcon")
    visit(properties?.opt("largeIcon"), "通知大图标", "largeIcon")
    visit(json.optJSONObject("notification")?.opt("extras"), "通知附加图片", "extras")
    visit(json.optJSONObject("notification")?.opt("actions"), "通知操作图标", "actions")
    visit(json, "通知图像", "notification")
    return DetailSnapshot(json = raw, images = images.values.toList())
}
