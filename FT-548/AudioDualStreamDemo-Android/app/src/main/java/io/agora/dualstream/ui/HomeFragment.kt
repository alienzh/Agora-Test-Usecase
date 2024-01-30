package io.agora.dualstream.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import io.agora.dualstream.R
import io.agora.dualstream.databinding.FragmentHomeBinding
import io.agora.rtc2.Constants

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etChannelId.setRightIconClickListener {
            binding.etChannelId.text = ""
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }

        })
        binding.btnJoinChannel.setOnClickListener {
            val channel = binding.etChannelId.text
            if (channel.isEmpty()) {
                Toast.makeText(requireContext(), "channel id cannot be empty!", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            val role =
                if (binding.tabLayout.selectedTabPosition == 0) Constants.CLIENT_ROLE_AUDIENCE else Constants.CLIENT_ROLE_BROADCASTER
            findNavController().navigate(R.id.action_homeFragment_to_liveFragment, Bundle().apply {
                putString(LiveFragment.KEY_CHANNEL, channel)
                putInt(LiveFragment.KEY_ROLE, role)
            })
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}