package io.agora.mediarelay.rtc.service

import androidx.annotation.Size
import io.agora.rtc2.video.VideoEncoderConfiguration

data class ChannelUid constructor(
    val channel: String,
    val uid: Int,
)

data class TransInputItem constructor(
    val channel: String,
    val uid: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class TransOutput constructor(
    val channel: String,
    val uid: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class TranscodeSetting constructor(
    val uid: Int,
    val rtcChannel: String,
    val fps: Int,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val inputItems: List<TransInputItem>,
    val output: TransOutput
) {

    // 480p
    // h264
    // 1500

    companion object {
        private val bitRate = 1000

        private val videoDimensions = VideoEncoderConfiguration.VD_1920x1080

        /**云端转码*/
        fun cloudTranscoding(
            uid: Int,
            channel: String,
            transcodeUid: Int,
            @Size(min = 1) vararg channelUids: ChannelUid,
        ): TranscodeSetting {
            // 竖屏
            val fullWidth = videoDimensions.height
            val fullHeight = videoDimensions.width
            val inputItems = mutableListOf<TransInputItem>()
            // 分配主播的画面布局。
            var totalX = 0
            var totalY = 0
            if (channelUids.size > 1) {
                totalY = fullHeight / (channelUids.size * channelUids.size)
            }
            channelUids.forEach {
                val item = TransInputItem(
                    channel = it.channel,
                    uid = it.uid,
                    x = totalX,
                    y = totalY,
                    width = fullWidth / channelUids.size,
                    height = fullHeight / channelUids.size
                )
                inputItems.add(item)
                totalX += fullWidth / channelUids.size
            }
            val output = TransOutput(
                channel = channel,
                uid = transcodeUid,
                x = 0,
                y = 0,
                width = fullWidth,
                height = fullHeight
            )
            // 默认1080p24fps分辨率。
            return TranscodeSetting(
                uid,
                channel,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24.value,
                bitRate,
                fullWidth,
                fullHeight,
                inputItems,
                output
            )
        }
    }
}

data class ResponseCloudToken constructor(
    val createTs: Long,
    val instanceId: String,
    val tokenName: String,
)

data class ResponseCloudTranscoder constructor(
    val createTs: String,
    val eventHandlers: Any,
    val execution: Any,
    val properties: Any,
    val sequenceId: String,
    val services: CloudServicesModel,
    val status: String,
    val taskId: String,
    val variables: Any,
    val workflows: Any,
)

data class CloudServicesModel constructor(val cloudTranscoder: CloudTranscoderModel)

data class CloudTranscoderModel constructor(
    val config: Map<String, Any>,
    val createTs: Long,
    val message: String,
    val serviceType: String,
    val status: String,
)