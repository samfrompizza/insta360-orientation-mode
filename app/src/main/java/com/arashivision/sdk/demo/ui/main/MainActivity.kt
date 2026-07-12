package com.arashivision.sdk.demo.ui.main

import android.os.Bundle
import com.arashivision.sdk.demo.R
import com.arashivision.sdk.demo.base.BaseActivity
import com.arashivision.sdk.demo.base.BaseEvent
import com.arashivision.sdk.demo.databinding.ActivityMainBinding
import com.arashivision.sdk.demo.ui.connect.ConnectFragment
import com.arashivision.sdk.demo.ui.main.MainEvent.PermissionDeniedEvent
import com.arashivision.sdk.demo.ui.main.MainEvent.PermissionGrantedEvent
import com.arashivision.sdk.demo.ui.setting.SettingFragment

class MainActivity :
    BaseActivity<ActivityMainBinding, MainViewModel>(
        bindingFactory = { ActivityMainBinding.inflate(it) },
        viewModelClass = MainViewModel::class.java,
    ) {

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.checkPermission(this)
    }

    override fun initView() {
        super.initView()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, ConnectFragment())
            .commit()
    }

    public override fun onEvent(event: BaseEvent) {
        super.onEvent(event)

        if (event is PermissionGrantedEvent) {
            toast(R.string.toast_permission_request_complete)
        } else if (event is PermissionDeniedEvent) {
            toast(R.string.toast_permission_request_failed, true)
        }
    }
}
