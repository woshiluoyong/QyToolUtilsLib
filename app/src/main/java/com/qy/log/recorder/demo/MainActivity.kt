package com.qy.log.recorder.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.qy.log.recorder.LogFileUtils
import com.qy.tool.utils.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LogFileUtils.getInstance().setOnChangeLogUploadUiCallback(object : LogFileUtils.OnChangeLogUploadUiCallback{
            override fun onChangeLogUploadUi(isShow: Boolean) {
                println("======Stephen=======onChangeLogUploadUi====>$isShow")
            }
        })
        findViewById<View>(R.id.showBtn).setOnClickListener {
            LogFileUtils.getInstance().updateDirectCanRecordLog()
        }
        val showMsgT = findViewById<TextView>(R.id.showMsgT)
        findViewById<View>(R.id.printLogT).setOnClickListener {
            toastMsg("==========>打印日志中,可查看Logcat...")
            for (i in 0..1000){
                var logStr = "==========>当前写入日志索引:$i"
                println("======Stephen=======before====>$logStr")
                LogFileUtils.getInstance().appendSelfLog(logStr){isSuccess, msg ->
                    println("======Stephen======after=====>$logStr========>$isSuccess====>$msg")
                }
            }
            showMsgT.text = "==========>打印日志:完成"
        }

        findViewById<View>(R.id.shareLogT).setOnClickListener {
            LogFileUtils.getInstance().shareSelfLogBySystem()
        }

        findViewById<View>(R.id.deleteLogT).setOnClickListener {
            toastMsg("==========>删除日志文件:${LogFileUtils.getInstance().deleteSelfLogFile()}")
        }

        findViewById<View>(R.id.existsLogT).setOnClickListener {
            toastMsg("==========>判定日志文件:${LogFileUtils.getInstance().isSelfLogFileExists()}")
        }
    }

    private fun toastMsg(msg: String){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}