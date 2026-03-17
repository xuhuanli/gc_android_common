package com.igancao.hpt_jsbridge

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Copyright (c) 2026-01, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 *
 * 交互参数处理，要求所有的参数都为json
 */
object JsParamsUtil {
    private val gson by lazy {
        GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping() // 禁用 HTML 转义
            .create()
    }
    private val mapType: Type by lazy {
        object : TypeToken<Map<String, Any>>() {}.type
    }

    /**
     * 将任意对象转换为 Json 字符串
     * @param obj 要转换的对象
     * @return Json 字符串
     */
    fun toJson(obj: Any): String = gson.toJson(obj)

    /**
     * 将 Json 字符串转换为指定类型的对象
     * @param json Json 字符串
     * @param classOfT 目标对象的 Class
     * @return 转换后的对象实例，如果解析失败可能返回 null 或抛出异常
     */
    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: String, classOfT: Class<T>): T {
        return gson.fromJson(json, classOfT)
    }

    /**
     * 将 Json 字符串转换为包含泛型的复杂类型对象（如 List<User>）
     * @param json Json 字符串
     * @param typeOfT 目标对象的 Type。通常通过 `object : TypeToken<List<User>>() {}.type` 获取
     * @return 转换后的对象实例
     */
    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: String, typeOfT: Type): T {
        return gson.fromJson(json, typeOfT)
    }

    /**
     * 将 Json 字符串转换为 Map<String, Any>
     * @param json Json 字符串
     * @return Map，要求输入必须是 JSON 对象
     */
    @Throws(JsonSyntaxException::class)
    fun fromJsonToMap(json: String): Map<String, Any> {
        val element = JsonParser.parseString(json)
        if (!element.isJsonObject) {
            throw JsonSyntaxException("JSON must be an object")
        }
        return gson.fromJson(element, mapType)
    }
}


fun Map<String, Any>.optString(key: String, defaultValue: String = ""): String {
    return when (val value = this[key]) {
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> defaultValue
    }
}

fun Map<String, Any>.optInt(key: String, defaultValue: Int = 0): Int {
    return when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: defaultValue
        else -> defaultValue
    }
}

fun Map<String, Any>.optBoolean(key: String, defaultValue: Boolean = false): Boolean {
    return when (val value = this[key]) {
        is Boolean -> value
        is String -> value.toBoolean()
        is Number -> value.toInt() != 0
        else -> defaultValue
    }
}

fun Map<String, Any>.optDouble(key: String, defaultValue: Double = 0.0): Double {
    return when (val value = this[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: defaultValue
        else -> defaultValue
    }
}
