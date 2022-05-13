package com.qy.tool.utilslib

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
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


//日志文件
class LogFileUtils private constructor() {
    private var application: Application? = null
    private var isInitializer: Boolean = false
    private val cacheNoInitLogList = arrayListOf<String>()
    private var isCanRecordLog: Boolean = false
    private val threadPool: ExecutorService = ThreadPoolExecutor(10, 10, 60L, TimeUnit.SECONDS, ArrayBlockingQueue(10))
    private var selfLogFile: File? = null
    private var userCustomInfo: String? = "nothing"//上传日志名中的自定义信息
    private var commitNeedToast: Boolean? = true//上传日志是否需要toast
    private var commitServerHost: String? = "https://openapi.qiyou.cn"//上传日志服务域名
    private var commitServerAddr: String? = "/api/common_bll/v1/external/reportLog/log/action/upload"//上传日志服务地址
    private var loadingDialog: AlertDialog? = null
    private var isCommitLogging = false//是否提交日志中
    private var uploadLogBtn: DragFloatTextView? = null
    private var curActivity: Activity? = null
    private val cacheShowActivityList = arrayListOf<Activity>()

    companion object {
        const val ConfigKeyName = "CAN_UPLOAD_LOG_CONFIG"
        private var mInstance: LogFileUtils? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): LogFileUtils {
            if(mInstance == null) mInstance = LogFileUtils()
            return mInstance!!
        }
    }

    //初始化
    fun init(application: Application, userCustomInfo: String? = null, commitNeedToast: Boolean? = null, commitServerHost: String? = null, commitServerAddr: String? = null){
        this.application = application
        updateSetUserCustomInfo(userCustomInfo)
        commitNeedToast?.let { this.commitNeedToast = it }
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
    }

    //更新检查标志,主要判断是否允许记录日志到文件(uid或deviceId其一匹配上后台配置的配置参数(默认逗号分隔)就显示入口),调用处主要为获取配置参数后及登录后设置uid
    fun updateCheckCanRecordLog(configUidOrDeviceIdStr: String?, curUid: String?, curDeviceId: String?, separator: String? = ","){
        if(null == application){
            println("======Stephen=LogFileUtils====Err====>application is Empty:重要,你没有调用init初始化日志工具!!")
            return
        }//end of if
        checkCanRecordLogCore(configUidOrDeviceIdStr, curUid, curDeviceId, separator)
        isInitializer = true
        checkInitLogUploadUi()
        changeLogUploadUi(isCanRecordLog)
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

    //log上传入口开关初始化
    private fun checkInitLogUploadUi(){
        if(null == application || !isCanRecordLog || null != uploadLogBtn)return
        uploadLogBtn = DragFloatTextView(application!!.applicationContext)
        uploadLogBtn?.run {
            text = "上传日志"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.parseColor("#614B18"))
            isSingleLine = false
            setPadding(dip2px(context, 10f), dip2px(context, 0f), dip2px(context, 10f), dip2px(context, 0f))
            setOnClickListener {
                if(null == curActivity || curActivity!!.isFinishing || curActivity!!.isDestroyed){
                    commitSelfLog(userCustomInfo, commitNeedToast, commitServerHost, commitServerAddr)
                    return@setOnClickListener
                }//end of if
                try {
                    val builder = AlertDialog.Builder(curActivity)
                    builder.setTitle("请确认")
                    builder.setMessage("你确认提交本地日志给开发者吗?")
                    builder.setPositiveButton("提交") { _, _ ->
                        commitSelfLog(userCustomInfo, commitNeedToast, commitServerHost, commitServerAddr)
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
        if(isShow)showFromAppTopView(curActivity, uploadLogBtn)
    }

    private fun showFromAppTopView(activity: Activity?, view: View?) {
        try {
            if (null == view) return
            activity?.let {
                if (!it.isDestroyed) { //Activity不为空并且没有被释放掉
                    val root = it.window.decorView as ViewGroup //获取Activity顶层视图
                    if (null != root) {
                        val params = FrameLayout.LayoutParams(dip2px(it, 64f), dip2px(it, 64f))
                        params.bottomMargin = dip2px(it, 150f)
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

    private fun getSelfLogFolder(context: Context): String = "${context.externalCacheDir?.path}/Log"

    private fun getSelfLogName(context: Context): String = "${context.packageName}_log.txt"

    //追加日志记录
    fun appendSelfLog(logMsg: String?){
        if(null == application?.externalCacheDir || logMsg.isNullOrBlank())return
        if(!isInitializer){//未初始化时先缓存起来,避免缺失前面的日志
            cacheNoInitLogList.add("Date:${generateCurrentDate()}\nLogStr:$logMsg\n")
            return
        }//end of if
        if(!isCanRecordLog)return
        if(null == selfLogFile || !selfLogFile!!.exists()){
            val dirs = File(getSelfLogFolder(application!!))
            if (!dirs.exists()) dirs.mkdirs()
            selfLogFile = File(dirs.absolutePath, getSelfLogName(application!!))
            try {
                selfLogFile!!.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }//end of if
        if (null != selfLogFile && selfLogFile!!.exists()) {
            var fileOutputStream: FileOutputStream? = null
            try {
                fileOutputStream = FileOutputStream(selfLogFile, true)
                if(cacheNoInitLogList.isNotEmpty()){
                    cacheNoInitLogList.forEach {
                        fileOutputStream.write(it.toByteArray())
                    }
                    cacheNoInitLogList.clear()
                }//end of if
                fileOutputStream.write("Date:${generateCurrentDate()}\nLogStr:$logMsg\n".toByteArray())
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }//end of if
            }
        } else {
            println("======Stephen=LogFileUtils=====appendSelfLog======>日志文件为空,追加日志失败!")
        }
    }

    //上报日志记录
    fun commitSelfLog(userCustomInfo: String? = "nothing", isNeedToast: Boolean? = true, commitServerHost: String? = "https://openapi.qiyou.cn",
                      commitServerAddr: String? = "/api/common_bll/v1/external/reportLog/log/action/upload", eventCallback: ((status: Int, msg: String?) -> Unit)? = null) {
        if(!isCanRecordLog)return
        try {
            if(null == application){
                val msg = "application is empty"
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                eventCallback?.let { it(-1, msg) }
                return
            }//end of if
            if(null == application!!.externalCacheDir || commitServerHost.isNullOrBlank()){
                val msg = "key data is empty, please init setting"
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                eventCallback?.let { it(-2, msg) }
                return
            }//end of if
            if(isCommitLogging){
                val msg = "正在提交中，请耐心等待完成提示..."
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                eventCallback?.let { it(0, msg) }
                return
            }//end of if
            val file = File(getSelfLogFolder(application!!), getSelfLogName(application!!))
            if(file.exists()){
                showLoadingDialog()
                isCommitLogging = true
                val msg = "上报日志记录开始,如果日志过大,将比较慢,期间请勿操作,请耐心等待完成提示..."
                if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                eventCallback?.let { it(1, msg) }
                val newLogFile = File(getSelfLogFolder(application!!), String.format("%s_%s_%s_log.txt", generateCurrentDate(), application!!.packageName, userCustomInfo))
                file.renameTo(newLogFile)
                doUpload("${commitServerHost}$commitServerAddr", newLogFile!!.name, newLogFile!!.readBytes()){ isSuccess, resCode, infoStr ->
                    var msg = "上报日志记录成功"
                    if (isSuccess) {
                        try { newLogFile?.delete() } catch (e: Exception) { e.printStackTrace() }
                        isCommitLogging = false
                        eventCallback?.let { it(2, msg) }
                    } else {
                        msg = "上报日志记录失败!(${resCode})${infoStr}"
                        try {
                            if (newLogFile!!.length() > 8 * 1024 * 1024) {//服务器上传大小上限8mb
                                try { newLogFile?.delete() } catch (e: Exception) { e.printStackTrace() }
                            } else {
                                newLogFile.renameTo(File(getSelfLogFolder(application!!), getSelfLogName(application!!)))//恢复回原名字
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                        isCommitLogging = false
                        eventCallback?.let { it(3, msg) }
                    }
                    println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                    if(isNeedToast!!) CoroutineScope(Dispatchers.Main).launch { Toast.makeText(application, msg, Toast.LENGTH_LONG).show() }
                    dismissLoadingDialog()
                }
            }else{
                val msg = "log file is not exists"
                println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
                if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                eventCallback?.let { it(-3, msg) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = "exception:${e.message}"
            println("======Stephen=LogFileUtils=====commitSelfLog====>$msg")
            if(isNeedToast!!)Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
            eventCallback?.let { it(-4, msg) }
        }
    }

    //删除日志文件
    fun deleteSelfLog(context: Context?){
        if(!isCanRecordLog || null == context?.externalCacheDir)return
        try {
            File(getSelfLogFolder(context), getSelfLogName(context))?.delete()
        } catch (e: Exception) { e.printStackTrace() }
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
            setPadding(dip2px(context, 10f), dip2px(context, 5f), dip2px(context, 10f), dip2px(context, 5f))
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

    private fun dip2px(context: Context?, dpValue: Float): Int {
        if(null == context)return dpValue.toInt()
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    private fun generateCurrentDate(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

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

        private var downX = 0F
        private var downY = 0F

        @Override
        override fun onTouchEvent(event: MotionEvent): Boolean {
            super.onTouchEvent(event);
            if (!this.isEnabled) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val xDistance: Float = event.x - downX
                    val yDistance: Float = event.y - downY
                    if (xDistance != 0F && yDistance != 0F) {
                        val l =(left + xDistance).toInt()
                        val r =(right + xDistance).toInt()
                        val t =(top + yDistance).toInt()
                        val b =(bottom + yDistance).toInt()
                        if(l < 0 || r > screenWidth || t < statusHeight || b > screenHeight)return false
                        isPressed = false
                        this.layout(l, t, r, b)
                    }//end of if
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
}