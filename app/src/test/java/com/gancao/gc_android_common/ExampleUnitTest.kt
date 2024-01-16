package com.gancao.gc_android_common

import org.junit.Test

import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    fun formatCurrentDate(): String {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy-M-dd HH:mm:ss")

        return dateFormat.format(currentDate)
    }

    @Test
    fun code() {
        val formattedDate = formatCurrentDate()
        println("Formatted Date: $formattedDate")
    }
}