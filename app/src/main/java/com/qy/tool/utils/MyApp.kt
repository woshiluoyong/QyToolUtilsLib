package com.qy.tool.utils

import android.app.Application
import com.qy.tool.utilslib.LogFileUtils

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        LogFileUtils.getInstance().appendSelfLog("Application初始化,我提前打印日志")
        LogFileUtils.getInstance().init(this)
    }
}