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
import androidx.recyclerview.widget.RecyclerView
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingMultiBinding
import io.agora.mediarelay.databinding.ViewVideoItemBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.AgoraRtcHelper
import io.agora.mediarelay.rtc.IAgoraRtcClient
import io.agora.mediarelay.rtc.MPObserverAdapter
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.tools.PermissionHelp
import io.agora.mediarelay.tools.ToastTool
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.video.VideoCanvas

enum class AudienceStatus {
    CDN_Audience,
    RTC_Audience,
    RTC_Broadcaster
}

/**
 * @author create by zhangwei03
 */
class LivingMultiFragment : BaseUiFragment<FragmentLivingMultiBinding>() {
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

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val ownerUid by lazy { channelName.toIntOrNull() ?: 123 }

    private val curUid by lazy {
        KeyCenter.rtcUid(role == Constants.CLIENT_ROLE_BROADCASTER, channelName)
    }

    // 房主
    private val isOwner: Boolean
        get() {
            return role == Constants.CLIENT_ROLE_BROADCASTER
        }

    private var videoAdapter: VideoAdapter? = null

    private fun runOnMainThread(runnable: Runnable) {
        if (Thread.currentThread() === mainHandler.looper.thread) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLivingMultiBinding {
        return FragmentLivingMultiBinding.inflate(inflater)
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
            binding.btSwitchCarma.isVisible = true
            binding.btMute.isVisible = true
            binding.btMute.setImageResource(R.drawable.ic_mic_on)
            binding.recyclerVideo.isVisible = true
            binding.layoutCdnContainer.isVisible = false
        } else {
            binding.btLinking.isVisible = true
            binding.btSwitchStream.isVisible = true
            binding.btSwitchCarma.isVisible = false
            binding.btMute.isVisible = false
            // 默认 cdn 观众
            binding.recyclerVideo.isVisible = false
            binding.layoutCdnContainer.isVisible = true
        }
        binding.tvChannelId.text = "ChannelId:$channelName"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        // 观众连麦
        binding.btLinking.setOnClickListener {
            when (audienceStatus) {
                AudienceStatus.CDN_Audience -> { // cdn 观众--> rtc 主播
                    audienceStatus = AudienceStatus.RTC_Broadcaster
                    switchRtc(Constants.CLIENT_ROLE_BROADCASTER)
                    binding.btSwitchCarma.isVisible = true
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
                    binding.btSwitchCarma.isVisible = true
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
                    binding.btSwitchCarma.isVisible = false
                    binding.btMute.isVisible = false
                    binding.btSwitchStream.isVisible = true
                    binding.btLinking.text = getString(R.string.calling)
                }
            }
        }
        binding.btSwitchStream.setOnClickListener {
            when (audienceStatus) {
                AudienceStatus.CDN_Audience -> { // cdn 观众--> rtc 观众
                    audienceStatus = AudienceStatus.RTC_Audience
                    switchRtc(Constants.CLIENT_ROLE_AUDIENCE)
                    binding.btSwitchCarma.isVisible = false
                    binding.btMute.isVisible = false
                    binding.btSwitchStream.isVisible = true
                    binding.btLinking.text = getString(R.string.calling)
                }

                AudienceStatus.RTC_Audience -> { // rtc 观众--> cdn 观众
                    audienceStatus = AudienceStatus.CDN_Audience
                    switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
                    binding.btSwitchCarma.isVisible = false
                    binding.btMute.isVisible = false
                    binding.btSwitchStream.isVisible = true
                    binding.btLinking.text = getString(R.string.calling)
                }

                AudienceStatus.RTC_Broadcaster -> { // rtc 主播--> cdn 观众
                    audienceStatus = AudienceStatus.CDN_Audience
                    switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
                    binding.btSwitchCarma.isVisible = false
                    binding.btMute.isVisible = false
                    binding.btSwitchStream.isVisible = true
                    binding.btLinking.text = getString(R.string.calling)
                }
            }
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
        binding.btSwitchStream.text = getString(R.string.rtc_audience)

        val textureView = TextureView(act)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.addView(textureView)
        binding.layoutCdnContainer.isVisible = true
        binding.recyclerVideo.isVisible = false
        mediaPlayer = rtcEngine.createMediaPlayer()
        mediaPlayer?.apply {
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
            it.destroy()
            mediaPlayer = null
        }
        binding.btSwitchStream.text = getString(R.string.cdn_audience)
        binding.layoutCdnContainer.removeAllViews()
        binding.layoutCdnContainer.isVisible = false
        binding.recyclerVideo.isVisible = true
        joinChannel(role)
    }

    private val eventListener = IAgoraRtcClient.IChannelEventListener(
        onChannelJoined = {
            runOnMainThread {
                if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                    videoAdapter?.apply {
                        val existIndex = mVideoList.indexOfValue(curUid)
                        if (existIndex != -1) return@runOnMainThread
                        val emptyIndex = mVideoList.indexOfValue(-1)
                        if (emptyIndex == -1) return@runOnMainThread
                        mVideoList.put(emptyIndex, curUid)
                        notifyItemChanged(emptyIndex)
                    }
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
            if (!isOwner) { // 非房主加入空位置
                videoAdapter?.apply {
                    mVideoList.forEach(action = { key, value ->
                        mVideoList.put(key, -1)
                    })
                    notifyDataSetChanged()
                }
            }
        },
        onUserJoined = { uid ->
            runOnMainThread {
                videoAdapter?.apply {
                    val existIndex = mVideoList.indexOfValue(uid)
                    if (existIndex != -1) return@runOnMainThread
                    val emptyIndex = mVideoList.indexOfValue(-1)
                    if (emptyIndex == -1) return@runOnMainThread
                    mVideoList.put(emptyIndex, uid)
                    notifyItemChanged(emptyIndex)
                    if (isOwner) {
                        updateRtmpStreamEnable()
                    }
                }
            }
        },
        onUserOffline = {
            runOnMainThread {
                videoAdapter?.apply {
                    val index = mVideoList.indexOfValue(it)
                    if (index == -1) return@runOnMainThread
                    mVideoList.put(index, -1)
                    notifyItemChanged(index)
                    if (isOwner) {
                        updateRtmpStreamEnable()
                    }
                }
            }
        },

        onClientRoleChanged = { oldRole, newRole, newRoleOptions ->
            // 忽略房主
            if (isOwner) return@IChannelEventListener
            runOnMainThread {
                if (audienceStatus == AudienceStatus.RTC_Broadcaster) { // 非房主加入空位置
                    videoAdapter?.apply {
                        val existIndex = mVideoList.indexOfValue(curUid)
                        if (existIndex != -1) return@runOnMainThread
                        val emptyIndex = mVideoList.indexOfValue(-1)
                        if (emptyIndex == -1) return@runOnMainThread
                        mVideoList.put(emptyIndex, curUid)
                        notifyItemChanged(emptyIndex)
                    }
                } else {
                    videoAdapter?.apply {
                        // 是否已经在麦位上
                        val existIndex = mVideoList.indexOfValue(curUid)
                        if (existIndex == -1) return@runOnMainThread
                        mVideoList.put(existIndex, -1)
                        notifyItemChanged(existIndex)
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
            if (isOwner) {
                joinChannel(Constants.CLIENT_ROLE_BROADCASTER)
            } else {
                // 默认 cdn 观众
                switchCdnAudience(KeyCenter.getRtmpPullUrl(channelName))
            }

            val act = activity ?: return@checkRequirePerms
            val videoItem = SparseIntArray()
            for (i in 0 until 16) {
                videoItem.put(i, -1)
            }
            if (isOwner) {
                videoItem.put(0, ownerUid)
            }
            videoAdapter = VideoAdapter(act, videoItem)
            binding.recyclerVideo.adapter = videoAdapter
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
            val adapter = videoAdapter ?: return
            val result = rtcEngine.startRtmpStreamWithTranscoding(
                pushUrl,
                AgoraRtcHelper.liveTranscodingMulti(adapter.mVideoList),
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
    private fun updateRtmpStreamEnable() {
        // CDN 推流转码属性配置。注意：调用这个接口前提是需要转码；否则，就不要调用这个接口。
        val videoUids = videoAdapter?.mVideoList ?: return
        val result = rtcEngine.updateRtmpTranscoding(AgoraRtcHelper.liveTranscodingMulti(videoUids))
        if (result == Constants.RTMP_STREAM_PUBLISH_ERROR_OK) {
            publishedRtmp = true
        } else {
            ToastTool.showToast("update push rtmp stream error:$result！")
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
        if (RtcSettings.mVideoDimensionsAuto) {
//            val ret =
//                rtcEngine.setParameters(
//                    "{\"che.video" +
//                            ".auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":1,\"resolution_list\":\"1920x1080, 1280x720\", \"resolution_score\":\"90, 1\"}}"
//                )
            rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
        } else {
//            val ret =
//                rtcEngine.setParameters("{\"che.video.auto_adjust_resolution\":{\"auto_adjust_resolution_flag\":0}}")
//            Log.d(TAG, "auto_adjust_resolution close $ret")
            rtcEngine.setVideoEncoderConfiguration(AgoraRtcEngineInstance.videoEncoderConfiguration)
        }
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


    inner class VideoAdapter constructor(
        private val mContext: Context,
        val mVideoList: SparseIntArray
    ) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

        private val mTextureVideos = mutableMapOf<Int, TextureView>()

        inner class VideoViewHolder(val binding: ViewVideoItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            return VideoViewHolder(ViewVideoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int {
            return mVideoList.size()
        }

        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            val uid = mVideoList[position]
            if (uid == -1) {
                mTextureVideos.remove(position)
                rtcEngine.setupRemoteVideo(
                    VideoCanvas(null, Constants.RENDER_MODE_FIT, Constants.VIDEO_MIRROR_MODE_ENABLED, uid)
                )
                if (holder.binding.flVideo.childCount > 0) holder.binding.flVideo.removeAllViews()
            } else {
                var textureView = mTextureVideos[position]
                if (textureView == null) {
                    textureView = TextureView(mContext).apply {
                        mTextureVideos[position] = this
                    }
                } else {
                    ((textureView.parent) as ViewGroup).removeAllViews()
                }
                if (uid == curUid) {
                    rtcEngine.setupLocalVideo(
                        VideoCanvas(textureView, Constants.RENDER_MODE_FIT, Constants.VIDEO_MIRROR_MODE_ENABLED, uid)
                    )
                } else {
                    rtcEngine.setupRemoteVideo(
                        VideoCanvas(textureView, Constants.RENDER_MODE_FIT, Constants.VIDEO_MIRROR_MODE_ENABLED, uid)
                    )
                }
                if (holder.binding.flVideo.childCount > 0) holder.binding.flVideo.removeAllViews()
                holder.binding.flVideo.addView(textureView)
            }
        }
    }
}