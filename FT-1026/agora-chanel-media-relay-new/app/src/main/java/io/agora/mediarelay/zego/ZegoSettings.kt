package io.agora.mediarelay.zego

import im.zego.zegoexpress.constants.ZegoVideoConfigPreset
import im.zego.zegoexpress.entity.ZegoVideoConfig
import io.agora.mediarelay.VideoSetting
import io.agora.mediarelay.tools.LogTool

object ZegoSettings : VideoSetting {

    /**
     * 编码配置
     */
    var mVideoConfigpreset: ZegoVideoConfigPreset = ZegoVideoConfigPreset.PRESET_1080P
        set(newValue) {
            field = newValue
            ZegoEngineInstance.zVideoConfig = ZegoVideoConfig(mVideoConfigpreset)
            LogTool.d("ZegoSettings mVideoConfigpreset：$newValue")
        }

    /**
     * 编码帧率
     */
    var mFrameRate: Int = 24
        set(newValue) {
            field = newValue
            ZegoEngineInstance.zVideoConfig.fps = newValue
            LogTool.d("RtcSetting FrameRate：$newValue")
        }

    /**
     * 编码码率
     */
    var mBitRate: Int = 0
        set(newValue) {
            field = newValue
            ZegoEngineInstance.zVideoConfig.bitrate = newValue
            LogTool.d("RtcSetting BitRate：$newValue")
        }
}