package com.gancao.gc_android_common.ext

import android.content.Context
import android.view.View
import cn.bingoogolapple.photopicker.widget.BGASortableNinePhotoLayout
import java.io.File

/**
 * Copyright (c) 2025-09, 甘草医生
 * All rights reserved
 * Author: xuhuanli2017@gmail.com
 */
fun BGASortableNinePhotoLayout.clickItem(block: (ArrayList<String>?, Int) -> Unit) {
    setDelegate(object : BGASortableNinePhotoLayout.Delegate {
        override fun onClickNinePhotoItem(
            sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
            view: View?,
            position: Int,
            model: String?,
            models: ArrayList<String>?,
        ) {
            block.invoke(models, position)
        }

        override fun onClickAddNinePhotoItem(
            sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
            view: View?,
            position: Int,
            models: ArrayList<String>?,
        ) {
        }

        override fun onClickDeleteNinePhotoItem(
            sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
            view: View?,
            position: Int,
            model: String?,
            models: ArrayList<String>?,
        ) {
        }

        override fun onNinePhotoItemExchanged(
            sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
            fromPosition: Int,
            toPosition: Int,
            models: ArrayList<String>?,
        ) {
        }
    })
}

fun Context.filePath() = getExternalFilesDir(null)?.absolutePath

fun externalImgPath(context: Context) = File(context.filePath(), "img")