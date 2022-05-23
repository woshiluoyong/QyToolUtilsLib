# QyToolUtilsLib
##公共工具库 [![](https://img.shields.io/badge/%E9%80%86%E6%B0%B4%E5%AF%92-Stephen's%20YYDS-brightgreen)](https://www.jianshu.com/u/9426aa3ff4ae) [![](https://img.shields.io/github/v/release/woshiluoyong/QyToolUtilsLib.svg)](https://github.com/woshiluoyong/QyToolUtilsLib)

功能列表:
1).无任何依赖的日志收集类,可动态根据服务器配置开关开启上传悬浮按钮和收集正常打印日志,便于排查线上问题
后续功能有了再添加...



## 添加依赖

 - 项目build.gradle添加如下
```java
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```
 - app build.gradle添加如下
```java
dependencies {
    implementation 'com.github.woshiluoyong:QyToolUtilsLib:xxxxx'
}
```

## 1).日志功能使用方法:

>1.Application初始化<必须>
>```
>LogFileUtils.getInstance().init(this,200,null,true,null, null);
>```
>参数依次是:application实例,内存里面存多少条消息才回写进文件的限制数,用于上传文件名上的用户自定义信息,上传操作中是否需要toast显示,上传的域名,上传的接口
>注:参数传null的就走默认的

>2.在合适的地方调用后端配置参数信息和本地标识匹配显示上传日志悬浮按钮,一般设置位置是获取全局配置参数接口返回处<必须>
>```
>LogFileUtils.getInstance().updateCheckCanRecordLog(Utils.getClientConfig(LogFileUtils.ConfigKeyName), Utils.getLoginUserId(), new DeviceUuidFactory(App.getInstance()).getDeviceUuid(),",");
>```
>参数依次是:后端接口返回的配置参数信息字符串(多个以分隔符分开,一般为设备id或用户id),本地登录用户id,本地设备id,数据分隔符
>注:方法逻辑是用数据分隔符拆分配置参数信息字符串,然后用本地登录用户id和本地设备id匹配,如果匹配到了就显示悬浮按钮,否则跳过
>e.g: updateCheckCanRecordLog("12334dfff4444453,6899077hdgd77", "6899077hdgd77", null, ",")

>3.在合适的地方调更新用户自定义信息,便于上传日志后在oss能更加显著的区分日志,一般设置位置是登录信息接口返回处<可选>
>```
>LogFileUtils.getInstance().updateSetUserCustomInfo("${Utils.getLoginUserId()}-${Utils.getLoginUserMobile()}-${Utils.getLoginUserEmail()}-${Utils.getLoginUserNickName()}-${Utils.getLoginUserName()}")
>```
>参数依次是:后端用户信息组装起来的字符串
>e.g: updateSetUserCustomInfo("12334dfff4444453-182155445555-455115@qq.com-test")

>4.在合适的地方追加日志信息,此为核心日志记录方法,一般是app统一的日志记录方法或拦截器中调用<必须>
>```
>LogFileUtils.getInstance().appendSelfLog(logMsg: String?, eventCallback: ((isSuccess: Boolean, msg: String?) -> Unit)? = null);
>```
>参数依次是:主要要记录的日志信息,本次记录的成功失败回调
>e.g: appendSelfLog("====Stephen===>测试一次日志", null)

## 后续功能有了再添加...