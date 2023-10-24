package io.agora.sttmediaplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import io.agora.aigc.sdk.AIGCServiceCallback
import io.agora.aigc.sdk.constants.HandleResult
import io.agora.aigc.sdk.constants.ServiceCode
import io.agora.aigc.sdk.constants.ServiceEvent
import io.agora.aigc.sdk.constants.Vad
import io.agora.aigc.sdk.model.Data
import io.agora.mediaplayer.IMediaPlayer
import io.agora.mediaplayer.IMediaPlayerObserver
import io.agora.mediaplayer.data.PlayerUpdatedInfo
import io.agora.mediaplayer.data.SrcInfo
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IAudioFrameObserver
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.audio.AudioParams
import io.agora.sttmediaplayer.databinding.SttMainActivityBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity(), AIGCServiceCallback, IAudioFrameObserver {

    companion object {
        private val TAG = "Agora-" + MainActivity::class.java.simpleName
        private const val CHANNEL_ID = "TestAgoraSTT"
    }

    private lateinit var mBinding: SttMainActivityBinding
    private var mRtcEngine: RtcEngine? = null
    private var mMediaPlayer: IMediaPlayer? = null

    private val mVideoList: List<VideoModel> by lazy {
        mutableListOf(
            VideoModel("东风破"),
            VideoModel("夜曲"),
            VideoModel("稻香"),
            VideoModel("千里之外"),
            VideoModel("最长的电影")
        )
    }

    private val mSpeechQueue: ArrayDeque<String> = ArrayDeque()

    @Volatile
    private var isSpeaking = false

    @Volatile
    private var isPlaying = false

    private val mHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    private fun toast(message: String) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } else {
            mHandler.post {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = SttMainActivityBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        handlePermission()
        mBinding.btnStart.isEnabled = false
        mBinding.btnStart.alpha = 0.6f
        mBinding.btnStart.setOnClickListener(object : OnClickListener {
            override fun onClick(v: View?) {

                if (isSpeaking) {
                    updateRoleSpeak(false)
                    updateRecycler(null)
                    STTServiceManager.instance.aIGCService?.stop()
                    mSpeechQueue.clear()
                    mBinding.btnStart.text = "开始"
                    mBinding.tvSpeech.text = ""
                } else {
                    updateRoleSpeak(true)
                    STTServiceManager.instance.aIGCService?.start()
                    mBinding.btnStart.text = "停止"
                }
                isSpeaking = !isSpeaking
            }
        })
        mBinding.btnExit.setOnClickListener(object : OnClickListener {
            override fun onClick(v: View?) {
                finish()
            }
        })
        mBinding.recyclerVideoName.adapter = ActionAdapter(this, mVideoList)
        initData()
        initRtc()
        initMediaPlayer()
        initAIGCService()
    }

    private fun updateRoleSpeak(isSpeak: Boolean): Boolean {
        var ret = Constants.ERR_OK
        ret += mRtcEngine?.updateChannelMediaOptions(object : ChannelMediaOptions() {
            init {
                publishMicrophoneTrack = isSpeak
//                publishCustomAudioTrack = isSpeak
            }
        }) ?: Constants.ERR_FAILED
        return ret == Constants.ERR_OK
    }


    private fun handlePermission() {
        // 需要动态申请的权限
        val permission = Manifest.permission.RECORD_AUDIO
        //查看是否已有权限
        val checkSelfPermission = ActivityCompat.checkSelfPermission(applicationContext, permission)
        if (checkSelfPermission == PackageManager.PERMISSION_GRANTED) {
            //已经获取到权限  获取用户媒体资源
        } else {
            //没有拿到权限  是否需要在第二次请求权限的情况下
            // 先自定义弹框说明 同意后在请求系统权限(就是是否需要自定义DialogActivity)
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            } else {
                val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
                requestPermissions(permissions, 1)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        }
    }

    private fun initData() {
        mVideoList.forEach {
            val fileName = it.name + ".mp4"
            initFile(fileName)
            val path = filesDir.absolutePath + File.separator + fileName
            it.localPath = path
        }
    }

    private fun initFile(fileName: String) {
        try {
            val inputStream = assets.open(fileName)
            val out = File(filesDir.absolutePath + File.separator + fileName)
            val outputStream: OutputStream = FileOutputStream(out)
            val buffer = ByteArray(10240)
            while (true) {
                val len = inputStream.read(buffer)
                if (len < 0) break
                outputStream.write(buffer, 0, len)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initRtc(): Boolean {
        if (mRtcEngine == null) {
            try {
                val rtcEngineConfig = RtcEngineConfig()
                rtcEngineConfig.mContext = applicationContext
                rtcEngineConfig.mAppId = KeyCenter.APP_ID
                rtcEngineConfig.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                rtcEngineConfig.mEventHandler = object : IRtcEngineEventHandler() {
                    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                        Log.i(TAG, "onJoinChannelSuccess channel:$channel uid:$uid elapsed:$elapsed")
                        mRtcEngine?.registerAudioFrameObserver(this@MainActivity)
                    }

                    override fun onLeaveChannel(stats: RtcStats) {
                        Log.i(TAG, "onLeaveChannel stats:$stats")
                    }

                    override fun onUserOffline(uid: Int, reason: Int) {
                        Log.i(TAG, "onUserOffline uid:$uid reason:$reason")
                    }

                    override fun onUserJoined(uid: Int, elapsed: Int) {
                        Log.i(TAG, "onUserJoined uid:$uid elapsed:$elapsed")
                    }
                }
                rtcEngineConfig.mAudioScenario = Constants.AudioScenario.getValue(Constants.AudioScenario.DEFAULT)
                mRtcEngine = RtcEngine.create(rtcEngineConfig)
                mRtcEngine?.apply {
                    setParameters("{\"rtc.enable_debug_log\":true}")
                    enableAudio()
                    setAudioProfile(
                        Constants.AUDIO_PROFILE_DEFAULT, Constants.AUDIO_SCENARIO_GAME_STREAMING
                    )
                    setDefaultAudioRoutetoSpeakerphone(true)
                    setPlaybackAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_WRITE, 640)
                    setRecordingAudioFrameParameters(16000, 1, Constants.RAW_AUDIO_FRAME_OP_MODE_READ_WRITE, 640)
                    val token = KeyCenter.getRtcToken(CHANNEL_ID, KeyCenter.getUserUid())
                    val ret = joinChannel(
                        token,
                        CHANNEL_ID,
                        KeyCenter.getUserUid(),
                        ChannelMediaOptions().apply {
                            publishMicrophoneTrack = false
                            publishCustomAudioTrack = false
                            autoSubscribeAudio = true
                            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                        })
                    Log.d(TAG, "joinChannel ret:$ret,channelId:$CHANNEL_ID,uid:${KeyCenter.getUserUid()},token:$token")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    private val mMediaPlayerObserver = object : IMediaPlayerObserver {
        override fun onPlayerStateChanged(
            state: io.agora.mediaplayer.Constants.MediaPlayerState?,
            error: io.agora.mediaplayer.Constants.MediaPlayerError?
        ) {
            Log.d(TAG, "onPlayerStateChanged:$state,$error")
            if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYBACK_ALL_LOOPS_COMPLETED) {
                mHandler.post {
                    isPlaying = false
                    playFirstVideo()
                }
            } else if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_PLAYING) {
                mHandler.post {
                    isPlaying = true
                }
            } else if (state == io.agora.mediaplayer.Constants.MediaPlayerState.PLAYER_STATE_OPEN_COMPLETED) {
                mHandler.post {
                    for (i in mVideoList.indices) {
                        val localPath = mVideoList[i].localPath
                        mMediaPlayer?.preloadSrc(localPath, 0L)
                    }
                }
            }
        }

        override fun onPositionChanged(position_ms: Long) {

        }

        override fun onPlayerEvent(
            eventCode: io.agora.mediaplayer.Constants.MediaPlayerEvent?,
            elapsedTime: Long,
            message: String?
        ) {

        }

        override fun onMetaData(type: io.agora.mediaplayer.Constants.MediaPlayerMetadataType?, data: ByteArray?) {

        }

        override fun onPlayBufferUpdated(playCachedBuffer: Long) {

        }

        override fun onPreloadEvent(src: String, event: io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent?) {
            Log.d(TAG, "onPreloadEvent:$src,$event")
            when (event) {
                io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent.PLAYER_PRELOAD_EVENT_COMPLETE -> {
                    for (i in 0 until mVideoList.size) {
                        val videoModel = mVideoList[i]
                        if (videoModel.localPath == src) {
                            videoModel.preloaded = true
                            break
                        }
                    }
                }

                io.agora.mediaplayer.Constants.MediaPlayerPreloadEvent.PLAYER_PRELOAD_EVENT_ERROR -> {
                    toast("预加载视频失败:$src")
                }

                else -> {}
            }
        }

        override fun onAgoraCDNTokenWillExpire() {

        }

        override fun onPlayerSrcInfoChanged(from: SrcInfo?, to: SrcInfo?) {

        }

        override fun onPlayerInfoUpdated(info: PlayerUpdatedInfo?) {

        }

        override fun onAudioVolumeIndication(volume: Int) {

        }
    }

    private fun initMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = mRtcEngine?.createMediaPlayer()
        }
        mMediaPlayer?.registerPlayerObserver(mMediaPlayerObserver)
        mMediaPlayer?.setView(mBinding.mediaPlayer)
        // TODO: sdk 限制，需要先 open 一个
        mMediaPlayer?.open("/assets/test.mp3", 0L)
    }

    private fun initAIGCService() {
        STTServiceManager.instance.initAIGCService(this, applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        STTServiceManager.instance.destroy()
    }


    override fun onEventResult(event: ServiceEvent, code: ServiceCode, msg: String?) {
        Log.i(TAG, "onEventResult event:$event code:$code msg:$msg")
        if (event == ServiceEvent.INITIALIZE && code == ServiceCode.SUCCESS) {
            mHandler.post {
                mBinding.btnStart.isEnabled = true
                mBinding.btnStart.alpha = 1.0f
            }
        }
    }

    override fun onSpeech2TextResult(
        roundId: String,
        result: Data<String?>,
        isRecognizedSpeech: Boolean
    ): HandleResult {
        Log.i(
            TAG, "onSpeech2TextResult roundId:$roundId result:$result isRecognizedSpeech:$isRecognizedSpeech"
        )
        if (isRecognizedSpeech) {
            // 一句话结束
            mHandler.post {
                for (i in 0 until mVideoList.size) {
                    val videoModel = mVideoList[i]
                    if (videoModel.name == result.data) {
                        mSpeechQueue.add(videoModel.name)
                        mBinding.tvSpeech.append(videoModel.name)
                        mBinding.tvSpeech.append("\n")
                        break
                    }
                }
                playFirstVideo()
            }
        }
        return HandleResult.CONTINUE
    }

    @Volatile
    private var mCurrentPlayPreloadSrc = ""
    private fun playFirstVideo() {
        if (isPlaying) return
        mSpeechQueue.removeFirstOrNull()?.let { speech ->
            for (i in 0 until mVideoList.size) {
                val videoModel = mVideoList[i]
                if (videoModel.preloaded && videoModel.name == speech) {
                    if (videoModel.localPath == mCurrentPlayPreloadSrc) return
                    mMediaPlayer?.playPreloadedSrc(videoModel.localPath)
                    mCurrentPlayPreloadSrc = videoModel.localPath
                    updateRecycler(videoModel)
                    Log.d(TAG, "playPreloadedSrc ${videoModel.localPath}")
                    break
                }
            }
        }
    }

    private fun updateRecycler(videoModel: VideoModel?) {
        mVideoList.forEach {
            it.isSelected = false
        }
        mVideoList.forEach {
            if (it.name == videoModel?.name) {
                it.isSelected = true
            }
        }
        mBinding.recyclerVideoName.adapter?.notifyDataSetChanged()
    }

    override fun onLLMResult(roundId: String?, answer: Data<String>?): HandleResult {
        return super.onLLMResult(roundId, answer)
    }

    override fun onText2SpeechResult(
        roundId: String?,
        voice: Data<ByteArray>?,
        sampleRates: Int,
        channels: Int,
        bits: Int
    ): HandleResult {
        return super.onText2SpeechResult(roundId, voice, sampleRates, channels, bits)
    }

    override fun onRecordAudioFrame(
        channelId: String,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer,
        renderTimeMs: Long,
        avsyncType: Int
    ): Boolean {
        val length = buffer.remaining()
        val origin = ByteArray(length)
        buffer[origin]
        buffer.flip()
        STTServiceManager.instance.aIGCService?.pushSpeechDialogue(origin, Vad.UNKNOWN)
        return false
    }

    override fun onPlaybackAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsyncType: Int
    ): Boolean {
        return true
    }

    override fun onMixedAudioFrame(
        channelId: String?,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsyncType: Int
    ): Boolean {
        return false
    }

    override fun onEarMonitoringAudioFrame(
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsyncType: Int
    ): Boolean {
        return false
    }

    override fun onPlaybackAudioFrameBeforeMixing(
        channelId: String?,
        userId: Int,
        type: Int,
        samplesPerChannel: Int,
        bytesPerSample: Int,
        channels: Int,
        samplesPerSec: Int,
        buffer: ByteBuffer?,
        renderTimeMs: Long,
        avsyncType: Int
    ): Boolean {
        return false
    }

    override fun getObservedAudioFramePosition(): Int {
        return 0
    }

    override fun getRecordAudioParams(): AudioParams? {
        return null
    }

    override fun getPlaybackAudioParams(): AudioParams? {
        return null
    }

    override fun getMixedAudioParams(): AudioParams? {
        return null
    }

    override fun getEarMonitoringAudioParams(): AudioParams? {
        return null
    }
}

class ActionAdapter constructor(private val mContext: Context, private val dataList: List<VideoModel>) :
    RecyclerView.Adapter<ActionViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(mContext).inflate(R.layout.stt_main_item, parent, false)
        return ActionViewHolder(view)
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        val actionData = dataList[position]
        if (actionData.isSelected) {
            holder.tvAction.setBackgroundColor(ResourcesCompat.getColor(mContext.resources, R.color.blue_9f, null))
        } else {
            holder.tvAction.setBackgroundColor(ResourcesCompat.getColor(mContext.resources, R.color.white_a8, null))
        }
        holder.tvAction.text = actionData.name
    }
}

data class VideoModel constructor(
    val name: String,
    var localPath: String = "",
    var isSelected: Boolean = false,
    var preloaded: Boolean = false
)

class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val tvAction: TextView

    init {
        tvAction = itemView.findViewById(R.id.tvAction)
    }
}

