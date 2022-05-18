package com.qy.tool.utils

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.qy.tool.utilslib.LogFileUtils

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        LogFileUtils.getInstance().updateCheckCanRecordLog("1,2", "1", "2")
        val showMsgT = findViewById<TextView>(R.id.showMsgT)
        findViewById<View>(R.id.printLogT).setOnClickListener {
            toastMsg("==========>打印日志中,可查看Logcat...")
            for (i in 0..10000){
                var logStr = "==========>当前写入日志索引:$i"
                println("======Stephen=======before====>$logStr")
                LogFileUtils.getInstance().appendSelfLog(logStr){isSuccess, msg ->
                    println("======Stephen======after=====>$logStr========>$isSuccess====>$msg")
                }
            }
            showMsgT.text = "==========>打印日志:完成"
        }
    }

    private fun toastMsg(msg: String){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}