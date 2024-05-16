package io.agora.mediarelay.rtc.transcoder

import androidx.annotation.Size
import io.agora.rtc2.video.VideoEncoderConfiguration

data class ChannelUid constructor(
    val channel: String,
    val uid: Int,
    val userAccount: String
)

data class TransInputItem constructor(
    val channel: String,
    val uid: Int,
    val account: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class TranscodeSetting constructor(
    val enableUserAccount: Boolean,
    val uid: Int,
    val rtcChannel: String,
    val cdnURL: String,
    val fps: Int,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val inputItems: List<TransInputItem>
) {

    // 480p
    // h264
    // 1500

    companion object {
        private val bitRate = 3072

        private val videoDimensions = VideoEncoderConfiguration.VD_1920x1080

        /**旁路推流转码1v1*/
        fun liveTranscoding(
            enableUserAccount: Boolean,
            uid: Int,
            channel: String,
            cdn: String,
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
                    account = it.userAccount,
                    x = totalX,
                    y = totalY,
                    width = fullWidth / channelUids.size,
                    height = fullHeight / channelUids.size
                )
                inputItems.add(item)
                totalX += fullWidth / channelUids.size
            }
            // 旁路推流，默认1080p24fps分辨率。
            return TranscodeSetting(
                enableUserAccount,
                uid,
                channel,
                cdn,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24.value,
                bitRate,
                fullWidth,
                fullHeight,
                inputItems
            )
        }

        /**旁路推流转码1vn*/
        fun liveTranscodingMulti(
            enableUserAccount:Boolean,
            uid: Int,
            channel: String,
            cdn: String,
            channelUids: List<ChannelUid>
        ): TranscodeSetting {
            // 竖屏
            val fullWidth = videoDimensions.height
            val fullHeight = videoDimensions.width
            val inputItems = mutableListOf<TransInputItem>()
            // 分配主播的画面布局。
            val startY = ((fullHeight - fullWidth) * 0.5).toInt()
            val singleSize = fullWidth / 4
            var transcodingX = 0
            var transcodingY = 0
            for (i in 0 until channelUids.size) {
                val videoUid = channelUids[i].uid
                if (videoUid == -1) continue
                transcodingX = singleSize * (i % 4)
                transcodingY = startY + singleSize * (i / 4)
                val item = TransInputItem(
                    channel = channel,
                    uid = videoUid,
                    account = channelUids[i].userAccount,
                    x = transcodingX,
                    y = transcodingY,
                    width = singleSize,
                    height = singleSize
                )
                inputItems.add(item)
            }
            // 旁路推流，默认1080p24fps分辨率。
            return TranscodeSetting(
                enableUserAccount,
                uid,
                channel,
                cdn,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15.value,
                bitRate,
                fullWidth,
                fullHeight,
                inputItems
            )
        }

        /**旁路推流转码1v2*/
        fun liveTranscoding3(
            enableUserAccount:Boolean,
            uid: Int,
            channel: String,
            cdn: String,
            channelUids: List<ChannelUid>
        ): TranscodeSetting {
            // 竖屏
            val fullWidth = videoDimensions.height
            val fullHeight = videoDimensions.width
            val inputItems = mutableListOf<TransInputItem>()
            // 分配主播的画面布局。
            val startY = ((fullHeight - fullWidth) * 0.5).toInt()
            val unitSize = (fullWidth * 0.5).toInt()
            var singleWidth = 0
            var singleHeight = 0
            var transcodingX = 0
            var transcodingY = 0
            for (i in 0 until channelUids.size) {
                val videoUid = channelUids[i].uid
                if (videoUid == -1) continue
                if (i == 0) {
                    singleWidth = unitSize
                    singleHeight = unitSize * 2
                    transcodingX = 0
                    transcodingY = startY
                } else if (i == 1) {
                    singleWidth = unitSize
                    singleHeight = unitSize
                    transcodingX = unitSize
                    transcodingY = startY
                } else if (i == 2) {
                    singleWidth = unitSize
                    singleHeight = unitSize
                    transcodingX = unitSize
                    transcodingY = startY + unitSize
                }
                val item = TransInputItem(
                    channel = channel,
                    uid = videoUid,
                    account = channelUids[i].userAccount,
                    x = transcodingX,
                    y = transcodingY,
                    width = singleWidth,
                    height = singleHeight
                )
                inputItems.add(item)
            }
            // 旁路推流，默认1080p24fps分辨率。
            return TranscodeSetting(
                enableUserAccount,
                uid,
                channel,
                cdn,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15.value,
                bitRate,
                fullWidth,
                fullHeight,
                inputItems
            )
        }

        /**旁路推流转码1v3*/
        fun liveTranscoding4(
            enableUserAccount: Boolean,
            uid: Int,
            channel: String,
            cdn: String,
            channelUids: List<ChannelUid>
        ): TranscodeSetting {
            // 竖屏
            val fullWidth = videoDimensions.height
            val fullHeight = videoDimensions.width
            val inputItems = mutableListOf<TransInputItem>()
            // 分配主播的画面布局。
            val startY = ((fullHeight - fullWidth) * 0.5).toInt()
            val singleSize = fullWidth / 2
            var transcodingX = 0
            var transcodingY = 0
            for (i in 0 until channelUids.size) {
                val videoUid = channelUids[i].uid
                if (videoUid == -1) continue
                transcodingX = singleSize * (i % 2)
                transcodingY = startY + singleSize * (i / 2)
                val item = TransInputItem(
                    channel = channel,
                    uid = videoUid,
                    account = channelUids[i].userAccount,
                    x = transcodingX,
                    y = transcodingY,
                    width = singleSize,
                    height = singleSize
                )
                inputItems.add(item)
            }
            // 旁路推流，默认1080p24fps分辨率。
            return TranscodeSetting(
                enableUserAccount,
                uid,
                channel,
                cdn,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15.value,
                bitRate,
                fullWidth,
                fullHeight,
                inputItems
            )
        }
    }
}