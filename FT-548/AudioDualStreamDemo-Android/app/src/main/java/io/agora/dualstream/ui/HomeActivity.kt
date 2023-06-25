package io.agora.dualstream.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import io.agora.dualstream.databinding.ActivityMainBinding
import io.agora.dualstream.utils.PermissionHelp

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        PermissionHelp(this).checkMicPerm({
        }, {}, true)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
    }
}