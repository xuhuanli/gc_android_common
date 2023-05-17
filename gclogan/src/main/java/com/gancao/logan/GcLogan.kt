package com.gancao.logan

import com.dianping.logan.Logan
import com.dianping.logan.LoganConfig

object GcLogan {
    /**
     * 初始化
     * @param cachePath mmap缓存路径
     * @param path file文件路径
     */
    fun initLogan(cachePath: String, path: String) {
        Logan.init(
            LoganConfig.Builder()
                .setCachePath(cachePath)
                .setPath(path)
                .setEncryptKey16("0123456789012345".toByteArray())
                .setEncryptIV16("0123456789012345".toByteArray())
                .build()
        )
    }

    /**
     * 写入日志
     * @param log What you want to write
     * @param type
     */
    fun w(log: String, type: Int = 2) {
        Logan.w(log, type)
    }

    /**
     * 立即写入日志到文件
     * @param log What you want to write
     * @param type
     */
    fun wf(log: String, type: Int = 2) {
        Logan.w(log, type)
        Logan.f()
    }

    /**
     * Logan上传到搭建的服务器
     * @param date
     * @param token
     * @param deviceId
     * @param versionCode
     * @param versionName
     * @param callback
     */
    fun loganUpload(
        date: String,
        token: String,
        deviceId: String,
        versionCode: String,
        versionName: String,
        callback: (code: Int, data: ByteArray) -> Unit
    ) {
        Logan.s(
            "https://front-log-server.igancao.com/logan/upload.json",
            date,
            "com.igancao.doctor",
            token,
            deviceId,
            versionCode,
            versionName
        ) { statusCode, data ->
            if (data != null) {
                callback(statusCode, data)
            }
        }
    }

    fun setDebug(debug: Boolean) {
        Logan.setDebug(debug)
    }
}