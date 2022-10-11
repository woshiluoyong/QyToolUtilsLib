package com.qy.log.recorder.demo

import android.app.Application
import com.qy.log.recorder.QyLogRecorder

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        QyLogRecorder.getInstance().appendSelfLog("Application初始化,我提前打印日志")
        QyLogRecorder.getInstance().init(this)
    }
}