package com.foolproof.base.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统分享入口的内容中转：Activity 解析 Intent，feature 消费后清空。
 */
object SharedContentStore {

    enum class Kind { IMAGE, VIDEO, PDF }

    data class SharedContent(val kind: Kind, val uris: List<String>)

    private val _content = MutableStateFlow<SharedContent?>(null)
    val content: StateFlow<SharedContent?> = _content.asStateFlow()

    fun publish(kind: Kind, uris: List<String>) {
        val cleaned = uris.filter { it.isNotBlank() }.distinct()
        if (cleaned.isNotEmpty()) {
            _content.value = SharedContent(kind, cleaned)
        }
    }

    fun consume() {
        _content.value = null
    }

    @Suppress("DEPRECATION")
    fun publishFrom(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val mime = intent.type ?: return
        val kind = when {
            mime.startsWith("image/") -> Kind.IMAGE
            mime.startsWith("video/") -> Kind.VIDEO
            mime == "application/pdf" -> Kind.PDF
            else -> return
        }
        val uris = if (action == Intent.ACTION_SEND) {
            listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
        // PDF 分享的 Uri 权限/Provider 不稳定，先在 Activity 有权限时落到自有缓存
        val values = if (kind == Kind.PDF && context != null) {
            uris.map { uri -> copyPdfToCache(context, uri) ?: uri.toString() }
        } else {
            uris.map { it.toString() }
        }
        publish(kind, values)
    }

    private fun copyPdfToCache(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.cacheDir, "shared_pdf").apply { mkdirs() }
        val target = File(dir, "shared_${System.currentTimeMillis()}.pdf")
        val input = when (uri.scheme) {
            null -> File(uri.path ?: return null).inputStream()
            "file" -> File(uri.path ?: return null).inputStream()
            else -> context.contentResolver.openInputStream(uri) ?: return null
        }
        input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()
}
