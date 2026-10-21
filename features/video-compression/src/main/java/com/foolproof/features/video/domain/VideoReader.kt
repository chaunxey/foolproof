package com.foolproof.features.video.domain

/** 读取视频元信息。抽成接口便于 ViewModel 单测。 */
interface VideoReader {
    /** 读取失败时抛异常，由 ViewModel 归类为 [VideoFailure.SOURCE_UNREADABLE]。 */
    suspend fun read(id: String): SourceVideo
}
