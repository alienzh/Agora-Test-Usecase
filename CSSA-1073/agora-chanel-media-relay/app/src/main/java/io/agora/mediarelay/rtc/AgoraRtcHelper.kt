package io.agora.mediarelay.rtc

import androidx.annotation.Size
import io.agora.mediarelay.tools.LogTool
import io.agora.rtc2.live.LiveTranscoding
import io.agora.rtc2.video.VideoEncoderConfiguration

object AgoraRtcHelper {

    /**旁路推流转码*/
    fun liveTranscoding(@Size(min = 1) vararg uids: Int): LiveTranscoding {
        val config = LiveTranscoding()
        when (uids.size) {
            1 -> {
                config.width = VideoEncoderConfiguration.VD_960x540.height
                config.height = VideoEncoderConfiguration.VD_960x540.width
            }
            2 -> {
                config.width = VideoEncoderConfiguration.VD_640x360.height * 2
                config.height = VideoEncoderConfiguration.VD_640x360.width
            }
            else -> {
                LogTool.e("liveTranscoding error! uids size:${uids.size}")
            }
        }

        // 分配主播的画面布局。
        var totalX = 0
        var totalY = 0
        uids.forEach {
            val user: LiveTranscoding.TranscodingUser = LiveTranscoding.TranscodingUser()
            user.uid = it
            user.x = totalX
            user.y = totalY
            user.audioChannel = 0
            if (user.x == 0) {
                user.width = VideoEncoderConfiguration.VD_960x540.width
                user.height = VideoEncoderConfiguration.VD_960x540.height
            } else {
                user.width = VideoEncoderConfiguration.VD_640x360.width
                user.height = VideoEncoderConfiguration.VD_640x360.height
            }
            user.width = config.width / uids.size
            user.height = config.height
            totalX += config.width / uids.size
            config.addUser(user)
        }
        return config
    }
}