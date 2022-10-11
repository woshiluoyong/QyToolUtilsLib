package com.qy.log.recorder

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.StrictMode
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.qy.encryptor.QyEncryptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

//日志记录器
class QyLogRecorder private constructor() {
    private var application: Application? = null
    private var isInitializer: Boolean = false
    private val cacheNoInitLogList = arrayListOf<String>()
    private var isCanRecordLog: Boolean = false
    private val threadPool: ExecutorService = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, ArrayBlockingQueue(10))
    private var limitMemorySize: Int = 200//限制先在内存集合里的条数
    private var userCustomInfo: String? = "nothing"//上传日志名中的自定义信息
    private var commitNeedToast: Boolean? = true//上传日志是否需要toast
    private var commitNeedLoading: Boolean? = true//上传日志是否需要loading
    private var commitServerHost: String? = "https://openapi.qiyou.cn"//上传日志服务域名
    private var commitServerAddr: String? = "/api/common_bll/v1/external/reportLog/log/action/upload"//上传日志服务地址
    private var loadingDialog: AlertDialog? = null
    private var isCommitLogging = false//是否提交日志中
    private var onChangeLogUploadUiCallback: OnChangeLogUploadUiCallback? = null
    private var uploadLogBtn: DragFloatTextView? = null
    private var curActivity: Activity? = null
    private val cacheShowActivityList = arrayListOf<Activity>()

    companion object {
        const val ConfigKeyName = "CAN_UPLOAD_LOG_CONFIG"//可用于后台配置的用户标识key
        private var mInstance: QyLogRecorder? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): QyLogRecorder {
            if(mInstance == null) mInstance = QyLogRecorder()
            return mInstance!!
        }
    }

    //初始化
    fun init(application: Application, limitMemorySize: Int? = 200, userCustomInfo: String? = null, commitNeedToast: Boolean? = null, commitNeedLoading: Boolean? = null,
             commitServerHost: String? = null, commitServerAddr: String? = null){
        this.application = application
        this.limitMemorySize = if(null == limitMemorySize || limitMemorySize < 200) 200 else limitMemorySize
        updateSetUserCustomInfo(userCustomInfo)
        commitNeedToast?.let { this.commitNeedToast = it }
        commitNeedLoading?.let { this.commitNeedLoading = it }
        commitServerHost?.let { this.commitServerHost = it }
        commitServerAddr?.let { this.commitServerAddr = it }
        println("======Stephen=LogFileUtils====init====>初始化日志工具")
        application!!.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks{
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                curActivity = activity
                changeLogUploadUi(isCanRecordLog)
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
        //在Android 7.0及以上系统，限制了file域的访问，导致进行intent分享的时候，会报错甚至崩溃。我们需要在App启动的时候在Application的onCreate方法中添加如下代码，解除对file域访问的限制
        if (VERSION.SDK_INT >= 24) StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
    }

    //设置自定义上传按钮事件回调(如果设置了将不显示默认的上传悬浮按钮,注意,必须设置在updateCheckCanRecordLog方法调用前)
    fun setOnChangeLogUploadUiCallback(onChangeLogUploadUiCallback: OnChangeLogUploadUiCallback? = null){
        this.onChangeLogUploadUiCallback = onChangeLogUploadUiCallback
    }

    //直接允许记录日志
    fun updateDirectCanRecordLog(){
        updateCheckCanRecordLog("allowRecordLog", "allowRecordLog", "allowRecordLog")
    }

    //更新检查标志,主要判断是否允许记录日志到文件(uid或deviceId其一匹配上后台配置的配置参数(默认逗号分隔)就显示入口),调用处主要为获取配置参数后及登录后设置uid
    fun updateCheckCanRecordLog(configUidOrDeviceIdStr: String?, curUid: String?, curDeviceId: String?, separator: String? = ","){
        if(null == application){
            println("======Stephen=LogFileUtils====Err====>application is Empty:重要,你没有调用init初始化日志工具!!")
            return
        }//end of if
        checkCanRecordLogCore(configUidOrDeviceIdStr, curUid, curDeviceId, if(separator.isNullOrBlank()) "," else separator)
        isInitializer = true
        checkInitLogUploadUi()
        changeLogUploadUi(isCanRecordLog)
        if(isCanRecordLog){
            appendSelfLogCore(cacheNoInitLogList.clone() as List<String>)//存储前面缓存的
            cacheNoInitLogList.clear()
        }//end of if
    }

    private fun checkCanRecordLogCore(configUidOrDeviceIdStr: String?, curUid: String?, curDeviceId: String?, separator: String? = ","){
        isCanRecordLog = false
        if(configUidOrDeviceIdStr.isNullOrBlank() || (curUid.isNullOrBlank() && curDeviceId.isNullOrBlank()))return
        configUidOrDeviceIdStr?.let { tmpConfigUidOrDeviceIdStr ->
            var configUidOrDeviceIdAry = arrayListOf(tmpConfigUidOrDeviceIdStr)
            if(tmpConfigUidOrDeviceIdStr.contains(separator!!))configUidOrDeviceIdAry = tmpConfigUidOrDeviceIdStr.split(separator) as ArrayList<String>
            configUidOrDeviceIdAry?.forEach {
                curUid?.run { if(this == it) isCanRecordLog = true }
                curDeviceId?.run { if(this == it) isCanRecordLog = true }
            }
        }
        println("======Stephen=LogFileUtils====checkCanRecordLog====>isCanRecordLog:$isCanRecordLog")
    }

    fun isCanRecordLog(): Boolean = isCanRecordLog

    //一般在登录处设置登录用户信息
    fun updateSetUserCustomInfo(userCustomInfo: String?){
        userCustomInfo?.let{ this.userCustomInfo = it }
    }

    /*********************上传悬浮按钮UI Start***************************/
    //log上传入口开关初始化
    private fun checkInitLogUploadUi(){
        if(null == application || !isCanRecordLog || null != uploadLogBtn)return
        if(null != onChangeLogUploadUiCallback)return//自定义了log日志显示事件就不显示内置ui
        uploadLogBtn = DragFloatTextView(application!!.applicationContext)
        uploadLogBtn?.run {
            text = "上传日志"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.parseColor("#614B18"))
            isSingleLine = false
            setPadding(dip2px(10f), dip2px(0f), dip2px(10f), dip2px(0f))
            setOnClickListener {
                if(null == curActivity || curActivity!!.isFinishing || curActivity!!.isDestroyed){
                    commitSelfLog(userCustomInfo, commitNeedToast, commitNeedLoading, commitServerHost, commitServerAddr)
                    return@setOnClickListener
                }//end of if
                try {
                    val builder = AlertDialog.Builder(curActivity)
                    builder.setTitle("请确认")
                    builder.setMessage("你确认提交本地日志给开发者吗?")
                    builder.setPositiveButton("提交") { _, _ ->
                        commitSelfLog(userCustomInfo, commitNeedToast, commitNeedLoading, commitServerHost, commitServerAddr)
                    }
                    builder.setNegativeButton("放弃"){_,_->}
                    builder.setCancelable(true)
                    val dialog = builder.create()
                    dialog.show()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.GREEN)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun changeLogUploadUi(isShow: Boolean){
        hideFromAppTopView(uploadLogBtn)
        if(null != onChangeLogUploadUiCallback){
            onChangeLogUploadUiCallback!!.onChangeLogUploadUi(isShow)
            return
        }//end of if
        if(isShow)showFromAppTopView(curActivity, uploadLogBtn)
    }

    private fun showFromAppTopView(activity: Activity?, view: View?) {
        try {
            if (null == view) return
            activity?.let {
                if (!it.isDestroyed) { //Activity不为空并且没有被释放掉
                    val root = it.window.decorView as ViewGroup //获取Activity顶层视图
                    if (null != root) {
                        val params = FrameLayout.LayoutParams(dip2px(64f), dip2px(64f))
                        params.bottomMargin = dip2px(150f)
                        params.marginEnd = 0
                        params.gravity = Gravity.END or Gravity.BOTTOM
                        root.addView(view, params)
                        cacheShowActivityList.add(it)
                    } // end of if
                } // end of if
            }
        } catch (e: java.lang.Exception) {
            println("======Stephen=LogFileUtils====showOrHideFromAppTopView==show==>err:${e.message}")
        }
    }

    private fun hideFromAppTopView(view: View?) {
        try {
            if (null == view) return
            cacheShowActivityList.forEach {
                it?.let {
                    if (!it.isDestroyed) { //Activity不为空并且没有被释放掉
                        val root = it.window.decorView as ViewGroup //获取Activity顶层视图
                        if (null != root) {
                            val index = root.indexOfChild(view)
                            if (-1 != index) root.removeViewAt(index)
                        } // end of if
                    } // end of if
                }
            }
            cacheShowActivityList.clear()
        } catch (e: java.lang.Exception) {
            println("======Stephen=LogFileUtils====showOrHideFromAppTopView==hide==>err:${e.message}")
        }
    }

    /*********************上传悬浮按钮UI End***************************/

    private fun getSelfLogFolder(): String?{
        if(null == application?.externalCacheDir)return null
        return "${application!!.externalCacheDir!!.path}/Log"
    }

    private fun getSelfLogName(): String?{
        if(application?.packageName.isNullOrBlank())return null
        return "${application!!.packageName}_log.txt"
    }

    //获取日志文件
    fun getSelfLogFile(isCheckCreate: Boolean? = false): File?{
        if(null == application?.externalCacheDir)return null
        try {
            var selfLogFile = File(getSelfLogFolder(), getSelfLogName())
            if (isCheckCreate!! && (null == selfLogFile || !selfLogFile!!.exists())) {
                try {
                    val dirs = File(getSelfLogFolder())
                    if (!dirs.exists()) dirs.mkdirs()
                    selfLogFile = File(dirs.absolutePath, getSelfLogName())
                    selfLogFile.createNewFile()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }//end of if
            return selfLogFile
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    //日志文件是否存在
    fun isSelfLogFileExists(): Boolean?{
        if(null == application?.externalCacheDir)return null
        try {
            val selfLogFile: File? = File(getSelfLogFolder(), getSelfLogName())
            return selfLogFile?.exists()
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    //删除日志文件
    fun deleteSelfLogFile(): Boolean{
        if(null == application?.externalCacheDir)return false
        try {
            val file = getSelfLogFile() ?: return false
            return file.delete()
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    //追加日志记录
    fun appendSelfLog(logMsgStr: String?, eventCallback: ((isSuccess: Boolean, msg: String?) -> Unit)? = null){
        if(logMsgStr.isNullOrBlank()){
            eventCallback?.let { it(false, "logMsg is empty") }
            return
        }//end of if
        val logMsg = QyEncryptor.methodForEn("Date:${generateCurrentDate()},LogStr:$logMsgStr")
        if(!isInitializer){//未初始化时先缓存起来,避免缺失前面的日志
            println("======Stephen=LogFileUtils====appendSelfLog====>未初始化时先缓存起来")
            cacheNoInitLogList.add("${logMsg}\n")
            return
        }//end of if
        if(null == application?.externalCacheDir){
            eventCallback?.let { it(false, "application or application.externalCacheDir is null") }
            return
        }//end of if
        if(!isCanRecordLog){
            eventCallback?.let { it(false, "isCanRecordLog is false") }
            return
        }//end of if
        if(cacheNoInitLogList.size < limitMemorySize){//避免频繁开关输出流导致oom
            cacheNoInitLogList.add("${logMsg}\n")
            return
        }//end of if
        appendSelfLogCore(cacheNoInitLogList.clone() as List<String>, logMsg, eventCallback)
        cacheNoInitLogList.clear()
    }

    private fun appendSelfLogCore(cacheLogList: List<String>, logMsg: String? = null, eventCallback: ((isSuccess: Boolean, msg: String?) -> Unit)? = null){
        try {
            threadPool.execute {
                val selfLogFile: File? = getSelfLogFile(true)
                if (true == isSelfLogFileExists()) {
                    println("======Stephen=LogFileUtils====appendSelfLog====>执行一次写入文件操作")
                    var fileOutputStream: FileOutputStream? = null
                    try {
                        fileOutputStream = FileOutputStream(selfLogFile, true)
                        if (cacheLogList.isNotEmpty()) {
                            cacheLogList.forEach {
                                fileOutputStream?.write(it.toByteArray())
                            }
                        }//end of if
                        logMsg?.let{ fileOutputStream?.write("${it}\n".toByteArray()) }
                        eventCallback?.let { it(true, "OK") }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val msg = "追加日志到文件异常:${e.message}"
                        eventCallback?.let { it(false, msg) }
                        println("======Stephen=LogFileUtils=====appendSelfLog======>$msg")
                    } finally {
                        if (null != fileOutputStream) {
                            try {
                                fileOutputStream.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            fileOutputStream = null
                        }//end of if
                    }
                } else {
                    val msg = "日志文件为空,追加日志到文件失败"
                    eventCallback?.let { it(false, msg) }
                    println("======Stephen=LogFileUtils=====appendSelfLog======>$msg")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            println("======Stephen=LogFileUtils=====appendSelfLog====>exception:${e.message}")
        }
    }

    fun commitSelfLog(onLogUploadEventCallback: OnLogUploadEventCallback? = null){
        commitSelfLog(onLogUploadEventCallback)
    }

    //上报日志记录
    fun commitSelfLog(userCustomInfo: String? = "nothing", isNeedToast: Boolean? = true, isNeedLoading: Boolean? = true, commitServerHost: String? = "https://openapi.qiyou.cn",
                      commitServerAddr: String? = "/api/common_bll/v1/external/reportLog/log/action/upload", onLogUploadEventCallback: OnLogUploadEventCallback? = null) {
        if(!isCanRecordLog)return
        try {
            if(null == application){
                val msg = "application is empty"
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                onLogUploadEventCallback?.let { it.onLogUploadEnd(false,-1, msg) }
                return
            }//end of if
            if(null == application!!.externalCacheDir || commitServerHost.isNullOrBlank()){
                val msg = "key data is empty, please init setting"
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                onLogUploadEventCallback?.let { it.onLogUploadEnd(false,-2, msg) }
                return
            }//end of if
            if(isCommitLogging){
                val msg = "正在提交中，请耐心等待完成提示..."
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                onLogUploadEventCallback?.let { it.onLogUploadEnd(false,-3, msg) }
                return
            }//end of if
            appendSelfLogCore(cacheNoInitLogList.clone() as List<String>)//补上剩下的
            cacheNoInitLogList.clear()
            if(true == isSelfLogFileExists()){
                if(isNeedLoading!!)showLoadingDialog()
                isCommitLogging = true
                val msg = "上报日志记录开始,如果日志过大,将比较慢,期间请勿操作,请耐心等待完成提示..."
                if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                onLogUploadEventCallback?.let { it.onLogUploadStart(msg) }
                val newLogFile = File(getSelfLogFolder(), String.format("%s_%s_%s_log.txt", generateCurrentDate(), application!!.packageName, userCustomInfo))
                getSelfLogFile()?.renameTo(newLogFile)
                doUpload("${commitServerHost}$commitServerAddr", newLogFile!!.name, newLogFile!!.readBytes()){ isSuccess, resCode, infoStr ->
                    var msg = "上报日志记录成功"
                    if (isSuccess) {
                        isCommitLogging = false
                        onLogUploadEventCallback?.let { it.onLogUploadEnd(true,0, msg) }
                    } else {
                        msg = "上报日志记录失败!(${resCode})${infoStr}"
                        try {
                            /*if (newLogFile!!.length() > 8 * 1024 * 1024) {//服务器上传大小上限8mb
                                try { newLogFile?.delete() } catch (e: Exception) { e.printStackTrace() }
                            } else {

                            }*/
                            newLogFile.renameTo(getSelfLogFile())//恢复回原名字
                        } catch (e: Exception) { e.printStackTrace() }
                        isCommitLogging = false
                        onLogUploadEventCallback?.let { it.onLogUploadEnd(false,-6, msg) }
                    }
                    println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                    if(isNeedToast!!) CoroutineScope(Dispatchers.Main).launch { Toast.makeText(application, msg, Toast.LENGTH_LONG).show() }
                    dismissLoadingDialog()
                }
            }else{
                val msg = "日志记录文件不存在,请先正常操作产生日志"
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                onLogUploadEventCallback?.let { it.onLogUploadEnd(false,-4, msg) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "上报日志发生异常:${e.message}"
            println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
            if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
            onLogUploadEventCallback?.let { it.onLogUploadEnd(false,-5, msg) }
        }
    }

    private fun showLoadingDialog() {
        if(null == curActivity || curActivity!!.isFinishing || curActivity!!.isDestroyed)return
        loadingDialog = AlertDialog.Builder(curActivity).create()
        loadingDialog?.window?.run {
            setBackgroundDrawable(ColorDrawable())
        }
        loadingDialog?.setCancelable(false)
        loadingDialog?.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_BACK }
        loadingDialog?.show()
        val loadingT = TextView(curActivity)
        loadingT.run {
            setBackgroundColor(Color.BLACK)
            text = "上传日志中..."
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.GRAY)
            isSingleLine = false
            setPadding(dip2px(10f), dip2px(5f), dip2px(10f), dip2px(5f))
        }
        val contentV = RelativeLayout(curActivity)
        val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.addRule(RelativeLayout.CENTER_IN_PARENT)
        contentV.addView(loadingT, lp)
        loadingDialog?.setContentView(contentV)
        loadingDialog?.setCanceledOnTouchOutside(false)
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }

    private fun dip2px(dpValue: Float): Int {
        if(null == application)return dpValue.toInt()
        val scale = application!!.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    private fun generateCurrentDate(): String = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault()).format(Date())

    private fun doUpload(urlString: String, fileName: String, fileByteAry: ByteArray?, headerMap: Map<String?, String?>? = null,
                         paramMap: Map<String?, String?>? = null, listener: ((isSuccess: Boolean, resCode: Int, infoStr: String) -> Unit)? = null) {
        println("======Stephen=LogFileUtils==doUpload===>请求Url:$urlString===请求数据:$fileName,fileByteAry length:" + (fileByteAry?.size ?: -1) + ",paramMap size:" + (paramMap?.size ?: -1))
        try {
            threadPool.execute {
                val url: URL
                var httpURLConnection: HttpURLConnection? = null
                var resCode = -1
                try {
                    val newLine = "\r\n" // 换行符
                    val boundaryPrefix = "--" //边界前缀
                    val boundary = String.format("=========%s", System.currentTimeMillis())// 定义数据分隔线
                    url = URL(urlString)
                    httpURLConnection = url.openConnection() as HttpURLConnection
                    httpURLConnection.requestMethod = "POST"
                    // 发送POST请求必须设置如下两行
                    httpURLConnection!!.doOutput = true
                    httpURLConnection.doInput = true
                    httpURLConnection.useCaches = false
                    // 设置请求头参数
                    httpURLConnection.setRequestProperty("connection", "Keep-Alive")
                    httpURLConnection.setRequestProperty("Charsert", "UTF-8")
                    httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    //self header
                    if (null != headerMap && headerMap.isNotEmpty()) {
                        for (key in headerMap.keys) {
                            if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(headerMap[key])) httpURLConnection.setRequestProperty(
                                key,
                                headerMap[key]
                            )
                        } // end of for
                    } // end of if
                    val out: OutputStream = DataOutputStream(httpURLConnection.outputStream)
                    val keyValue = "Content-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                    val parameterLine = (boundaryPrefix + boundary + newLine).toByteArray()
                    //构建请求参数
                    if (null != paramMap && paramMap.isNotEmpty()) {
                        for ((key, value) in paramMap) {
                            val keyValueBytes =
                                String.format(keyValue, key, value).toByteArray()
                            out.write(parameterLine)
                            out.write(keyValueBytes)
                        } //end of for
                    } //end of if
                    val sb = StringBuilder()
                    sb.append(boundaryPrefix)
                    sb.append(boundary)
                    sb.append(newLine)
                    // 文件参数
                    sb.append("Content-Disposition: form-data;name=\"file\";filename=\"$fileName\"$newLine")
                    sb.append("Content-Type:application/octet-stream")
                    // 参数头设置完以后需要两个换行，然后才是参数内容
                    sb.append(newLine)
                    sb.append(newLine)
                    // 将参数头的数据写入到输出流中
                    out.write(sb.toString().toByteArray())
                    out.write(fileByteAry)
                    // 最后添加换行
                    out.write(newLine.toByteArray())
                    // 定义最后数据分隔线，即--加上boundary再加上--。
                    val end_data =
                        (newLine + boundaryPrefix + boundary + boundaryPrefix + newLine).toByteArray()
                    // 写上结尾标识
                    out.write(end_data)
                    out.flush()
                    out.close()
                    var `is`: InputStream? = null
                    resCode = httpURLConnection.responseCode
                    `is` = if (resCode == HttpURLConnection.HTTP_OK) {
                        httpURLConnection.inputStream
                    } else {
                        httpURLConnection.errorStream
                    }
                    val bf = BufferedReader(InputStreamReader(`is`, StandardCharsets.UTF_8))
                    val buffer = StringBuffer() //最好在将字节流转换为字符流的时候 进行转码
                    var line: String? = ""
                    while (bf.readLine().also { line = it } != null) {
                        buffer.append(line)
                    }
                    bf.close()
                    `is`.close()
                    val infoStr = buffer.toString()
                    if (resCode == HttpURLConnection.HTTP_OK) {
                        println("======Stephen=LogFileUtils==doUpload===>成功数据(请求Url:$urlString):$infoStr")
                        listener?.let { it(true, resCode, infoStr) }
                    } else {
                        println("======Stephen=LogFileUtils==doUpload===>失败Code(请求Url:$urlString):$resCode===>失败Msg:$infoStr")
                        listener?.let { it(false, resCode, infoStr) }
                    }
                } catch (e: java.lang.Exception) {
                    println("======Stephen=LogFileUtils==doUpload===>异常Code(请求Url:$urlString):$resCode===>异常Msg:${e.message}")
                    listener?.let { it(false, resCode, e.message ?: "Unknown") }
                } finally {
                    httpURLConnection?.disconnect()
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    // 通过系统自带分享发送文件
    fun shareSelfLogBySystem(): Boolean {
        val file = getSelfLogFile() ?: return false
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        return try {
            val fileUri: Uri = Uri.fromFile(file)
            application!!.grantUriPermission("com.tencent.mm", fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)// 授权给微信访问路径
            intent.putExtra(Intent.EXTRA_STREAM, fileUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            application!!.startActivity(Intent.createChooser(intent, "分享日志文件"))
            true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            false
        }
    }

    /*********************以下为附加***************************/
    private class DragFloatTextView : AppCompatTextView {
        private var screenWidth = 0//屏幕内横向方向移动范围
        private var screenHeight = 0//屏幕内垂直方向移动范围
        private var statusHeight = 0//状态栏高度

        constructor(context: Context) : super(context) {
            getScreenWidthHeight(context).let {
                screenWidth = it.x
                screenHeight = it.y
                statusHeight = getStatusHeight(context)
            }
            val gradientDrawable = GradientDrawable()
            gradientDrawable.shape = GradientDrawable.RECTANGLE
            gradientDrawable.cornerRadius = 100f
            gradientDrawable.gradientType = GradientDrawable.RADIAL_GRADIENT //辐射渐变
            gradientDrawable.gradientRadius = 80f //设置渐变半径
            gradientDrawable.setGradientCenter(0.5f, 0.5f)
            gradientDrawable.colors = intArrayOf(Color.parseColor("#F7DBBF"), Color.parseColor("#DCC1A2"), Color.parseColor("#F2DD95"))
            //gradientDrawable.setColor(Color.parseColor("#F3DE93"))
            gradientDrawable.setStroke(5, Color.parseColor("#E2D8CD"), 0f, 0f)
            val layerDrawable = LayerDrawable(arrayOf(gradientDrawable))
            background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {//水波纹点击效果,5.0以上才有效,当控件设置了点击监听器,并且控件点击有效时,才能产生水波纹
                RippleDrawable(ColorStateList.valueOf(Color.LTGRAY), layerDrawable, null)
            }else{
                layerDrawable
            }
        }

        private var lastX = 0F
        private var lastY = 0F
        private var tranL = 0f
        private var tranT = 0f

        @Override
        override fun onTouchEvent(event: MotionEvent): Boolean {
            super.onTouchEvent(event);
            if (!this.isEnabled) return false
            val x = event.rawX
            val y = event.rawY
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = x
                    lastY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    val xDistance = x - lastX
                    val yDistance = y - lastY
                    //println("======Stephen============x:$x=====y:$y====>xDistance:$xDistance===>yDistance:$yDistance")
                    if(0f == xDistance && 0f == yDistance)return false
                    tranL = this.translationX + xDistance
                    tranT = this.translationY + yDistance
                    val borderL = left + tranL
                    val borderT = top + tranT
                    if(borderL < 0 || (borderL + width) > screenWidth || borderT < statusHeight || (borderT + height) > screenHeight)return false
                    this.translationX = tranL
                    this.translationY = tranT
                    lastX = x
                    lastY = y
                    isPressed = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isPressed = false
            }//end of when
            return true
        }

        //获得屏幕宽/高度
        private fun getScreenWidthHeight(context: Context): Point {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val outMetrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(outMetrics)
            return Point(outMetrics.widthPixels, outMetrics.heightPixels)
        }

        //获得状态栏的高度
        private fun getStatusHeight(context: Context): Int {
            var statusHeight = -1
            try {
                val clazz = Class.forName("com.android.internal.R\$dimen")
                val `object` = clazz.newInstance()
                val height = clazz.getField("status_bar_height")[`object`].toString().toInt()
                statusHeight = context.resources.getDimensionPixelSize(height)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return statusHeight
        }
    }

    interface OnChangeLogUploadUiCallback{
        fun onChangeLogUploadUi(isShow: Boolean)
    }

    interface OnLogUploadEventCallback{
        fun onLogUploadStart(hintMsg: String? = "")
        fun onLogUploadEnd(isSuccess: Boolean, errFlag: Int? = 0, errMsg: String? = "OK")
    }
}