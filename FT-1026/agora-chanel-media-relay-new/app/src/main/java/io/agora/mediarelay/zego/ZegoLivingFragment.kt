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
import io.agora.mediarelay.BuildConfig
import io.agora.mediarelay.KeyCenter
import io.agora.mediarelay.R
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentZegoLivingBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.RtcSettings
import io.agora.mediarelay.rtc.transcoder.ChannelUid
import io.agora.mediarelay.rtc.transcoder.ZegoTranscodeSetting
import io.agora.mediarelay.tools.LogTool
import io.agora.mediarelay.widget.OnFastClickListener
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

    // pk 频道roomId
    private var remoteZegoRoomId: String? = null

    // agora 频道
    private var remoteAgoraChannel: String? = null

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
//            binding.layoutRoomId.isVisible = true
//            binding.btSubmitPk.isVisible = true
//            binding.layoutAgora.isVisible = true
//            binding.btPkAgora.isVisible = true
//
//            binding.btSwitchCarma.isVisible = true
//            binding.btMuteMic.isVisible = true
//            binding.btMuteCarma.isVisible = true
            binding.groupBroadcaster.isVisible = true
            binding.btMuteMic.setImageResource(R.drawable.ic_mic_on)
            binding.btMuteCarma.setImageResource(R.drawable.ic_camera_on)
        } else {
//            binding.layoutRoomId.isVisible = false
//            binding.btSubmitPk.isVisible = false
//            binding.layoutAgora.isVisible = false
//            binding.btPkAgora.isVisible = false
//
//            binding.btSwitchCarma.isVisible = false
//            binding.btMuteMic.isVisible = false
//            binding.btMuteCarma.isVisible = false

            binding.groupBroadcaster.isVisible = false
        }
        binding.tvChannelId.text = "$channelName(${KeyCenter.vendor})"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btPkZego.setOnClickListener {
            if (!remoteZegoRoomId.isNullOrEmpty()) { // pk 中停止pk
                binding.btPkZego.text = getString(R.string.start_pk_zego)
                binding.etPkZegoRoomId.setText("")
                remoteZegoRoomId = ""
                stopPk()
                binding.groupPkAgora.isVisible = true
            } else {
                val roomId = binding.etPkZegoRoomId.text.toString()
                if (roomId.isEmpty()) {
                    ToastUtils.showShort("Please enter zego roomId")
                    return@setOnClickListener
                }
                binding.btPkZego.text = getString(R.string.stop_pk_zego)
                remoteZegoRoomId = roomId
                startPk(roomId)
                binding.groupPkAgora.isVisible = false
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
                zegoEngine.startPreview(ZegoCanvas(localTexture))
            } else {
                muteLocalVideo = true
                binding.btMuteCarma.setImageResource(R.drawable.ic_camera_off)
                zegoEngine.stopPreview()
            }
        }

        binding.btPkAgora.setOnClickListener(object :
            OnFastClickListener(message = getString(R.string.click_too_fast)) {
            override fun onClickJacking(view: View) {

                if (!remoteAgoraChannel.isNullOrEmpty()) { // pk 中停止pk
                    binding.btPkAgora.text =getString(R.string.start_pk_agora)
                    binding.etAgoraChannel.setText("")
                    remoteAgoraChannel = ""
                    stopPk()
                    binding.groupPk.isVisible = true
                } else {
                    val agoraChannel = binding.etAgoraChannel.text.toString()
                    if (agoraChannel.isEmpty()) {
                        ToastUtils.showShort("Please enter agora channelId")
                        return
                    }
                    binding.btPkAgora.text =getString(R.string.stop_pk_agora)
                    remoteAgoraChannel = agoraChannel
                    remotePublishStreamId = agoraChannel
                    startPk(agoraChannel)
                    binding.groupPk.isVisible = false
                }
            }
        })
    }


    private var muteLocalAudio = false
    private var muteLocalVideo = false

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

                ThreadUtils.runOnUiThread {
                    startPushToAgora()
                }
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
                LogTool.d(TAG, "onRecvRoomTransparentMessage: roomID = $roomID, message = $message")
                val data = message?.content ?: return
                if (roomID != channelName) return
                if (isBroadcaster) return
                runOnMainThread {
                    try {
                        val strMsg = String(data)
                        val jsonMsg = JSONObject(strMsg)
                        if (jsonMsg.getString("cmd") == "StartPk") { //同步远端 remotePk
                            val roomId = jsonMsg.getString("roomId")
                            if (!remoteZegoRoomId.isNullOrEmpty()) return@runOnMainThread
                            remoteZegoRoomId = roomId
                            startPk(roomId)
                        } else if (jsonMsg.getString("cmd") == "StopPk") {
                            if (remoteZegoRoomId.isNullOrEmpty()) return@runOnMainThread
                            remoteZegoRoomId = ""
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

            override fun onPlayerRenderVideoFirstFrame(streamID: String?) {
                super.onPlayerRenderVideoFirstFrame(streamID)
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
            stopPushToZego()
        } else {
            zegoEngine.stopPlayingStream(ownerPublishStreamId)
        }
        ZegoEngineInstance.destroy()
        super.onDestroy()
    }

    private var remotePublishStreamId: String? = null
    private fun startPk(remoteRoomId: String) {
        updateVideoEncoder()
        updatePkMode(remoteRoomId)
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
        sendRemoteChannel(remoteZegoRoomId ?: "")
    }) {
        override fun doInBackground(): Boolean {
            return true
        }
    }

    private fun startSendRemoteChannel() {
        mStopRemoteChannel = false
        ThreadUtils.executeBySingleAtFixRate(remoteChannelTask, 0, 1, TimeUnit.SECONDS)
    }

    private fun sendRemoteChannel(remotePkRoomId: String) {
        val msg: MutableMap<String, Any?> = HashMap()
        if (remotePkRoomId.isEmpty()) {
            msg["cmd"] = "StopPk"
        } else {
            msg["cmd"] = "StartPk"
            msg["roomId"] = remotePkRoomId
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
        if ((!remoteZegoRoomId.isNullOrEmpty() || !remoteAgoraChannel.isNullOrEmpty())
            && ZegoSettings.mVideoConfigpreset ==
            ZegoVideoConfigPreset
                .PRESET_1080P
        ) {
            val videoConfigPreset = ZegoVideoConfigPreset.PRESET_720P
            zegoEngine.videoConfig = ZegoVideoConfig(videoConfigPreset)
            Log.d(TAG, "updateVideoEncoder ${zegoEngine.videoConfig}")
        } else {
            zegoEngine.videoConfig = ZegoVideoConfig(ZegoSettings.mVideoConfigpreset)
            Log.d(TAG, "updateVideoEncoder ${zegoEngine.videoConfig}")
        }
    }


    /**pk 模式,*/
    private fun updatePkMode(remoteRoomId: String) {
        val act = activity ?: return
        binding.videoPKLayout.videoContainer.isVisible = true
        binding.layoutVideoContainer.isVisible = false
        binding.layoutVideoContainer.removeAllViews()
        if (isBroadcaster) { // 主播
            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(localTexture)

            val remoteTexture = TextureView(act)
            binding.videoPKLayout.iBroadcasterBView.removeAllViews()
            binding.videoPKLayout.iBroadcasterBView.addView(remoteTexture)

            remotePublishStreamId = remoteRoomId
            zegoEngine.startPlayingStream(remotePublishStreamId, ZegoCanvas(remoteTexture))
        } else {

            binding.videoPKLayout.iBroadcasterAView.removeAllViews()
            binding.videoPKLayout.iBroadcasterAView.addView(ownerRemoteTexture)

            val remoteTextureB = TextureView(act)
            binding.videoPKLayout.iBroadcasterBView.removeAllViews()
            binding.videoPKLayout.iBroadcasterBView.addView(remoteTextureB)

            remotePublishStreamId = remoteRoomId
            zegoEngine.startPlayingStream(remotePublishStreamId, ZegoCanvas(remoteTextureB))
        }
    }

    /**单主播模式*/
    private fun updateIdleMode() {
        binding.videoPKLayout.videoContainer.isVisible = false
        binding.layoutVideoContainer.isVisible = true
        binding.videoPKLayout.iBroadcasterAView.removeAllViews()
        binding.videoPKLayout.iBroadcasterBView.removeAllViews()
        if (isBroadcaster) {
            binding.layoutVideoContainer.removeAllViews()
            binding.layoutVideoContainer.addView(localTexture)
        } else {
            binding.layoutVideoContainer.removeAllViews()
            binding.layoutVideoContainer.addView(ownerRemoteTexture)
        }
    }

    private fun startPushToAgora() {
        val stringUid = channelName
        val channelUid = ChannelUid(channelName, channelName.toIntOrNull() ?: -1, stringUid)
        AgoraRtcEngineInstance.agoraZegoTranscoder.startZegoStreamWithTranscoding(
            ZegoTranscodeSetting.cloudZegoTranscoding(
                enableUserAccount = RtcSettings.mEnableUserAccount,
                rtcStringUid = stringUid,
                zegoAppId = BuildConfig.ZEGO_APP_ID.toLong(),
                zegoAppSign = BuildConfig.ZEGO_APP_SIGN,
                zegoRoomId = channelName,
                zegoUser = ZegoUser(ZegoEngineInstance.userId),
                zegoPublishStreamId = channelName,
                agoraChannel = channelUid
            ),
            completion = { succeed, code, message ->
                if (succeed) {
                    ToastUtils.showShort("push to agora success")
                } else {
                    ToastUtils.showShort("push to agora error: $code, $message")
                }
            }
        )
    }

    private fun stopPushToZego() {
        AgoraRtcEngineInstance.agoraZegoTranscoder.stopRtmpStream(channelName, null)
    }
}