package io.github.a13e300.notificationmanager

import android.app.NotificationChannel
import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Build
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Modifier

/** Store readable data and image files, never Parcel bytes with live Binder handles. */
class NotificationSnapshot(private val context: Context, private val directory: File) {
    private var asset = 0
    fun saveIcon(icon: Icon?, name: String): String? {
        if (icon == null) return null
        return runCatching {
            val drawable = icon.loadDrawable(context) ?: return null
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 128, 128)
            drawable.draw(Canvas(bitmap))
            saveBitmap(bitmap, name)
        }.getOrNull()
    }
    private fun saveBitmap(bitmap: Bitmap, name: String): String {
        val file = File(directory, "${asset++}-$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return file.name
    }
    fun capture(sbn: StatusBarNotification, ranking: Ranking?): JSONObject = JSONObject().apply {
        put("notification", fields(sbn.notification, 0))
        put("sdkProperties", getters(sbn.notification, 0))
        put("properties", JSONObject().apply {
            val n = sbn.notification
            put("channelId", n.channelId); put("group", n.group); put("sortKey", n.sortKey)
            put("timeoutAfter", n.timeoutAfter); put("badgeIconType", n.badgeIconType)
            put("shortcutId", n.shortcutId); put("settingsText", n.settingsText)
            put("smallIcon", value(n.smallIcon, 0)); put("largeIcon", value(n.getLargeIcon(), 0))
            put("bubbleMetadata", value(n.bubbleMetadata, 0))
            put("allowSystemGeneratedContextualActions", n.allowSystemGeneratedContextualActions)
        })
        put("statusBar", JSONObject().apply {
            put("package", sbn.packageName); put("opPackage", sbn.opPkg); put("uid", sbn.uid)
            put("userId", sbn.user.hashCode()); put("key", sbn.key); put("groupKey", sbn.groupKey)
            put("overrideGroupKey", sbn.overrideGroupKey); put("postTime", sbn.postTime)
            put("id", sbn.id); put("tag", sbn.tag); put("ongoing", sbn.isOngoing)
            put("clearable", sbn.isClearable); put("isGroup", sbn.isGroup)
        })
        ranking?.let { r -> put("ranking", JSONObject().apply {
            put("rank", r.rank); put("importance", r.importance); put("ambient", r.isAmbient)
            put("suppressedVisualEffects", r.suppressedVisualEffects); put("matchesInterruptionFilter", r.matchesInterruptionFilter())
            put("channel", r.channel?.let { getters(it, 0) }); put("overrideGroupKey", r.overrideGroupKey)
            put("canBubble", r.canBubble()); put("suspended", r.isSuspended)
        }) }
        put("format", "Readable SDK snapshot v1. Unsupported objects are marked with class and description. Icons are rendered at 128px. Bitmap extras keep original resolution.")
    }
    private fun fields(target: Any, depth: Int): JSONObject = JSONObject().apply {
        target.javaClass.fields.filter { !Modifier.isStatic(it.modifiers) }.sortedBy { it.name }.forEach { field ->
            put(field.name, runCatching { value(field.get(target), depth + 1) }.getOrElse { error(it) })
        }
    }
    private fun getters(target: Any, depth: Int): JSONObject = JSONObject().apply {
        target.javaClass.methods.filter {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 &&
                (it.name.startsWith("get") || it.name.startsWith("is")) && it.name != "getClass"
        }.sortedBy { it.name }.forEach { method ->
            put(method.name, runCatching { value(method.invoke(target), depth + 1) }.getOrElse { error(it) })
        }
    }
    private fun error(e: Throwable) = JSONObject().put("unavailable", e.javaClass.simpleName).put("message", e.message)
    private fun value(item: Any?, depth: Int): Any {
        if (item == null) return JSONObject.NULL
        if (depth > 16) return JSONObject().put("omitted", "nested more than 16 levels").put("class", item.javaClass.name)
        return when (item) {
            is CharSequence -> item.toString()
            is Number, is Boolean -> item
            is Bundle -> JSONObject().apply { item.keySet().sorted().forEach { key ->
                put(key, runCatching { @Suppress("DEPRECATION") value(item.get(key), depth + 1) }.getOrElse { error(it) })
            } }
            is Bitmap -> JSONObject().put("image", saveBitmap(item, "bitmap")).put("width", item.width).put("height", item.height)
            is Icon -> JSONObject().put("description", item.toString()).put("image", saveIcon(item, "icon"))
            is PendingIntent -> JSONObject().apply {
                put("creatorPackage", item.creatorPackage); put("creatorUid", item.creatorUid)
                put("creatorUser", item.creatorUserHandle.hashCode())
                if (Build.VERSION.SDK_INT >= 31) {
                    put("isActivity", item.isActivity); put("isBroadcast", item.isBroadcast)
                    put("isService", item.isService); put("isForegroundService", item.isForegroundService)
                    put("immutable", item.isImmutable)
                }
                put("description", item.toString()); put("intent", "requires privileged extraction")
            }
            is Notification.Action -> fields(item, depth).apply {
                put("icon", value(item.getIcon(), depth + 1)); put("extras", value(item.extras, depth + 1))
                put("remoteInputs", value(item.remoteInputs, depth + 1)); put("semanticAction", item.semanticAction)
                put("allowGeneratedReplies", item.allowGeneratedReplies)
                put("contextual", item.isContextual); if (Build.VERSION.SDK_INT >= 31) put("authenticationRequired", item.isAuthenticationRequired)
            }
            is RemoteInput -> JSONObject().apply {
                put("resultKey", item.resultKey); put("label", value(item.label, depth + 1))
                put("choices", value(item.choices, depth + 1)); put("extras", value(item.extras, depth + 1))
                put("allowFreeFormInput", item.allowFreeFormInput); put("allowedDataTypes", value(item.allowedDataTypes, depth + 1))
            }
            is Person -> JSONObject().apply {
                put("name", item.name); put("key", item.key); put("uri", item.uri)
                put("bot", item.isBot); put("important", item.isImportant); put("icon", value(item.icon, depth + 1))
            }
            is Notification -> fields(item, depth)
            is NotificationChannel, is Notification.BubbleMetadata, is android.media.AudioAttributes -> getters(item, depth)
            is RemoteViews -> JSONObject().put("package", item.`package`).put("layoutId", item.layoutId)
                .put("note", "RemoteViews actions and rendered view are not captured")
            is Iterable<*> -> JSONArray().apply { item.forEach { put(value(it, depth + 1)) } }
            else -> if (item.javaClass.isArray) JSONArray().apply {
                repeat(java.lang.reflect.Array.getLength(item)) { put(value(java.lang.reflect.Array.get(item, it), depth + 1)) }
            } else JSONObject().put("class", item.javaClass.name).put("description", item.toString())
        }
    }
}
