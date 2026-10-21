package com.foolproof.features.video.domain

/** 视频导出（存相册）。分享由 UI 用 FileProvider 自己拼 Intent，不放这里。 */
interface VideoExporter {
    /** 仅 API ≥ 29 可无权限写相册（宪法 §8）。 */
    fun canSaveToGallery(): Boolean

    /** 成功返回 true；失败需自行清理半成品条目。 */
    suspend fun saveToGallery(inputPath: String, displayName: String): Boolean
}
