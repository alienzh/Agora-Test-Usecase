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
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.SeiHelper
import io.agora.mediarelay.rtc.transcoder.TranscodeSetting
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.GsonTools
import io.agora.mediarelay.tools.PermissionHelp
import io.agora.mediarelay.tools.ThreadTool
import io.agora.mediarelay.tools.ToastTool
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.mediarelay.widget.PopAdapter.OnItemClickListener
import io.agora.mediarelay.tools.ViewTool
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IMetadataObserver
import io.agora.rtc2.video.ChannelMediaInfo
import io.agora.rtc2.video.ChannelMediaRelayConfiguration
import io.agora.rtc2.video.ImageTrackOptions
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import java.nio.charset.Charset
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


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

    /**
     * cdn 链接 码率 index
     * 0 720p, 1 1080p
     * [KeyCenter.mBitrateList]
     */
    private var cdnPosition = 0

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
            binding.btMuteMic.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.isVisible = true
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            binding.btBitrate.isVisible = false
        } else {
            binding.layoutChannel.isVisible = false
            binding.btSubmitPk.isVisible = false
            binding.btSwitchStream.isVisible = true
            binding.btSwitchCarma.isVisible = false
            binding.btMuteMic.isVisible = false
            binding.btMuteCarma.isVisible = false
            binding.btBitrate.isVisible = true
            binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
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

            updateVideoEncoder()
        }
        binding.btSwitchStream.setOnClickListener {
            if (isCdnAudience) {
                switchRtcAudience()
            } else {
                switchCdnAudience(cdnPosition)
            }
            isCdnAudience = !isCdnAudience
        }
        binding.btSwitchCarma.setOnClickListener {
            rtcEngine.switchCamera()
        }
        binding.btMuteMic.setOnClickListener {
            if (muteLocalAudio) {
                muteLocalAudio = false
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                rtcEngine.muteLocalAudioStream(false)
            } else {
                muteLocalAudio = true
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                rtcEngine.muteLocalAudioStream(true)
            }
        }
        binding.btMuteCarma.setOnClickListener {
            if (muteLocalVideo) {
                muteLocalVideo = false
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                rtcEngine.muteLocalVideoStream(false)
                val imageTrackOptions = ImageTrackOptions(FileUtils.blackImage, 15)
                rtcEngine.enableVideoImageSource(false, imageTrackOptions)
            } else {
                muteLocalVideo = true
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                rtcEngine.muteLocalVideoStream(true)
                val imageTrackOptions = ImageTrackOptions(FileUtils.blackImage, 15)
                rtcEngine.enableVideoImageSource(true, imageTrackOptions)
            }
        }
        val dashboardFragment = DashboardFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.fl_dashboard, dashboardFragment)
            .commit()
        binding.btDashboard.setOnClickListener {
            it.isSelected = !it.isSelected
            dashboardFragment.setOn(it.isSelected)
            binding.flDashboard.isVisible = it.isSelected
        }
        binding.btBitrate.setOnClickListener {
            val cxt = context ?: return@setOnClickListener
            val data = KeyCenter.mBitrateList
            ViewTool.showPop(cxt, binding.btBitrate, data, cdnPosition) { position, text ->
                tempCdnPosition = position
                val pullUrl = KeyCenter.getRtmpPullUrl(channelName, position)
                Log.d(TAG, "switchSrc $pullUrl")
                mediaPlayer?.switchSrc(pullUrl, true)
            }
        }
    }

    // 临时变量，切换成功则修改
    private var tempCdnPosition = -1

    private fun switchSrcSuccess(ret: Boolean) {
        runOnMainThread {
            if (ret) {
                cdnPosition = tempCdnPosition
                tempCdnPosition = -1
                if (cdnPosition >= 0 && cdnPosition < KeyCenter.mBitrateList.size) {
                    binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
                }
                ToastTool.showToast(R.string.switch_src_success)
            } else {
                ToastTool.showToast(R.string.switch_src_failed)
            }
        }
    }

    private var muteLocalAudio = false
    private var muteLocalVideo = false

    private val mediaPlayerObserver = object : MPObserverAdapter() {
        override fun onPlayerEvent(
            eventCode: io.agora.mediaplayer.Constants.MediaPlayerEvent?,
            elapsedTime: Long,
            message: String?
        ) {
            super.onPlayerEvent(eventCode, elapsedTime, message)
            Log.d(TAG, "onPlayerEvent: $eventCode，$elapsedTime,$message")
            when (eventCode) {
                io.agora.mediaplayer.Constants.MediaPlayerEvent.PLAYER_EVENT_SWITCH_COMPLETE -> {
                    switchSrcSuccess(true)
                }

                io.agora.mediaplayer.Constants.MediaPlayerEvent.PLAYER_EVENT_SWITCH_ERROR -> {
                    switchSrcSuccess(false)
                    Log.d(TAG, "getPlaySrc:${mediaPlayer?.playSrc}")
                }

                else -> {}
            }
        }

        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerReason?
        ) {
            super.onPlayerStateChanged(state, error)
            if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                mediaPlayer?.play()
            }
            if (error != io.agora.mediaplayer.Constants.MediaPlayerReason.PLAYER_REASON_NONE) {
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

        override fun onMetaData(type: io.agora.mediaplayer.Constants.MediaPlayerMetadataType?, data: ByteArray?) {
            super.onMetaData(type, data)
            data?.let {
                Log.d(TAG, "onMetaData type:$type,data:${String(it)}")
            }
        }
    }

    private fun switchCdnAudience(cdnPosition: Int) {
        val act = activity ?: return
        val rtmpPullUrl = KeyCenter.getRtmpPullUrl(channelName, cdnPosition)
        binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        rtcEngine.leaveChannel()
        remotePkUid = -1
        isInPk = false
        binding.btSwitchStream.text = getString(R.string.rtc_audience)
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.btBitrate.isVisible = true

        val textureView = TextureView(act)
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(textureView)
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            setPlayerOption("is_live_source", 1);
            setPlayerOption("play_speed_down_cache_duration", 0)
            setPlayerOption("open_timeout_until_success", 6000)
            setPlayerOption("enable_search_metadata", 1)
            registerPlayerObserver(mediaPlayerObserver)
            setView(textureView)
            Log.d(TAG, "rtmpPullUrl $rtmpPullUrl")
            open(rtmpPullUrl, 0)
        }

    }

    //切换到rtc 观众
    private fun switchRtcAudience() {
        mediaPlayer?.let {
            it.unRegisterPlayerObserver(mediaPlayerObserver)
            it.stop()
            it.setView(null)
            it.destroy()
            mediaPlayer = null
        }

        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.btBitrate.isVisible = false

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
                    startSendSei()
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
                        updateVideoEncoder()
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
                        updateVideoEncoder()
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
                    updateVideoEncoder()
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
                        if (code == Constants.RTMP_STREAM_PUBLISH_REASON_OK) {
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
                    switchCdnAudience(cdnPosition)
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
                AgoraRtcEngineInstance.transcoder.stopRtmpStream(null)
                publishedRtmp = false
            }
            AgoraRtcEngineInstance.transcoder.startRtmpStreamWithTranscoding(
                TranscodeSetting.liveTranscoding(
                    channelName,
                    pushUrl,
                    ownerUid
                )
            ) { succeed ->
                if (succeed) {
                    publishedRtmp = true
                    ToastTool.showToast("rtmp stream publish state running")
                } else {
                    ToastTool.showToast("push rtmp stream error ！")
                }
            }
        } else {
            // 删除一个推流地址。
            AgoraRtcEngineInstance.transcoder.stopRtmpStream { succeed ->
                if (succeed) {
                    publishedRtmp = false
                } else {
                    ToastTool.showToast("stop rtmp stream error！")
                }
            }
        }
    }

    /**新主播进来，更新CDN推流*/
    private fun updateRtmpStreamEnable(@Size(min = 1) vararg uids: Int) {
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
        AgoraRtcEngineInstance.transcoder.updateRtmpTranscoding(
            TranscodeSetting.liveTranscoding(
                channelName,
                pushUrl,
                *uids
            )
        ) { succeed ->
            if (succeed) {
                publishedRtmp = true
            } else {
                ToastTool.showToast("update rtmp stream error ！")
            }
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
        val result = rtcEngine.startOrUpdateChannelMediaRelay(mediaRelayConfiguration)
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
            updateVideoEncoder()
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
        val code: Int = rtcEngine.registerMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "registerMediaMetadataObserver code:$code")
        rtcEngine.joinChannel(null, channelName, curUid, channelMediaOptions)
    }

    private fun updateVideoEncoder() {
        if (isInPk && RtcSettings.mVideoDimensions == VideoEncoderConfiguration.VD_1920x1080) {
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
        val code: Int = rtcEngine.unregisterMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "unregisterMediaMetadataObserver code:$code")
        if (isBroadcast()) {
            stopSendSei()
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

    private fun sendMetaSei() {
        val map = SeiHelper.buildSei(channelName, curUid)
        val jsonString = GsonTools.beanToString(map) ?: return
        metadata = jsonString.toByteArray()
    }

    // 开始发送 sei
    private var mStopSei = true
    private var seiFuture: ScheduledFuture<*>? = null
    private val seiTask = object : Runnable {
        override fun run() {
            if (mStopSei) return
            if (isBroadcast()) sendMetaSei()
        }
    }

    private fun startSendSei() {
        mStopSei = false
        seiFuture = ThreadTool.scheduledThreadPool.scheduleAtFixedRate(seiTask, 0, 1, TimeUnit.SECONDS)
    }

    // 停止播放歌词
    private fun stopSendSei() {
        mStopSei = true
        seiFuture?.cancel(true)
        seiFuture = null
        if (ThreadTool.scheduledThreadPool is ScheduledThreadPoolExecutor) {
            ThreadTool.scheduledThreadPool.remove(seiTask)
        }
    }

    private var metadata: ByteArray? = null

    private val iMetadataObserver: IMetadataObserver = object : IMetadataObserver {
        /**Returns the maximum data size of Metadata */
        override fun getMaxMetadataSize(): Int {
            return KeyCenter.MAX_META_SIZE
        }

        /**Occurs when the SDK is ready to receive and send metadata.
         * You need to specify the metadata in the return value of this callback.
         * @param timeStampMs The timestamp (ms) of the current metadata.
         * @return The metadata that you want to send in the format of byte[]. Ensure that you set the return value.
         * PS: Ensure that the size of the metadata does not exceed the value set in the getMaxMetadataSize callback.
         */
        override fun onReadyToSendMetadata(timeStampMs: Long, sourceType: Int): ByteArray? {
            /**Check if the metadata is empty. */

            if (metadata == null) {
                return null
            }
            Log.i(TAG, "There is metadata to send!")
            /**Recycle metadata objects. */
            val toBeSend: ByteArray = metadata!!
            metadata = null
            if (toBeSend.size > KeyCenter.MAX_META_SIZE) {
                Log.e(TAG, String.format("Metadata exceeding max length %d!", KeyCenter.MAX_META_SIZE))
                return null
            }
            val data = String(toBeSend, Charset.forName("UTF-8"))
            Log.i(TAG, String.format("Metadata sent successfully! The content is %s", data))
            return toBeSend
        }

        /**Occurs when the local user receives the metadata.
         * @param buffer The received metadata.
         * @param uid The ID of the user who sent the metadata.
         * @param timeStampMs The timestamp (ms) of the received metadata.
         */
        override fun onMetadataReceived(buffer: ByteArray, uid: Int, timeStampMs: Long) {
            val data = String(buffer, Charset.forName("UTF-8"))
            Log.i(TAG, "onMetadataReceived:$data")
        }
    }


}