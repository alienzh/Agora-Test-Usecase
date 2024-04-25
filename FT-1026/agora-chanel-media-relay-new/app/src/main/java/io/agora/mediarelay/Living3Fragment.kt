package io.agora.mediarelay

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseIntArray
import android.view.*
import androidx.core.util.forEach
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLiving3Binding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.SeiHelper
import io.agora.mediarelay.rtc.transcoder.TranscodeSetting
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.GsonTools
import io.agora.mediarelay.tools.PermissionHelp
import io.agora.mediarelay.tools.ThreadTool
import io.agora.mediarelay.tools.ToastTool
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.mediarelay.widget.OnFastClickListener
import io.agora.mediarelay.widget.PopAdapter
import io.agora.mediarelay.tools.ViewTool
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IMetadataObserver
import io.agora.rtc2.video.ImageTrackOptions
import io.agora.rtc2.video.VideoCanvas
import java.nio.charset.Charset
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * @author create by zhangwei03
 */
class Living3Fragment : BaseUiFragment<FragmentLiving3Binding>() {
    companion object {
        private const val TAG = "Living3Fragment"

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

    private val channelMediaOptions by lazy {
        ChannelMediaOptions()
    }

    // 默认进来的角色，第一个进来的主播是房主
    private val role by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_BROADCASTER) ?: Constants.CLIENT_ROLE_BROADCASTER
    }

    private val rtcEngine by lazy { AgoraRtcEngineInstance.rtcEngine }

    private var mediaPlayer: IMediaPlayer? = null

    /**
     * cdn 链接 码率 index
     * 0 720p, 1 1080p
     * [KeyCenter.mBitrateList]
     */
    private var cdnPosition = 0

    @Volatile
    private var audienceStatus: AudienceStatus = AudienceStatus.CDN_Audience

    private val ownerUid by lazy { channelName.toIntOrNull() ?: 123 }

    private val curUid by lazy {
        KeyCenter.rtcUid(role == Constants.CLIENT_ROLE_BROADCASTER, channelName)
    }

    // 房主
    private val isOwner: Boolean
        get() {
            return role == Constants.CLIENT_ROLE_BROADCASTER
        }

    private val mVideoList: SparseIntArray = SparseIntArray()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLiving3Binding {
        return FragmentLiving3Binding.inflate(inflater)
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
        if (isOwner) {
            binding.btLinking.isVisible = false
            binding.btSwitchStream.isVisible = false
            binding.btMuteMic.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.isVisible = true
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
            binding.videosLayout.videoContainer.isVisible = true
            binding.layoutCdnContainer.isVisible = false
        } else {
            binding.btLinking.isVisible = true
            binding.btSwitchStream.isVisible = true
            binding.btMuteMic.isVisible = false
            binding.btMuteCarma.isVisible = false
            // 默认 cdn 观众
            binding.videosLayout.videoContainer.isVisible = false
            binding.layoutCdnContainer.isVisible = true
            binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        }
        binding.tvChannelId.text = "ChannelId:$channelName"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 观众连麦
        binding.btLinking.setOnClickListener(object : OnFastClickListener() {
            override fun onClickJacking(view: View) {
                ToastTool.showToast(R.string.changing_roles)
                when (audienceStatus) {
                    AudienceStatus.CDN_Audience -> { // cdn 观众--> rtc 主播
                        audienceStatus = AudienceStatus.RTC_Broadcaster
                        switchRtc(Constants.CLIENT_ROLE_BROADCASTER)
                        binding.btMuteMic.isVisible = true
                        binding.btMuteCarma.isVisible = true
                        binding.btSwitchStream.isVisible = false
                        binding.btLinking.text = getString(R.string.hang_up)
                        rtcEngine.setEnableSpeakerphone(false)
                        rtcEngine.setEnableSpeakerphone(true)
                    }

                    AudienceStatus.RTC_Audience -> { // rtc 观众--> rtc 主播
                        audienceStatus = AudienceStatus.RTC_Broadcaster
                        channelMediaOptions.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                        channelMediaOptions.publishCameraTrack = true
                        channelMediaOptions.publishMicrophoneTrack = true
                        val ret = rtcEngine.updateChannelMediaOptions(channelMediaOptions)
                        Log.d("alien", "rtc 观众--> rtc 主播 ret:$ret")
                        binding.btMuteMic.isVisible = true
                        binding.btMuteCarma.isVisible = true
                        binding.btSwitchStream.isVisible = false
                        binding.btLinking.text = getString(R.string.hang_up)
                        rtcEngine.setEnableSpeakerphone(false)
                        rtcEngine.setEnableSpeakerphone(true)
                    }

                    AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> rtc 观众
                        audienceStatus = AudienceStatus.RTC_Audience
                        channelMediaOptions.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
                        channelMediaOptions.publishCameraTrack = false
                        channelMediaOptions.publishMicrophoneTrack = false
                        val ret = rtcEngine.updateChannelMediaOptions(channelMediaOptions)
                        Log.d("alien", "rtc 主播--> rtc 观众 ret:$ret")
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }
                }
            }
        })
        binding.btSwitchStream.setOnClickListener(object :
            OnFastClickListener(1000, getString(R.string.click_too_fast)) {
            override fun onClickJacking(view: View) {
                when (audienceStatus) {
                    AudienceStatus.CDN_Audience -> { // cdn 观众--> rtc 观众
                        audienceStatus = AudienceStatus.RTC_Audience
                        switchRtc(Constants.CLIENT_ROLE_AUDIENCE)
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Audience -> { // rtc 观众--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(cdnPosition)
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(cdnPosition)
                        binding.btMuteMic.isVisible = false
                        binding.btMuteCarma.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }
                }
            }
        })
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
            if (muteLocalAudio) {
                muteLocalAudio = false
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                rtcEngine.muteLocalVideoStream(false)
                val imageTrackOptions = ImageTrackOptions(FileUtils.blackImage, 15)
                rtcEngine.enableVideoImageSource(false, imageTrackOptions)
            } else {
                muteLocalAudio = true
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
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
                mediaPlayer?.switchSrc(KeyCenter.getRtmpPullUrl(channelName, position), true)
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
                }

                else -> {}
            }
        }

        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerReason?
        ) {
            super.onPlayerStateChanged(state, error)
            Log.d(TAG, "onPlayerStateChanged: $state，$error")
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
    }

    private fun switchCdnAudience(cdnPosition: Int) {
        val act = activity ?: return
        val rtmpPullUrl = KeyCenter.getRtmpPullUrl(channelName, cdnPosition)
        binding.btBitrate.text = KeyCenter.mBitrateList[cdnPosition]
        rtcEngine.leaveChannel()
        mVideoList.forEach { key, value ->
            if (value == curUid) {
                rtcEngine.setupLocalVideo(null);
            } else {
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(null, Constants.RENDER_MODE_FIT, Constants.VIDEO_MIRROR_MODE_DISABLED, value)
                )
            }
        }

        binding.btSwitchStream.text = getString(R.string.rtc_audience)

        val textureView = TextureView(act)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.addView(textureView)
        binding.layoutCdnContainer.isVisible = true
        binding.videosLayout.videoContainer.isVisible = false
        binding.btBitrate.isVisible = true
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            setPlayerOption("is_live_source", 1);
            setPlayerOption("play_speed_down_cache_duration", 0)
            setPlayerOption("open_timeout_until_success", 6000)
            registerPlayerObserver(mediaPlayerObserver)
            setView(textureView)
            open(rtmpPullUrl, 0)
        }
    }

    //切换到rtc 观众
    private fun switchRtc(role: Int) {
        mediaPlayer?.let {
            it.unRegisterPlayerObserver(mediaPlayerObserver)
            it.stop()
            it.setView(null)
            it.destroy()
            mediaPlayer = null
        }
        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.isVisible = false
        binding.videosLayout.videoContainer.isVisible = true
        binding.btBitrate.isVisible = false
        joinChannel(role)
    }

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = {
            runOnMainThread {
                if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                    startSendSei()
                    if (muteLocalAudio) {
                        binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                        rtcEngine.muteLocalAudioStream(true)
                    } else {
                        binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                        rtcEngine.muteLocalAudioStream(false)
                    }
                    if (muteLocalVideo) {
                        binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                        rtcEngine.muteLocalVideoStream(true)
                    } else {
                        binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                        rtcEngine.muteLocalVideoStream(false)
                    }
                    val existIndex = mVideoList.indexOfValue(curUid)
                    if (existIndex != -1) return@runOnMainThread
                    val emptyIndex = fetchValidIndex(curUid)
                    if (emptyIndex == -1) return@runOnMainThread
                    mVideoList.put(emptyIndex, curUid)
                    notifyItemChanged(emptyIndex)
                }
                if (isOwner) {
                    startSendSei()
                    setRtmpStreamEnable(true)
                } else {
                    //超级画质
                    if (audienceStatus == AudienceStatus.RTC_Audience) {
                        val ret1 = rtcEngine.setParameters("{\"rtc.video.enable_sr\":{\"enabled\":true, \"mode\": 2}}")
                        val ret2 = rtcEngine.setParameters("{\"rtc.video.sr_type\":20}")
                        Log.d(TAG, "enable_sr ret：$ret1, sr_type ret：$ret2")
                    }
                }
            }
        },
        onLeaveChannel = {
            runOnMainThread {
                if (!isOwner) { // 非房主加入空位置
                    mVideoList.forEach(action = { key, value ->
                        mVideoList.put(key, -1)
                    })
                    notifyDataSetChanged()
                }
            }
        },
        onUserJoined = { uid ->
            runOnMainThread {
                val existIndex = mVideoList.indexOfValue(uid)
                if (existIndex != -1) return@runOnMainThread
                val emptyIndex = fetchValidIndex(uid)
                if (emptyIndex == -1) return@runOnMainThread
                mVideoList.put(emptyIndex, uid)
                notifyItemChanged(emptyIndex)
                if (isOwner) {
                    updateRtmpStreamEnable()
                }
            }
        },
        onUserOffline = {
            runOnMainThread {
                val index = mVideoList.indexOfValue(it)
                if (index == -1) return@runOnMainThread
                mVideoList.put(index, -1)
                notifyItemChanged(index)
                if (isOwner) {
                    updateRtmpStreamEnable()
                }
            }
        },

        onClientRoleChanged = { oldRole, newRole, newRoleOptions ->
            // 忽略房主
            if (isOwner) return@IChannelEventListener
            runOnMainThread {
                if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                    startSendSei()
                    if (muteLocalAudio) {
                        binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                        rtcEngine.muteLocalAudioStream(true)
                    } else {
                        binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                        rtcEngine.muteLocalAudioStream(false)
                    }
                    if (muteLocalVideo) {
                        binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                        rtcEngine.muteLocalVideoStream(true)
                    } else {
                        binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                        rtcEngine.muteLocalVideoStream(false)
                    }
                    val existIndex = mVideoList.indexOfValue(curUid)
                    if (existIndex != -1) return@runOnMainThread
                    val emptyIndex = fetchValidIndex(curUid)
                    if (emptyIndex == -1) return@runOnMainThread
                    mVideoList.put(emptyIndex, curUid)
                    notifyItemChanged(emptyIndex)
                } else {
                    // 是否已经在麦位上
                    val existIndex = mVideoList.indexOfValue(curUid)
                    if (existIndex == -1) return@runOnMainThread
                    mVideoList.put(existIndex, -1)
                    notifyItemChanged(existIndex)
                    stopSendSei()
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
            if (isOwner) {
                joinChannel(Constants.CLIENT_ROLE_BROADCASTER)
            } else {
                // 默认 cdn 观众
                switchCdnAudience(cdnPosition)
            }
            for (i in 0 until 3) {
                mVideoList.put(i, -1)
            }
            if (isOwner) {
                mVideoList.put(0, ownerUid)
                notifyItemChanged(0)
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
                TranscodeSetting.liveTranscoding3(
                    channelName,
                    pushUrl,
                    mVideoList
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
                    ToastTool.showToast("stop rtmp stream error ！")
                }
            }
        }
    }

    /**新主播进来，更新CDN推流*/
    private fun updateRtmpStreamEnable() {
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val pushUrl = KeyCenter.getRtmpPushUrl(channelName)
        AgoraRtcEngineInstance.transcoder.updateRtmpTranscoding(
            TranscodeSetting.liveTranscoding3(
                channelName,
                pushUrl,
                mVideoList
            )
        ) { succeed ->
            if (succeed) {
                publishedRtmp = true
            } else {
                ToastTool.showToast("push rtmp stream error ！")
            }
        }
    }

    private fun joinChannel(role: Int) {
        channelMediaOptions.clientRoleType = role
        channelMediaOptions.autoSubscribeVideo = true
        channelMediaOptions.autoSubscribeAudio = true
        channelMediaOptions.publishCameraTrack = role == Constants.CLIENT_ROLE_BROADCASTER
        channelMediaOptions.publishMicrophoneTrack = role == Constants.CLIENT_ROLE_BROADCASTER
        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)

        updateVideoEncoder()
//        val score = rtcEngine.queryDeviceScore()
//        Log.d(TAG, "queryDeviceScore $score")
        // 265
        rtcEngine.setParameters("{\"che.video.videoCodecIndex\":2}")
        rtcEngine.setDefaultAudioRoutetoSpeakerphone(true)
        val code: Int = rtcEngine.registerMediaMetadataObserver(iMetadataObserver, IMetadataObserver.VIDEO_METADATA)
        Log.d(TAG, "registerMediaMetadataObserver code:$code")
        rtcEngine.joinChannel(null, channelName, curUid, channelMediaOptions)
    }

    private fun updateVideoEncoder() {
        rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
    }

    private fun fetchValidIndex(uid: Int): Int {
        if (uid == channelName.toInt()) {
            return 0
        } else {
            for (i in 1 until mVideoList.size()) {
                if (mVideoList.valueAt(i) == -1) {
                    return i
                }
            }
        }
        return -1
    }

    private fun checkRequirePerms(
        force: Boolean = false,
        denied: (() -> Unit)? = null,
        granted: () -> Unit
    ) {
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
        if (isOwner || audienceStatus == AudienceStatus.RTC_Broadcaster) {
            stopSendSei()
        }
        if (isOwner) {
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

    private fun notifyDataSetChanged() {
        mVideoList.forEach { key, value ->
            notifyItemChanged(key)
        }
    }

    private val mTextureVideos = mutableMapOf<Int, TextureView>()
    private fun notifyItemChanged(position: Int) {
        val videoContainer = when (position) {
            0 -> binding.videosLayout.iBroadcasterAView
            1 -> binding.videosLayout.iBroadcasterBView
            2 -> binding.videosLayout.iBroadcasterCView
            else -> binding.videosLayout.iBroadcasterAView
        }
        val uid = mVideoList[position]
        if (uid == -1) {
            mTextureVideos.remove(position)
            rtcEngine.setupRemoteVideo(
                VideoCanvas(null, Constants.RENDER_MODE_HIDDEN, Constants.VIDEO_MIRROR_MODE_DISABLED, uid)
            )
            if (videoContainer.childCount > 0) videoContainer.removeAllViews()
        } else {
            var textureView = mTextureVideos[position]
            if (textureView == null) {
                val act = activity ?: return
                textureView = TextureView(act).apply {
                    mTextureVideos[position] = this
                }
            } else {
                ((textureView.parent) as ViewGroup).removeAllViews()
            }
            if (uid == curUid) {
                rtcEngine.setupLocalVideo(
                    VideoCanvas(textureView, Constants.RENDER_MODE_HIDDEN, Constants.VIDEO_MIRROR_MODE_AUTO, uid)
                )
            } else {
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(textureView, Constants.RENDER_MODE_HIDDEN, Constants.VIDEO_MIRROR_MODE_DISABLED, uid)
                )
            }
            if (videoContainer.childCount > 0) videoContainer.removeAllViews()
            videoContainer.addView(textureView)
        }
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private fun runOnMainThread(runnable: Runnable) {
        if (Thread.currentThread() === mainHandler.looper.thread) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
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
            if (isOwner || audienceStatus == AudienceStatus.RTC_Broadcaster) sendMetaSei()
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