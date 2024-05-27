package io.agora.mediarelay.rtc

import io.agora.mediarelay.tools.LogTool
import io.agora.rtc2.video.VideoEncoderConfiguration

object RtcSettings {

    var mVideoDimensionsAuto = false
        set(newValue) {
            field = newValue
            LogTool.d("RtcSetting mVideoDimensionsAuto：$newValue")
        }

    /**
     * 编码分辨率
     */
    var mVideoDimensions: VideoEncoderConfiguration.VideoDimensions = VideoEncoderConfiguration.VD_1920x1080
        set(newValue) {
            field = newValue
            AgoraRtcEngineInstance.videoEncoderConfiguration.dimensions = newValue
            LogTool.d("RtcSetting VideoDimensions：$newValue")
        }

    /**
     * 编码帧率
     */
    var mFrameRate: Int = 24
        set(newValue) {
            field = newValue
            AgoraRtcEngineInstance.videoEncoderConfiguration.frameRate = newValue
            LogTool.d("RtcSetting FrameRate：$newValue")
        }

    /**
     * 编码码率
     */
    var mBitRate: Int = 0
        set(newValue) {
            field = newValue
            AgoraRtcEngineInstance.videoEncoderConfiguration.bitrate = newValue
            LogTool.d("RtcSetting BitRate：$newValue")
        }

    var mEnableUserAccount: Boolean = false
        set(newValue) {
            field = newValue
            LogTool.d("RtcSetting mEnableUserAccount：$newValue")
        }

    var mEnableQuic: Boolean = false
        set(newValue) {
            field = newValue
            LogTool.d("RtcSetting mEnableQuic：$newValue")
        }

    var mSwitchSrcTimeout: Int = 20
        set(newValue) {
            if (newValue > 0) {
                field = newValue
                LogTool.d("RtcSetting mSwitchSrcTimeout：$newValue")
            }
        }

    fun reset() {
        mVideoDimensionsAuto = false
        mVideoDimensions = VideoEncoderConfiguration.VD_1920x1080
        mFrameRate = 24
        mBitRate = 0
    }
}
