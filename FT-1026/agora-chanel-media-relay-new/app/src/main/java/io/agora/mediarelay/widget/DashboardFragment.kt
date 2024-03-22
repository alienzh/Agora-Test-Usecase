package io.agora.mediarelay.widget

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import io.agora.mediarelay.R
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentDashboardBinding
import io.agora.mediarelay.rtc.AgoraRtcEngineInstance
import io.agora.mediarelay.rtc.IVideoInfoListener
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler

class DashboardFragment : BaseUiFragment<FragmentDashboardBinding>(), IVideoInfoListener {

    private var mIsOn = false

    private var localStats: IRtcEngineEventHandler.LocalVideoStats? = null

    private var remoteStats: IRtcEngineEventHandler.RemoteVideoStats? = null

    private var upInfo: IRtcEngineEventHandler.UplinkNetworkInfo? = null

    private var downInfo: IRtcEngineEventHandler.DownlinkNetworkInfo? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentDashboardBinding {
        return FragmentDashboardBinding.inflate(inflater)
    }

    override fun onDestroy() {
        AgoraRtcEngineInstance.setVideoInfoListener(null)
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AgoraRtcEngineInstance.setVideoInfoListener(this)
    }

    fun setOn(isOn: Boolean) {
        mIsOn = isOn
    }

    private fun updateView() {
        if (!mIsOn) return
        localStats?.let { stats ->
            binding.tvStatisticUpBitrate.text = getString(R.string.dashboard_up_bitrate, stats.sentBitrate.toString())
            binding.tvStatisticEncodeFPS.text = getString(R.string.dashboard_encode_fps, stats.encoderOutputFrameRate.toString())
            binding.tvStatisticUpLossPackage.text = getString(R.string.dashboard_up_loss_package, stats.txPacketLossRate.toString())
            binding.tvEncodeResolution.text = getString(R.string.dashboard_encode_resolution, "${stats.encodedFrameHeight}x${stats.encodedFrameWidth}")
        }
//        remoteStats?.let { stats ->
//            binding.tvStatisticDownBitrate.text = getString(R.string.dashboard_down_bitrate, stats.receivedBitrate.toString())
//            binding.tvStatisticReceiveFPS.text = getString(R.string.dashboard_receive_fps, stats.decoderOutputFrameRate.toString())
//            binding.tvStatisticDownLossPackage.text = getString(R.string.dashboard_down_loss_package, stats.packetLossRate.toString())
//            binding.tvReceiveResolution.text = getString(R.string.dashboard_receive_resolution, "${stats.height}x${stats.width}")
//            binding.tvStatisticDownDelay.text = getString(R.string.dashboard_delay, stats.delay.toString())
//        }
        upInfo?.let { info ->
            val bps = info.video_encoder_target_bitrate_bps
            binding.tvStatisticUpNet.text = getString(R.string.dashboard_up_net_speech, (bps / 8192).toString())
        }
//        downInfo?.let { info ->
//            val bps = info.bandwidth_estimation_bps
//            binding.tvStatisticDownNet.text = getString(R.string.dashboard_down_net_speech, (bps / 8192).toString())
//        }
        if (binding.tvStatisticUpBitrate.text.isEmpty()) binding.tvStatisticUpBitrate.text = getString(R.string.dashboard_up_bitrate, "--")
        if (binding.tvStatisticEncodeFPS.text.isEmpty()) binding.tvStatisticEncodeFPS.text = getString(R.string.dashboard_encode_fps, "--")
        if (binding.tvStatisticUpLossPackage.text.isEmpty()) binding.tvStatisticUpLossPackage.text = getString(R.string.dashboard_up_loss_package, "--")
        if (binding.tvEncodeResolution.text.isEmpty()) binding.tvEncodeResolution.text = getString(R.string.dashboard_encode_resolution, "--")
//        if (binding.tvStatisticDownBitrate.text.isEmpty()) binding.tvStatisticDownBitrate.text = getString(R.string.dashboard_down_bitrate, "--")
//        if (binding.tvStatisticReceiveFPS.text.isEmpty()) binding.tvStatisticReceiveFPS.text = getString(R.string.dashboard_receive_fps, "--")
//        if (binding.tvStatisticDownLossPackage.text.isEmpty()) binding.tvStatisticDownLossPackage.text = getString(R.string.dashboard_down_loss_package, "--")
//        if (binding.tvReceiveResolution.text.isEmpty()) binding.tvReceiveResolution.text = getString(R.string.dashboard_receive_resolution, "--")
//        if (binding.tvStatisticDownDelay.text.isEmpty()) binding.tvStatisticDownDelay.text = getString(R.string.dashboard_delay, "--")
        if (binding.tvStatisticUpNet.text.isEmpty()) binding.tvStatisticUpNet.text = getString(R.string.dashboard_up_net_speech, "--")
//        if (binding.tvStatisticDownNet.text.isEmpty()) binding.tvStatisticDownNet.text = getString(R.string.dashboard_down_net_speech, "--")
    }

    override fun onLocalVideoStats(
        source: Constants.VideoSourceType?,
        stats: IRtcEngineEventHandler.LocalVideoStats
    ) {
        localStats = stats
        runOnMainThread { updateView() }
    }

    override fun onRemoteVideoStats(stats: IRtcEngineEventHandler.RemoteVideoStats) {
        remoteStats = stats
        runOnMainThread { updateView() }
    }

    override fun onUplinkNetworkInfoUpdated(info: IRtcEngineEventHandler.UplinkNetworkInfo?) {
        upInfo = info
        runOnMainThread { updateView() }
    }

    override fun onDownlinkNetworkInfoUpdated(info: IRtcEngineEventHandler.DownlinkNetworkInfo?) {
        downInfo = info
        runOnMainThread { updateView() }
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