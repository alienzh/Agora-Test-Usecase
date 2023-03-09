package io.agora.mediarelay

import android.os.Bundle
import android.view.LayoutInflater
import io.agora.mediarelay.baseui.BaseUiActivity
import io.agora.mediarelay.databinding.ActivityMainBinding
import io.agora.mediarelay.tools.PermissionHelp

/**
 * @author create by zhangwei03
 */
class MainActivity : BaseUiActivity<ActivityMainBinding>() {

    internal lateinit var permissionHelp: PermissionHelp

    override fun getViewBinding(inflater: LayoutInflater): ActivityMainBinding {
        return ActivityMainBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionHelp = PermissionHelp(this)
    }
}