package com.gancao.gc_android_common

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.commit
import com.bigkoo.pickerview.builder.TimePickerBuilder
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.tv_1).setOnClickListener {
            val startDate: Calendar = Calendar.getInstance()
            val endDate: Calendar = Calendar.getInstance()
            // 开始结束年月
            startDate.set(2015, 0, 1)
            endDate.set(endDate.get(Calendar.YEAR), endDate.get(Calendar.MONTH), 31)
            val timePicker = TimePickerBuilder(this) { date, v ->
            }
                .setTitleText("nianyue")
                .setTitleSize(18)
                .setSubCalSize(16)
                .setType(BooleanArray(6) { it == 0 || it == 1 }) // 只显示年月
                .setDate(endDate)
                .setRangDate(startDate, endDate)
                .isDialog(true)
                .build()
            timePicker.show()
        }
        findViewById<TextView>(R.id.tv_2).setOnClickListener {
            val fm = BlankFragment.newInstance("1", "2")
            supportFragmentManager.commit {
                add(R.id.container, fm)
            }
        }
    }
}