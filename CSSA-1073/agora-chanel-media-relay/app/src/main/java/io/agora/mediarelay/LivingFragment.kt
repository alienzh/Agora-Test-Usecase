package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentLivingBinding
import io.agora.rtc2.Constants

/**
 * @author create by zhangwei03
 */
class LivingFragment : BaseUiFragment<FragmentLivingBinding>() {
    companion object {
        private const val TAG = "LivingFragment"

        const val KEY_CHANNEL_ID: String = "key_channel_id"
        const val KEY_ROLE: String = "key_role"
    }

    private val channelName by lazy { arguments?.getString(KEY_CHANNEL_ID) ?: "" }
    private val role by lazy {
        arguments?.getInt(KEY_ROLE, Constants.CLIENT_ROLE_BROADCASTER) ?: Constants.CLIENT_ROLE_BROADCASTER
    }

    @Volatile
    private var isInPk: Boolean = false

    @Volatile
    private var isCdnAudience: Boolean = false

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLivingBinding {
        return FragmentLivingBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        if (role == Constants.CLIENT_ROLE_BROADCASTER) {
            binding.layoutChannel.isVisible = true
            binding.btSubmitPk.isVisible = true
            binding.btSwitchStream.isVisible = false
        } else {
            binding.layoutChannel.isVisible = false
            binding.btSubmitPk.isVisible = false
            binding.btSwitchStream.isVisible = true
        }
        binding.tvChannelId.text = "ChannelId:$channelName"
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.btSubmitPk.setOnClickListener {
            if (isInPk) {
                showToast("end Pk")
                binding.btSubmitPk.text = getString(R.string.start_pk)
            } else {
                val channelId = binding.etPkChannel.text.toString()
                if (checkChannelId(channelId)) return@setOnClickListener
                showToast("start Pk")
                binding.btSubmitPk.text = getString(R.string.end_pk)
            }
            isInPk = !isInPk
            binding.videoPKLayout.videoContainer.isVisible = isInPk
            binding.layoutVideoContainer.isVisible = !isInPk
        }
        binding.btSwitchStream.setOnClickListener {
            if (isCdnAudience) {
                showToast("switch rtc audience")
                binding.btSwitchStream.text = getString(R.string.rtc_audience)
            } else {
                showToast("switch cdn audience")
                binding.btSwitchStream.text = getString(R.string.cdn_audience)
            }
            isCdnAudience = !isCdnAudience
        }
    }

    private fun checkChannelId(channelId: String): Boolean {
        return channelId.isEmpty().also {
            showToast("Please enter pk channel id")
        }
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
        super.onDestroy()
    }
}