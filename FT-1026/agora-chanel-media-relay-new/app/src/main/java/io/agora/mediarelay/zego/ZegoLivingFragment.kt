package io.agora.mediarelay.zego

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.blankj.utilcode.util.ThreadUtils
import com.blankj.utilcode.util.ToastUtils
import com.blankj.utilcode.util.Utils
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoPublishChannel
import im.zego.zegoexpress.constants.ZegoPublisherState
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.constants.ZegoVideoConfigPreset
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoPublishStreamQuality
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoRoomRecvTransparentMessage
import im.zego.zegoexpress.entity.ZegoRoomSendTransparentMessage
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import im.zego.zegoexpress.entity.ZegoVideoConfig
import io.agora.mediarelay.KeyCenter
import io.agora.mediarelay.R
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentZegoLivingBinding
import io.agora.mediarelay.tools.LogTool
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * @author create by zhangwei03
 */
class ZegoLivingFragment : BaseUiFragment<FragmentZegoLivingBinding>() {
    companion object {
        private const val TAG = "ZegoLivingFragment"

        const val KEY_CHANNEL_ID: String = "key_channel_id"
        const val KEY_IS_BROADCASTER: String = "key_is_broadcaster"
    }

    private val channelName by lazy { arguments?.getString(KEY_CHANNEL_ID) ?: "" }

    private val isBroadcaster by lazy { arguments?.getBoolean(KEY_IS_BROADCASTER, false) ?: false }

    private val zegoEngine by lazy { ZegoEngineInstance.engine }

    private val zegoUser: ZegoUser by lazy { ZegoUser(ZegoEngineInstance.userId) }

    private val publishStreamId: String by lazy { "$channelName" }

    private val ownerPublishStreamId: String by lazy { "$channelName" }

    private var frontCamera = true

    // pk 频道流
    private var remotePkChannel: String? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentZegoLivingBinding {
        return FragmentZegoLivingBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        checkRequirePerms {
            loginRoom()
        }
    }

    private fun initView() {
        if (isBroadcaster) {
            binding.layoutChannel.isVisible = true
            binding.btSubmitPk.isVisible = true
            binding.btSwitchCarma.isVisible = true
            binding.btMuteMic.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.isVisible = true
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
        } else {
            binding.layoutChannel.isVisible = false
            binding.btSubmitPk.isVisible = false
            binding.btSwitchCarma.isVisible = false
            binding.btMuteMic.isVisible = false
            binding.btMuteCarma.isVisible = false
        }
        binding.tvChannelId.text = "$channelName(${KeyCenter.vendor})"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btSubmitPk.setOnClickListener {
            if (!remotePkChannel.isNullOrEmpty()) { // pk 中停止pk
                binding.btSubmitPk.text = getString(R.string.start_pk)
                binding.etPkChannel.setText("")
                remotePkChannel = ""
                stopPk()
            } else {
                val channelId = binding.etPkChannel.text.toString()
                if (checkChannelId(channelId)) return@setOnClickListener
                remotePkChannel = channelId
                startPk(channelId)
            }
        }

        binding.btSwitchCarma.setOnClickListener {
            frontCamera = !frontCamera
            zegoEngine.useFrontCamera(frontCamera)
        }
        binding.btMuteMic.setOnClickListener {
            if (muteLocalAudio) {
                muteLocalAudio = false
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
                zegoEngine.mutePublishStreamAudio(false)
            } else {
                muteLocalAudio = true
                binding.btMuteMic.setImageResource(R.drawable.ic_mic_off)
                zegoEngine.mutePublishStreamAudio(true)
            }
        }
        binding.btMuteCarma.setOnClickListener {
            if (muteLocalVideo) {
                muteLocalVideo = false
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
                zegoEngine.startPreview()
            } else {
                muteLocalVideo = true
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                zegoEngine.stopPreview()
            }
        }
    }


    private var muteLocalAudio = false
    private var muteLocalVideo = false

    private fun checkChannelId(channelId: String): Boolean {
        if (channelId.isEmpty()) {
            ToastUtils.showShort("Please enter pk channel id")
            return true
        }
        return false
    }

    // 加入 zego 房间
    private fun loginRoom() {
        //login room
        zegoEngine.loginRoom(channelName, zegoUser, ZegoRoomConfig()) { errorCode, extendedData ->
            LogTool.d(TAG, "loginRoom: errorCode = $errorCode, extendedData =  $extendedData")
            val isSuccess = errorCode == 0
            if (isSuccess) {
                ToastUtils.showShort("zego loginroom success")
            } else {
                ToastUtils.showShort("zego loginroom error: $errorCode")
                ThreadUtils.runOnUiThreadDelayed({ findNavController().popBackStack() }, 1000)
            }

            if (!isSuccess) return@loginRoom
            if (isBroadcaster) {
                startPublish()
                startSendRemoteChannel()
            } else {
                startPlay(ownerPublishStreamId)
            }
        }
        //enable the camera
        zegoEngine.enableCamera(isBroadcaster)
        //enable the microphone
        zegoEngine.muteMicrophone(!isBroadcaster)
        //enable the speaker
        zegoEngine.muteSpeaker(!isBroadcaster)

        zegoEngine.setEventHandler(object : IZegoEventHandler() {

            override fun onRoomStateChanged(
                roomID: String?,
                reason: ZegoRoomStateChangedReason?,
                errorCode: Int,
                extendedData: JSONObject?
            ) {
                super.onRoomStateChanged(roomID, reason, errorCode, extendedData)
                LogTool.d(TAG, "onRoomStateChanged: roomID = $roomID, reason = $reason, errorCode = $errorCode")
            }

            override fun onRecvRoomTransparentMessage(roomID: String?, message: ZegoRoomRecvTransparentMessage?) {
                super.onRecvRoomTransparentMessage(roomID, message)
                val data = message?.content ?: return
                if (roomID != channelName) return
                runOnMainThread {
                    try {
                        val strMsg = String(data)
                        val jsonMsg = JSONObject(strMsg)
                        if (jsonMsg.getString("cmd") == "StartPk") { //同步远端 remotePk
                            val channel = jsonMsg.getString("channel")
                            if (!remotePkChannel.isNullOrEmpty()) return@runOnMainThread
                            remotePkChannel = channel
                            startPk(channel)
                        } else if (jsonMsg.getString("cmd") == "StopPk") {
                            if (remotePkChannel.isNullOrEmpty()) return@runOnMainThread
                            remotePkChannel = ""
                            stopPk()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "onRecvRoomTransparentMessage $e")
                    }
                }
            }

            override fun onRoomStreamUpdate(
                roomID: String?,
                updateType: ZegoUpdateType?,
                streamList: ArrayList<ZegoStream>?,
                extendedData: JSONObject?
            ) {
                super.onRoomStreamUpdate(roomID, updateType, streamList, extendedData)
                LogTool.d(
                    TAG,
                    "onRoomStreamUpdate: roomID = $roomID, updateType = $updateType, streamList = $streamList"
                )
            }

            override fun onPublisherStateUpdate(
                streamID: String?,
                state: ZegoPublisherState?,
                errorCode: Int,
                extendedData: JSONObject?
            ) {
                super.onPublisherStateUpdate(streamID, state, errorCode, extendedData)
                LogTool.d(TAG, "onPublisherStateUpdate: streamID = $streamID, state = $state, errorCode = $errorCode")
            }

            override fun onPublisherQualityUpdate(streamID: String?, quality: ZegoPublishStreamQuality?) {
                super.onPublisherQualityUpdate(streamID, quality)
                LogTool.d(TAG, "onPublisherQualityUpdate: streamID = $streamID, quality = $quality")
            }

            override fun onPublisherCapturedAudioFirstFrame() {
                super.onPublisherCapturedAudioFirstFrame()
                LogTool.d(TAG, "onPublisherCapturedAudioFirstFrame: ")
            }

            override fun onPublisherCapturedVideoFirstFrame(channel: ZegoPublishChannel?) {
                super.onPublisherCapturedVideoFirstFrame(channel)
                LogTool.d(TAG, "onPublisherCapturedVideoFirstFrame: channel = $channel")
            }

            override fun onPublisherVideoSizeChanged(width: Int, height: Int, channel: ZegoPublishChannel?) {
                super.onPublisherVideoSizeChanged(width, height, channel)
                LogTool.d(TAG, "onPublisherVideoSizeChanged: width = $width, height = $height")
            }
        })
    }

    private val localTexture by lazy { TextureView(requireActivity()) }
    private val ownerRemoteTexture by lazy { TextureView(requireActivity()) }

    // 推流
    private fun startPublish() {
        if (!isBroadcaster) return
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(localTexture)
        zegoEngine.startPreview(ZegoCanvas(localTexture))
        // Start publishing stream
        zegoEngine.startPublishingStream(publishStreamId)
    }

    // 拉流
    private fun startPlay(playStreamId: String) {
        val act = activity ?: return
        binding.layoutVideoContainer.removeAllViews()
        binding.layoutVideoContainer.addView(ownerRemoteTexture)
        zegoEngine.startPlayingStream(playStreamId, ZegoCanvas(ownerRemoteTexture))
    }

    override fun onDestroy() {
        if (isBroadcaster) {
            stopSendRemoteChannel()
            zegoEngine.stopPreview()
            zegoEngine.stopPublishingStream()
        } else {
            zegoEngine.stopPlayingStream(ownerPublishStreamId)
        }
        ZegoEngineInstance.destroy()
        super.onDestroy()
    }

    private var remotePublishStreamId: String? = null
    private fun startPk(remoteChannel: String) {
        updateVideoEncoder()
        updatePkMode(remoteChannel)
    }

    private fun stopPk() {
        remotePublishStreamId?.let {
            zegoEngine.stopPlayingStream(it)
            remotePublishStreamId = null
        }
        updateVideoEncoder()
        updateIdleMode()
    }

    // 开始发送 remoteChannel
    @Volatile
    private var mStopRemoteChannel = true

    private val remoteChannelTask = object : Utils.Task<Boolean>(Utils.Consumer {
        if (mStopRemoteChannel) return@Consumer
        if (!isBroadcaster) return@Consumer
        sendRemoteChannel(remotePkChannel ?: "")
    }) {
        override fun doInBackground(): Boolean {
            return true
        }
    }

    private fun startSendRemoteChannel() {
        mStopRemoteChannel = false
        ThreadUtils.executeBySingleAtFixRate(remoteChannelTask, 0, 1, TimeUnit.SECONDS)
    }

    private fun sendRemoteChannel(remoteChannel: String) {
        val msg: MutableMap<String, Any?> = HashMap()
        if (remoteChannel.isEmpty()) {
            msg["cmd"] = "StopPk"
        } else {
            msg["cmd"] = "StartPk"
            msg["channel"] = remoteChannel
        }
        val jsonMsg = JSONObject(msg)
        val message = ZegoRoomSendTransparentMessage().apply {
            content = jsonMsg.toString().toByteArray()
        }
        zegoEngine.sendTransparentMessage(channelName, message, null)
    }


    // 停止发送remote channel
    private fun stopSendRemoteChannel() {
        mStopRemoteChannel = true
        remoteChannelTask.cancel()
    }

    private fun updateVideoEncoder() {
        if (!remotePkChannel.isNullOrEmpty() && ZegoSettings.mVideoConfigpreset == ZegoVideoConfigPreset.PRESET_1080P) {
            val videoConfigPreset = ZegoVideoConfigPreset.PRESET_720P
            zegoEngine.videoConfig = ZegoVideoConfig(videoConfigPreset)
            Log.d(TAG, "updateVideoEncoder ${zegoEngine.videoConfig}")
        } else {
            zegoEngine.videoConfig = ZegoVideoConfig(ZegoSettings.mVideoConfigpreset)
            Log.d(TAG, "updateVideoEncoder ${zegoEngine.videoConfig}")
        }
    }


    /**pk 模式,*/
    private fun updatePkMode(remoteChannel: String) {
        val act = activity ?: return
        binding.videoPKLayout.videoContainer.isVisible = true
        binding.layoutVideoContainer.isVisible = false
        binding.layoutVideoContainer.removeAllViews()
        binding.btSubmitPk.text = getString(R.string.stop_pk)
        if (isBroadcaster) { // 主播
            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(localTexture)

            val remoteTexture = TextureView(act)
            binding.videoPKLayout.iBroadcasterBView.removeAllViews()
            binding.videoPKLayout.iBroadcasterBView.addView(remoteTexture)

            remotePublishStreamId = remoteChannel
            zegoEngine.startPlayingStream(remotePublishStreamId, ZegoCanvas(remoteTexture))
        } else {

            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(ownerRemoteTexture)

            val remoteTextureB = TextureView(act)
            binding.videoPKLayout.iBroadcasterBView.removeAllViews()
            binding.videoPKLayout.iBroadcasterBView.addView(remoteTextureB)

            remotePublishStreamId = remoteChannel
            zegoEngine.startPlayingStream(remotePublishStreamId, ZegoCanvas(remoteTextureB))
        }
    }

    /**单主播模式*/
    private fun updateIdleMode() {
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.iBroadcasterAView.removeAllViews()
        binding.videoPKLayout.iBroadcasterBView.removeAllViews()
        binding.btSubmitPk.text = getString(R.string.start_pk)
        if (isBroadcaster) {
            binding.layoutVideoContainer.removeAllViews()
            binding.layoutVideoContainer.addView(localTexture)
        } else {
            binding.layoutVideoContainer.removeAllViews()
            binding.layoutVideoContainer.addView(ownerRemoteTexture)
        }
    }

}