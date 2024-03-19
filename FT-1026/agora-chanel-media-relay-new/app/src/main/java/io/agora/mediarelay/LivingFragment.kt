package io.agora.mediarelay

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.annotation.Size
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.AgoraRtcHelper
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.tools.PermissionHelp
import io.agora.mediarelay.tools.ToastTool
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.video.ChannelMediaInfo
import io.agora.rtc2.video.ChannelMediaRelayConfiguration
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

/**
 * @author create by zhangwei03
 */
class LivingFragment : BaseUiFragment<FragmentLivingBinding>() {
    companion object {
        private const val TAG = "LivingFragment"

        const val KEY_CHANNEL_ID: String = "key_channel_id"
        const val KEY_ROLE: String = "key_role"
    }

    private var permissionHelp: PermissionHelp? = null

    // 跨频道媒体流转发
    @Volatile
    private var mediaRelaying = false

    // 推流状态
    @Volatile
    private var publishedRtmp = false

    private val channelName by lazy { arguments?.getString(KEY_CHANNEL_ID) ?: "" }

    private val role by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_BROADCASTER) ?: Constants.CLIENT_ROLE_BROADCASTER
    }

    private val rtcEngine by lazy { AgoraRtcEngineInstance.rtcEngine }

    private val mediaRelayConfiguration by lazy {
        ChannelMediaRelayConfiguration()
    }

    private var mediaPlayer: IMediaPlayer? = null

    @Volatile
    private var isInPk: Boolean = false

    @Volatile
    private var isCdnAudience: Boolean = true

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var remotePkUid: Int = -1

    private val ownerUid by lazy { channelName.toIntOrNull() ?: 123 }

    private val curUid by lazy {
        KeyCenter.rtcUid(isBroadcast(), channelName)
    }

    private fun runOnMainThread(runnable: Runnable) {
        if (Thread.currentThread() === mainHandler.looper.thread) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLivingBinding {
        return FragmentLivingBinding.inflate(inflater)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            permissionHelp = context.permissionHelp
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initRtcEngine()
    }

    private fun initView() {
        if (isBroadcast()) {
            binding.layoutChannel.isVisible = true
            binding.btSubmitPk.isVisible = true
            binding.btSwitchStream.isVisible = false
            binding.btSwitchCarma.isVisible = true
            binding.btMute.isVisible = true
            binding.btMute.setImageResource(R.drawable.ic_mic_on)
        } else {
            binding.layoutChannel.isVisible = false
            binding.btSubmitPk.isVisible = false
            binding.btSwitchStream.isVisible = true
            binding.btSwitchCarma.isVisible = false
            binding.btMute.isVisible = false
        }
        binding.tvChannelId.text = "ChannelId:$channelName"
        binding.btnBack.setOnClickListener {
            goBack()
        }
        binding.btSubmitPk.setOnClickListener {
            if (isInPk) {
                binding.btSubmitPk.text = getString(R.string.start_pk)
                binding.etPkChannel.setText("")
                remotePkUid = -1
                isInPk = false
                stopChannelMediaRelay()
                updateRtmpStreamEnable(ownerUid)
                updateIdleMode()
            } else {
                val channelId = binding.etPkChannel.text.toString()
                if (checkChannelId(channelId)) return@setOnClickListener
                remotePkUid = channelId.toIntOrNull() ?: -1
                binding.btSubmitPk.text = getString(R.string.stop_pk)
                isInPk = true
                startChannelMediaRelay(remotePkUid.toString())
                val uids = intArrayOf(ownerUid, remotePkUid)
                updateRtmpStreamEnable(*uids)
                updatePkMode(remotePkUid)
            }

            updateVideoEncoder(isInPk)
        }
        binding.btSwitchStream.setOnClickListener {
            if (isCdnAudience) {
                switchRtcAudience()
            } else {
                switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
            }
            isCdnAudience = !isCdnAudience
        }
        binding.btSwitchCarma.setOnClickListener {
            rtcEngine.switchCamera()
        }
        binding.btMute.setOnClickListener {
            if (muteLocalAudio) {
                muteLocalAudio = false
                binding.btMute.setImageResource(R.drawable.ic_mic_on)
                rtcEngine.muteLocalAudioStream(false)
            } else {
                muteLocalAudio = true
                binding.btMute.setImageResource(R.drawable.ic_mic_off)
                rtcEngine.muteLocalAudioStream(true)
            }
        }
    }

    private var muteLocalAudio = false

    private val mediaPlayerObserver = object : MPObserverAdapter() {
        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerError?
        ) {
            super.onPlayerStateChanged(state, error)
            if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                mediaPlayer?.play()
            }
            if (error != io.agora.mediaplayer.Constants.MediaPlayerError.PLAYER_ERROR_NONE) {
                runOnMainThread {
                    ToastTool.showToast("$error")
                }
            }
            if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING) {
                mediaPlayer?.let {
                    val streamCount = it.streamCount
                    Log.d(TAG, "streamInfo: $streamCount")
                    for (i in 0 until streamCount) {
                        Log.d(TAG, "streamInfo: ${it.getStreamInfo(i)}")
                    }
                }
            }
        }
    }

    private fun switchCdnAudience(rtmpPullUrl: String) {
        val act = activity ?: return
        rtcEngine.leaveChannel()
        remotePkUid = -1
        isInPk = false
        binding.btSwitchStream.text = getString(R.string.rtc_audience)
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.videoContainer.isVisible = false

        val textureView = TextureView(act)
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(textureView)
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            registerPlayerObserver(mediaPlayerObserver)
            setView(textureView)
            open(rtmpPullUrl, 0)
        }

    }

    //切换到rtc 观众
    private fun switchRtcAudience() {
        mediaPlayer?.let {
            it.unRegisterPlayerObserver(mediaPlayerObserver)
            it.stop()
            it.destroy()
            mediaPlayer = null
        }

        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.videoContainer.isVisible = false

        initVideoView()
        joinChannel()
    }

    private fun goBack() {
        findNavController().popBackStack()
    }

    private fun checkChannelId(channelId: String): Boolean {
        if (channelId.isEmpty()) {
            ToastTool.showToast("Please enter pk channel id")
            return true
        }
        return false
    }

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = {
            runOnMainThread {
                if (isBroadcast()) {
                    setRtmpStreamEnable(true)
                } else {
                    //超级画质
                    val ret1 = rtcEngine.setParameters("{\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}}")
                    val ret2 = rtcEngine.setParameters("{\"rtc.video.sr_type\":20}")
                    Log.d(TAG, "enable_sr ret：$ret1, sr_type ret：$ret2")
                }
            }
        },
        onUserJoined = {
            runOnMainThread {
                if (isBroadcast()) {
                    // 除了主播外,有其他用户加入就认为是pk
                    if (remotePkUid == -1) {
                        remotePkUid = it
                        isInPk = true
                        updatePkMode(it)
                        startChannelMediaRelay(it.toString())
                        val uids = intArrayOf(ownerUid, it)
                        // 主播合流转cdn
                        updateRtmpStreamEnable(*uids)
                    }
                } else {
                    if (remotePkUid == -1 && channelName != it.toString()) {
                        remotePkUid = it
                        isInPk = true
                        updatePkMode(it)
                    }
                }
            }
        },
        onUserOffline = {
            runOnMainThread {
                if (remotePkUid == it) { // pk 用户离开
                    remotePkUid = -1
                    isInPk = false
                    updateIdleMode()
                    if (isBroadcast()) {
                        stopChannelMediaRelay()
                        updateRtmpStreamEnable(ownerUid)
                    }
                }
            }
        },

        onRtmpStreamingStateChanged = { url, state, code ->
            runOnMainThread {
                when (state) {
                    Constants.RTMP_STREAM_PUBLISH_STATE_RUNNING -> {
                        if (code == Constants.RTMP_STREAM_PUBLISH_ERROR_OK) {
                            ToastTool.showToast("rtmp stream publish state running")
                        }
                    }

                    Constants.RTMP_STREAM_PUBLISH_STATE_FAILURE -> {
                        ToastTool.showToast("rtmp stream publish state failure: $code")
                    }
                }
            }
        },
        onChannelMediaRelayStateChanged = { state, code ->
            //跨频道媒体流转发状态
            //https://docs.agora.io/cn/extension_customer/API%20Reference/java_ng/API/toc_stream_management.html?platform=Android#callback_irtcengineeventhandler_onchannelmediarelaystatechanged
            runOnMainThread {
                when (state) {
                    // 源频道主播成功加入目标频道
                    Constants.RELAY_STATE_RUNNING -> {
                        mediaRelaying = true
                        ToastTool.showToast("relay state running: $code")
                    }
                    // 发生异常，详见 code 中提示的错误信息
                    Constants.RELAY_STATE_FAILURE -> {
                        mediaRelaying = false
                        ToastTool.showToast("relay state failure: $code")
                    }

                    else -> {
                        mediaRelaying = false
                    }
                }
            }
        },
    )

    private fun initRtcEngine() {
        checkRequirePerms {
            AgoraRtcEngineInstance.eventListener = eventListener
            if (isBroadcast()) {
                initVideoView()
                joinChannel()
            } else {
                if (isCdnAudience) {
                    switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
                } else {
                    switchRtcAudience()
                }
            }
        }
    }

    /**推流到CDN*/
    private fun setRtmpStreamEnable(enable: Boolean) {
        if (enable) {
            // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
            val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
            if (publishedRtmp) {
                rtcEngine.stopRtmpStream(KeyCenter.getRtmpPushUrl(channelName))
                publishedRtmp = false
            }
            val result = rtcEngine.startRtmpStreamWithTranscoding(
                pushUrl,
                AgoraRtcHelper.liveTranscoding(ownerUid),
            )
            if (result == Constants.RTMP_STREAM_PUBLISH_ERROR_OK) {
                publishedRtmp = true
            } else {
                ToastTool.showToast("push rtmp stream error:$result！")
            }
        } else {
            // 删除一个推流地址。
            val result = rtcEngine.stopRtmpStream(KeyCenter.getRtmpPushUrl(channelName))
            if (result == Constants.RTMP_STREAM_UNPUBLISH_ERROR_OK) {
                publishedRtmp = false
            } else {
                ToastTool.showToast("stop rtmp stream error！:$result")
            }
        }
    }

    /**新主播进来，更新CDN推流*/
    private fun updateRtmpStreamEnable(@Size(min = 1) vararg uids: Int) {
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val result = rtcEngine.updateRtmpTranscoding(AgoraRtcHelper.liveTranscoding(*uids))
        if (result == Constants.RTMP_STREAM_PUBLISH_ERROR_OK) {
            publishedRtmp = true
        } else {
            ToastTool.showToast("update push rtmp stream error:$result！")
        }
    }

    /**跨频道媒体流转发*/
    private fun startChannelMediaRelay(remoteChannelId: String) {
        // 配置源频道信息，其中 channelName 使用用户填入的源频道名，myUid 需要填为 0
        // 注意 sourceChannelToken 和用户加入源频道时的 Token 不一致，需要用 uid = 0 和源频道名重新生成
        val srcChannelInfo = ChannelMediaInfo(channelName, null, 0)
        mediaRelayConfiguration.setSrcChannelInfo(srcChannelInfo)

        // 配置目标频道信息，其中 destChannelName 使用用户填入的目标频道名，myUid 填入用户在目标频道内的用户名
        val destChannelInfo = ChannelMediaInfo(remoteChannelId, null, ownerUid)
        mediaRelayConfiguration.setDestChannelInfo(remoteChannelId, destChannelInfo)
        // 调用 startChannelMediaRelay 开始跨频道媒体流转发
        val result = rtcEngine.startChannelMediaRelay(mediaRelayConfiguration)
        if (result == Constants.ERR_OK) {
        } else {
            ToastTool.showToast("channel media relay error:$result！")
        }
    }

    private fun stopChannelMediaRelay() {
        rtcEngine.stopChannelMediaRelay()
    }

    /**pk 模式,*/
    private fun updatePkMode(remotePkUid: Int) {
        val act = activity ?: return
        binding.videoPKLayout.videoContainer.isVisible = true
        binding.layoutVideoContainer.isVisible = false
        binding.btSubmitPk.text = getString(R.string.stop_pk)
        if (isBroadcast()) { // 主播
            val localTexture = TextureView(act)
            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(localTexture)
            rtcEngine.setupLocalVideo(
                VideoCanvas(
                    localTexture,
                    Constants.RENDER_MODE_ADAPTIVE,
                    Constants.VIDEO_MIRROR_MODE_ENABLED,
                    curUid
                )
            )

            val remoteTexture = TextureView(act)
            binding.videoPKLayout.iBroadcasterBView.removeAllViews()
            binding.videoPKLayout.iBroadcasterBView.addView(remoteTexture)
            rtcEngine.setupRemoteVideo(
                VideoCanvas(
                    remoteTexture,
                    Constants.RENDER_MODE_FIT,
                    remotePkUid
                )
            )
        } else {
            if (!isCdnAudience) {  // rtc 观众
                val remoteTextureA = TextureView(act)
                binding.videoPKLayout.iBroadcasterAView.removeAllViews()
                binding.videoPKLayout.iBroadcasterAView.addView(remoteTextureA)
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(
                        remoteTextureA,
                        Constants.RENDER_MODE_FIT,
                        ownerUid
                    )
                )
                val remoteTextureB = TextureView(act)
                binding.videoPKLayout.iBroadcasterBView.removeAllViews()
                binding.videoPKLayout.iBroadcasterBView.addView(remoteTextureB)
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(
                        remoteTextureB,
                        Constants.RENDER_MODE_FIT,
                        remotePkUid
                    )
                )
            }
        }
    }

    /**单主播模式*/
    private fun updateIdleMode() {
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.layoutVideoContainer.isVisible = true
        binding.btSubmitPk.text = getString(R.string.start_pk)
        initVideoView()
        updateVideoEncoder(isInPk)
    }

    private fun initVideoView() {
        val act = activity ?: return
        if (isBroadcast()) {
            val localTexture = TextureView(act)
            binding.layoutVideoContainer.removeAllViews()
            binding.layoutVideoContainer.addView(localTexture)
            rtcEngine.setupLocalVideo(
                VideoCanvas(
                    localTexture,
                    Constants.RENDER_MODE_FIT,
                    Constants.VIDEO_MIRROR_MODE_ENABLED,
                    curUid
                )
            )
        } else {
            val remoteTexture = TextureView(act)
            binding.layoutVideoContainer.removeAllViews()
            binding.layoutVideoContainer.addView(remoteTexture)
            rtcEngine.setupRemoteVideo(
                VideoCanvas(
                    remoteTexture,
                    Constants.RENDER_MODE_FIT,
                    ownerUid
                )
            )
        }
    }

    private fun joinChannel() {
        val channelMediaOptions = ChannelMediaOptions()
        channelMediaOptions.clientRoleType = role
        channelMediaOptions.autoSubscribeVideo = true
        channelMediaOptions.autoSubscribeAudio = true
        channelMediaOptions.publishCameraTrack = isBroadcast()
        channelMediaOptions.publishMicrophoneTrack = isBroadcast()
        if (isBroadcast()) {
            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
//            rtcEngine.enableInstantMediaRendering() // 加速出图(加入频道前调用)
//            rtcEngine.setCameraAutoFocusFaceModeEnabled(true) // 开启人脸自动对焦(加入频道前调用)
//            rtcEngine.setAudioProfile(Constants.AUDIO_PROFILE_DEFAULT) // audio profile ：default
//            rtcEngine.setAudioScenario(Constants.AUDIO_SCENARIO_DEFAULT) // scenario ：default
            updateVideoEncoder(false)
        } else {
//            rtcEngine.setParameters("\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}")
//            rtcEngine.setParameters("\"rtc.video.sr_type\":20")
        }
//        rtcEngine.setParameters("{\"engine.video.enable_hw_encoder\":\"true\"}") // 硬编 （加入频道前调用）
//        rtcEngine.setParameters("{\"rtc.enable_early_data_for_vos\":\"false\"}") // 针对弱网链接优化（加入频道前调用）
        val ret1 = rtcEngine.setParameters("{\"engine.video.enable_hw_decoder\":\"true\"}")
        val ret2 = rtcEngine.setParameters("{\"engine.video.decoder_out_byte_frame\":\"true\"}")

        val score = rtcEngine.queryDeviceScore()
        Log.d(TAG, "queryDeviceScore $score")
        // 265
        rtcEngine.setParameters("{\"che.video.videoCodecIndex\":2}")
        rtcEngine.setDefaultAudioRoutetoSpeakerphone(true)
        rtcEngine.joinChannel(null, channelName, curUid, channelMediaOptions)
    }

    private fun updateVideoEncoder(isInPk: Boolean) {
        if (isInPk) {
            val videoEncoderConfiguration = VideoEncoderConfiguration().apply {
                dimensions = VideoEncoderConfiguration.VD_1280x720
                frameRate = VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24.value
                mirrorMode = VideoEncoderConfiguration.MIRROR_MODE_TYPE.MIRROR_MODE_ENABLED
            }
            val ret =
                rtcEngine.setParameters("{\"che.video.auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":0}}")
            Log.d(TAG, "auto_adjust_resolution close $ret")
            rtcEngine.setVideoEncoderConfiguration(videoEncoderConfiguration)
        } else {
            if (RtcSettings.mVideoDimensionsAuto) {
                val ret =
                    rtcEngine.setParameters(
                        "{\"che.video" +
                                ".auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":1,\"resolution_list\":\"1920x1080, 1280x720\", \"resolution_score\":\"90, 1\"}}"
                    )
                rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
                Log.d(TAG, "auto_adjust_resolution $ret")
            } else {
                val ret =
                    rtcEngine.setParameters("{\"che.video.auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":0}}")
                Log.d(TAG, "auto_adjust_resolution close $ret")
                rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
            }
        }
    }

    private fun isBroadcast(): Boolean {
        return role == Constants.CLIENT_ROLE_BROADCASTER
    }

    private fun checkRequirePerms(
        force: Boolean = false,
        denied: (() -> Unit)? = null,
        granted: () -> Unit
    ) {
        if (role == Constants.CLIENT_ROLE_AUDIENCE) {
            granted.invoke()
            return
        }
        permissionHelp?.checkCameraAndMicPerms(
            granted = { granted.invoke() },
            unGranted = { denied?.invoke() },
            force = force
        )
    }

    override fun onResume() {
        activity?.let {
            val insetsController = WindowCompat.getInsetsController(it.window, it.window.decorView)
            insetsController.isAppearanceLightStatusBars = false
        }
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        if (isBroadcast()) {
            setRtmpStreamEnable(false)
        } else {
            mediaPlayer?.apply {
                unRegisterPlayerObserver(mediaPlayerObserver)
                stop()
                destroy()
                mediaPlayer = null
            }
        }
        rtcEngine.leaveChannel()
        AgoraRtcEngineInstance.destroy()
        super.onDestroy()
    }
}