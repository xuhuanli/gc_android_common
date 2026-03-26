package com.igancao.hptasr.bean

import com.google.gson.annotations.SerializedName


data class TaskData(
    @SerializedName("task_id")
    val taskId: String?
)
