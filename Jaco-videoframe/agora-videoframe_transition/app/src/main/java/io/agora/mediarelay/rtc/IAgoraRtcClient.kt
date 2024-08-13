package io.agora.mediarelay.rtc

import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.ClientRoleOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.VideoLayoutInfo
import io.agora.rtc2.RtcConnection
import io.agora.rtc2.UserInfo

interface IAgoraRtcClient {

    data class IChannelEventListener constructor(
        var onChannelJoined: ((channel: String, uid: Int) -> Unit)? = null,
        var onLeaveChannel: (() -> Unit)? = null,
        var onUserJoined: ((uid: Int) -> Unit)? = null,
        var onUserOffline: ((uid: Int) -> Unit)? = null,
        var onStreamMessage: ((uid: Int, streamId: Int,data:ByteArray) -> Unit)? = null,
        var onTranscodedStreamLayoutInfo: ((uid: Int, videoLayoutInfo: VideoLayoutInfo?) -> Unit)? = null,
    )

    data class VideoCanvasContainer constructor(
        val lifecycleOwner: LifecycleOwner,
        val container: ViewGroup,
        val uid: Int,
        val viewIndex: Int = 0,
        val renderMode: Int = Constants.RENDER_MODE_HIDDEN,
    )

    /**
     * setup remote video
     */
    fun setupRemoteVideo(connection: RtcConnection, container: VideoCanvasContainer)

    /**
     * setup local video
     */
    fun setupLocalVideo(container: VideoCanvasContainer)
}