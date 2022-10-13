package com.qy.log.recorder.demo

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qy.encryptor.QyEncryptor
import com.qy.log.recorder.QyLogRecorder
import com.qy.tool.utils.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val showBtn = findViewById<TextView>(R.id.showBtn)
        val showMsgT = findViewById<TextView>(R.id.showMsgT)
        val infoMsgT = findViewById<TextView>(R.id.infoMsgT)

        showBtn.text = "当前${if(QyLogRecorder.getInstance().isCanRecordLog()) "允许" else "禁止"}记录日志"
        QyLogRecorder.getInstance().setOnChangeCanRecordLogCallback(object : QyLogRecorder.OnChangeCanRecordLogCallback{
            override fun onChangeCanRecordLog(isCanRecordLog: Boolean) {
                showBtn.text = "当前${if(isCanRecordLog) "允许" else "禁止"}记录日志"
            }
        })
        showBtn.setOnClickListener {
            QyLogRecorder.getInstance().updateDirectRecordLog(!QyLogRecorder.getInstance().isCanRecordLog())
        }

        findViewById<View>(R.id.printLogT).setOnClickListener {
            showMsgT.text = "==========>打印日志中,可查看Logcat..."
            Thread {
                for (i in 0..1000) {
                    var logStr = "==========>当前写入日志索引:$i"
                    println("======Stephen=======before====>$logStr")
                    QyLogRecorder.getInstance().appendSelfLog(logStr) { isSuccess, msg ->
                        println("======Stephen======after=====>$logStr========>$isSuccess====>$msg")
                    }
                }
                runOnUiThread{
                    showMsgT.text = "==========>打印日志:完成"
                }
            }.start()
        }

        findViewById<View>(R.id.shareLogT).setOnClickListener {
            QyLogRecorder.getInstance().shareSelfLogBySystem()
        }

        findViewById<View>(R.id.deleteLogT).setOnClickListener {
            toastMsg("==========>删除日志文件:${QyLogRecorder.getInstance().deleteSelfLogFile()}")
        }

        findViewById<View>(R.id.existsLogT).setOnClickListener {
            toastMsg("==========>判定日志文件:${QyLogRecorder.getInstance().isSelfLogFileExists()}")
        }

        var encrtotVal = ""
        findViewById<View>(R.id.encryptTxtT).setOnClickListener {
            var encryptStr = "奇游加速器Nb,No.1!"
            encrtotVal = QyEncryptor.methodForEn(encryptStr)
            println("======Stephen===========>encrtotVal:$encrtotVal")
            infoMsgT.text = "原始串:${encryptStr}\n加密串:${encrtotVal}"
        }

        findViewById<View>(R.id.dencryptTxtT).setOnClickListener {
            if(encrtotVal.isNullOrBlank()){
                toastMsg("请先加密")
                return@setOnClickListener
            }//end of if
            infoMsgT.text = "加密串:${encrtotVal}\n解密串:${QyEncryptor.methodForDe(encrtotVal)}"
        }
    }

    private fun toastMsg(msg: String){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}