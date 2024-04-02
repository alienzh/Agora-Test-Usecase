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
import io.agora.mediarelay.rtc.AgoraRtcHelper
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.transcoder.TranscodeSetting
import io.agora.mediarelay.tools.PermissionHelp
import io.agora.mediarelay.tools.ToastTool
import io.agora.mediarelay.widget.DashboardFragment
import io.agora.mediarelay.widget.OnFastClickListener
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.video.VideoCanvas

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
            binding.btMute.isVisible = true
            binding.btMute.setImageResource(R.drawable.ic_mic_on)
            binding.videosLayout.videoContainer.isVisible = true
            binding.layoutCdnContainer.isVisible = false
        } else {
            binding.btLinking.isVisible = true
            binding.btSwitchStream.isVisible = true
            binding.btMute.isVisible = false
            // 默认 cdn 观众
            binding.videosLayout.videoContainer.isVisible = false
            binding.layoutCdnContainer.isVisible = true
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
                        binding.btMute.isVisible = true
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
                        binding.btMute.isVisible = true
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
                        binding.btMute.isVisible = false
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
                        binding.btMute.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Audience -> { // rtc 观众--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
                        binding.btMute.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }

                    AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> cdn 观众
                        audienceStatus = AudienceStatus.CDN_Audience
                        switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
                        binding.btMute.isVisible = false
                        binding.btSwitchStream.isVisible = true
                        binding.btLinking.text = getString(R.string.calling)
                    }
                }
            }
        })
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
        val dashboardFragment = DashboardFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.fl_dashboard, dashboardFragment)
            .commit()
        binding.btDashboard.setOnClickListener {
            it.isSelected = !it.isSelected
            dashboardFragment.setOn(it.isSelected)
            binding.flDashboard.isVisible = it.isSelected
        }
    }

    private var muteLocalAudio = false

    private val mediaPlayerObserver = object : MPObserverAdapter() {
        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerError?
        ) {
            super.onPlayerStateChanged(state, error)
            Log.d(TAG, "onPlayerStateChanged: $state，$error")
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
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
            setPlayerOption("play_speed_down_cache_duration", 0)
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
        joinChannel(role)
    }

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = {
            runOnMainThread {
                if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                    val existIndex = mVideoList.indexOfValue(curUid)
                    if (existIndex != -1) return@runOnMainThread
                    var emptyIndex = fetchValidIndex(curUid)
                    if (emptyIndex == -1) return@runOnMainThread
                    mVideoList.put(emptyIndex, curUid)
                    notifyItemChanged(emptyIndex)
                }
                if (isOwner) {
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
            if (isOwner) {
                joinChannel(Constants.CLIENT_ROLE_BROADCASTER)
            } else {
                // 默认 cdn 观众
                switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
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
}