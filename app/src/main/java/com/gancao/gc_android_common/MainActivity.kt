package com.gancao.gc_android_common

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import cn.bingoogolapple.photopicker.activity.BGAPreviewActivity
import cn.bingoogolapple.photopicker.widget.BGASortableNinePhotoLayout
import com.bigkoo.pickerview.builder.TimePickerBuilder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import java.io.File
import java.util.Calendar

class MainActivity : AppCompatActivity(), BGASortableNinePhotoLayout.Delegate {
    private var mPhotoLayout: BGASortableNinePhotoLayout? = null
    protected var mPhoto: File? = null // 头像
    private var singleIv: ImageView? = null

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
        findViewById<BGASortableNinePhotoLayout>(R.id.photoLayout).apply {
            initUpload(this)
        }
    }

    override fun onClickAddNinePhotoItem(
        sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
        view: View?,
        position: Int,
        models: ArrayList<String>?
    ) {
        openGallery()
    }

    override fun onClickDeleteNinePhotoItem(
        sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
        view: View?,
        position: Int,
        model: String?,
        models: ArrayList<String>?
    ) {
    }

    override fun onClickNinePhotoItem(
        sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
        view: View?,
        position: Int,
        model: String?,
        models: ArrayList<String>?
    ) {
        var file=filesDir
        var list= arrayListOf("你好我是一条文字你好我是一条文字你好我是一条文字你好我是一条文字你好我是一条文字你好我是一条文字你好我是一条文字你好我是一条文字","222222")
        startActivity(
            BGAPreviewActivity.IntentBuilder(this)
                .saveImgDir(file)
                .previewMaskPhotos(models)
                .previewBottomContent(list)
                .previewPhotos(models)
                .currentPosition(0)
                .build()
        )
//        sortableNinePhotoLayout?.let {
//            val intent = BGAPhotoPickerPreviewActivity.IntentBuilder(this)
//                .previewPhotos(models)
//                .selectedPhotos(models)
//                .maxChooseCount(it.maxItemCount)
//                .currentPosition(position)
//                .isFromTakePhoto(false)
//                .build()
//            startActivityForResult(intent, 100)
//        }
    }

    override fun onNinePhotoItemExchanged(
        sortableNinePhotoLayout: BGASortableNinePhotoLayout?,
        fromPosition: Int,
        toPosition: Int,
        models: ArrayList<String>?
    ) {
    }

    protected open fun initUpload(
        photoLayout: BGASortableNinePhotoLayout,
    ) {
        mPhotoLayout = photoLayout
        mPhotoLayout?.setDelegate(this)
    }

    protected open fun openGallery(maxCount: Int = 9, callback: ((List<File>) -> Unit)? = null) {
        PictureSelector.create(this)
            .openGallery(SelectMimeType.ofImage())
            .setMaxSelectNum(maxCount)
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>?) {
                    result?.map { File(it.realPath) }?.let {
                        if (callback != null)
                            callback.invoke(it)
                        else
                            onPhotoResult(it)
                    }
                }

                override fun onCancel() {
                }
            })
    }

    fun onPhotoResult(files: List<File>) {
        mPhoto?.let {
            files.getOrNull(0)?.let { file ->
                file.copyTo(it, true)
                singleIv?.let { iv ->
                    Glide.with(this).load(file).apply(
                        RequestOptions().skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                    ).into(iv)
                }
            }
        }
        mPhotoLayout?.let { lay ->
            files.map { it.absolutePath }.let {
                lay.addMoreData(ArrayList(it))

            }
        }
    }
}