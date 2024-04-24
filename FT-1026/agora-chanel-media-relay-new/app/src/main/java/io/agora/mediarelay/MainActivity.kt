package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import io.agora.mediarelay.baseui.BaseUiActivity
import io.agora.mediarelay.databinding.ActivityMainBinding
import io.agora.mediarelay.tools.FileUtils
import io.agora.mediarelay.tools.PermissionHelp

/**
 * @author create by zhangwei03
 */
class MainActivity : BaseUiActivity<ActivityMainBinding>() {

    val permissionHelp: PermissionHelp = PermissionHelp(this)

    override fun getViewBinding(inflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        FileUtils.copyFileFromAssets(this, "black.png", null)
    }
}