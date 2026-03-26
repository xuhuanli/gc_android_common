package com.igancao.hptasr.net

import com.igancao.hptasr.bean.BaseResponse
import com.igancao.hptasr.bean.ConfigInfo
import com.igancao.hptasr.bean.TaskData
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AsrApiService {

    /**
     * 获取基础配置
     */
    @FormUrlEncoded
    @POST("api/v1/task/get_base_config")
    suspend fun getBaseConfig(@Field("third_party") thirdParty: String): BaseResponse<ConfigInfo>

    /**
     * 创建任务
     */
    @FormUrlEncoded
    @POST("api/v1/task/create")
    suspend fun createTask(@Field("third_party") thirdParty: String): BaseResponse<TaskData>

    /**
     * 结束任务
     * @Deprecated("不需要调用结束任务接口，直接socket发送END指令")
     */
    @Deprecated("不需要调用结束任务接口，直接socket发送END指令")
    @FormUrlEncoded
    @POST("api/v1/task/finish")
    suspend fun finishTask(@Field("task_id") taskId: String): String
}
