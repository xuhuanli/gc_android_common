package com.gancao.gc_android_common

import android.app.Application
import com.weikaiyun.fragmentation.Fragmentation
import com.weikaiyun.fragmentation.R

/**
 * Copyright (c) 2025-05, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Fragmentation
            .builder()
            .stackViewMode(Fragmentation.BUBBLE)
            .debug(true)
            .animation(
                R.anim.v_fragment_enter,
                R.anim.v_fragment_pop_exit,
                R.anim.v_fragment_pop_enter,
                R.anim.v_fragment_exit
            ) //设置默认动画
            .install()
    }
}