package com.foolproof.features.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.data.Media3VideoCompressor
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoProgress
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 裁剪精度（SC-003）：裁 5 秒，输出时长必须是 5000ms ±200ms。
 *
 * 用 MediaCodec 现场生成一段 10 秒的合成素材（纯色画面），不依赖相册里的视频，
 * 也不依赖 `screenrecord`（需要 shell 权限）。
 */
@RunWith(AndroidJUnit4::class)
class TrimAccuracyTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun trimFiveSecondsProducesFiveSecondVideo() {
        val sourceFile = File(context.cacheDir, "trim_source.mp4")
        val outputFile = File(context.cacheDir, "trim_output.mp4")
        outputFile.delete()

        createSyntheticVideo(sourceFile, durationMs = 10_000)

        val sourceDuration = readDurationMs(sourceFile)
        assertTrue("合成素材应约 10 秒，实际 $sourceDuration", sourceDuration in 9_000..11_000)

        val compressor = Media3VideoCompressor(context, LogUtil(enabled = false))
        val request = VideoCompressRequest(
            sourceId = Uri.fromFile(sourceFile).toString(),
            outputPath = outputFile.absolutePath,
            targetShortSide = 480,
            videoBitrateKbps = 1_000,
            frameRate = 30,
            trimStartMs = 5_000,
            trimEndMs = 10_000,
            hasAudio = false,
        )

        var successPath: String? = null
        var failure: String? = null
        runBlocking {
            compressor.compress(request).collect { progress ->
                when (progress) {
                    is VideoProgress.Success -> successPath = progress.path
                    is VideoProgress.Failed -> failure = progress.reason.name
                    else -> Unit
                }
            }
        }

        assertNotNull("转码失败: $failure", successPath)
        assertTrue("输出文件应存在", outputFile.exists())

        val outputDuration = readDurationMs(outputFile)
        val delta = kotlin.math.abs(outputDuration - 5_000)
        assertTrue(
            "输出时长应为 5000ms ±200ms，实际 ${outputDuration}ms（偏差 ${delta}ms）",
            delta <= 200,
        )

        sourceFile.delete()
        outputFile.delete()
    }

    /**
     * 生成一段纯色 H.264 视频（YUV420Planar 软编），不引入任何测试依赖。
     */
    private fun createSyntheticVideo(file: File, durationMs: Long) {
        file.delete()
        val width = 640
        val height = 480
        val frameRate = 30

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val frameCount = (durationMs * frameRate / 1000).toInt()
        val frameSize = width * height * 3 / 2
        val yuv = ByteArray(frameSize).apply {
            // Y 平面填 16（黑），U/V 填 128（中性）
            java.util.Arrays.fill(this, 0, width * height, 16.toByte())
            java.util.Arrays.fill(this, width * height, frameSize, 128.toByte())
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var inputIndex = 0
        var outputIndex = 0

        while (outputIndex <= frameCount && !Thread.currentThread().isInterrupted) {
            // 送一个输入帧
            if (inputIndex < frameCount) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    buffer.clear()
                    buffer.put(yuv)
                    val ptsUs = inputIndex * 1_000_000L / frameRate
                    codec.queueInputBuffer(inIndex, 0, yuv.size, ptsUs, 0)
                    inputIndex++
                }
            } else if (inputIndex == frameCount) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputIndex++
                }
            }

            // 取输出
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        outputIndex++
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
