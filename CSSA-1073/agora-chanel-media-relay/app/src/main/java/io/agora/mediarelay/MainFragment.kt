package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.navigation.fragment.findNavController
import io.agora.mediarelay.baseui.BaseUiFragment
import io.agora.mediarelay.databinding.FragmentMainBinding
import io.agora.mediarelay.tools.ToastTool
import io.agora.rtc2.Constants

/**
 * @author create by zhangwei03
 */
class MainFragment : BaseUiFragment<FragmentMainBinding>() {
    companion object {
        private const val TAG = "MainFragment"
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentMainBinding {
        return FragmentMainBinding.inflate(inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btSubmit.setOnClickListener {
            val channelId = binding.etChannel.text.toString()
            if (checkChannelId(channelId)) return@setOnClickListener
            val isBroadcaster = binding.radioParent.checkedRadioButtonId == R.id.radio_broadcaster
            val args = Bundle().apply {
                putString(LivingFragment.KEY_CHANNEL_ID, channelId)
                putInt(
                    LivingFragment.KEY_ROLE,
                    if (isBroadcaster) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
                )
            }
            findNavController().navigate(R.id.action_mainFragment_to_livingFragment, args)
        }
    }

    private fun checkChannelId(channelId: String): Boolean {
        if (channelId.isEmpty()){
            ToastTool.showToast("Please enter channel id")
            return true
        }
        return false
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