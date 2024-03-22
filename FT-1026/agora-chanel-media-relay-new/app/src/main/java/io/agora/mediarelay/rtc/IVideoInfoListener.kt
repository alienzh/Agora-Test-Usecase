package io.agora.mediarelay.rtc

import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler

interface IVideoInfoListener {

    fun onLocalVideoStats(
        source: Constants.VideoSourceType?,
        stats: IRtcEngineEventHandler.LocalVideoStats
    ) {}

    fun onRemoteVideoStats(stats: IRtcEngineEventHandler.RemoteVideoStats) {}

    fun onUplinkNetworkInfoUpdated(info: IRtcEngineEventHandler.UplinkNetworkInfo?) {}

    fun onDownlinkNetworkInfoUpdated(info: IRtcEngineEventHandler.DownlinkNetworkInfo?) {}
}