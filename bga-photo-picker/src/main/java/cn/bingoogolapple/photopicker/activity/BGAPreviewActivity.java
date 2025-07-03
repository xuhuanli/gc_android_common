package cn.bingoogolapple.photopicker.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import cn.bingoogolapple.baseadapter.BGAOnNoDoubleClickListener;
import cn.bingoogolapple.photopicker.R;
import cn.bingoogolapple.photopicker.adapter.BGAPhotoPageAdapter;
import cn.bingoogolapple.photopicker.imageloader.BGAImage;
import cn.bingoogolapple.photopicker.imageloader.BGAImageLoader;
import cn.bingoogolapple.photopicker.util.BGAAsyncTask;
import cn.bingoogolapple.photopicker.util.BGAPhotoPickerUtil;
import cn.bingoogolapple.photopicker.util.BGASavePhotoTask;
import cn.bingoogolapple.photopicker.widget.BGAHackyViewPager;
import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * desc:带有底部说明，更新时间描述等功能预览图片布局
 * <p>
 * author：ccw
 * date:2025/3/12
 * time:15:09
 */
public class BGAPreviewActivity extends BGAPPToolbarActivity implements PhotoViewAttacher.OnViewTapListener, BGAAsyncTask.Callback<Void> {
    private static final String EXTRA_SAVE_PHOTO_DIR = "EXTRA_SAVE_PHOTO_DIR";
    private static final String EXTRA_PREVIEW_PHOTOS = "EXTRA_PREVIEW_PHOTOS";
    private static final String EXTRA_CURRENT_POSITION = "EXTRA_CURRENT_POSITION";

    private TextView mTitleTv;
    private TextView mSub;
    private TextView desc_text;
    private ImageView mDownloadIv;
    private BGAHackyViewPager mContentHvp;
    private BGAPhotoPageAdapter mPhotoPageAdapter;

    private boolean mIsSinglePreview;

    private File mSavePhotoDir;

    private BGASavePhotoTask mSavePhotoTask;

    ArrayList<String> maskContent;

    ArrayList<String> bottomContentList;


    public static class IntentBuilder {
        private Intent mIntent;

        public IntentBuilder(Context context) {
            mIntent = new Intent(context, BGAPreviewActivity.class);
        }

        /**
         * 保存图片的目录，如果传 null，则没有保存图片功能
         */
        public IntentBuilder saveImgDir(@Nullable File saveImgDir) {
            mIntent.putExtra(EXTRA_SAVE_PHOTO_DIR, saveImgDir);
            return this;
        }

        /**
         * 当前预览的图片路径
         */
        public IntentBuilder previewPhoto(String photoPath) {
            mIntent.putStringArrayListExtra(EXTRA_PREVIEW_PHOTOS, new ArrayList<>(Arrays.asList(photoPath)));
            return this;
        }


        /**
         * 当前预览的图片路径集合
         */
        public IntentBuilder previewPhotos(ArrayList<String> previewPhotos) {
            mIntent.putStringArrayListExtra(EXTRA_PREVIEW_PHOTOS, previewPhotos);
            return this;
        }

        /**
         * 增加底部说明文案
         */
        public IntentBuilder previewBottomContent(ArrayList<String> maskPhotos) {
            mIntent.putStringArrayListExtra("bottomContent", maskPhotos);
            return this;
        }

        /**
         * 当前内容
         */
        public IntentBuilder previewMaskPhotos(ArrayList<String> maskPhotos) {
            mIntent.putStringArrayListExtra("maskPhotos", maskPhotos);
            return this;
        }

        /**
         * 当前预览的图片索引
         */
        public IntentBuilder currentPosition(int currentPosition) {
            mIntent.putExtra(EXTRA_CURRENT_POSITION, currentPosition);
            return this;
        }

        public Intent build() {
            return mIntent;
        }
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setContentView(R.layout.bga_pp_layout_pre_main);
        mContentHvp = findViewById(R.id.hvp_photo_preview_content);
        mTitleTv = findViewById(R.id.tv_photo_preview_title);
        mSub = findViewById(R.id.renson_text);
        ImageView backImage = findViewById(R.id.back_image);
        desc_text = findViewById(R.id.desc_text);
        mToolbar.setVisibility(View.GONE);
        backImage.setOnClickListener(v -> finish());
        mDownloadIv =findViewById(R.id.iv_photo_preview_download);
        mDownloadIv.setOnClickListener(new BGAOnNoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                if (mSavePhotoTask == null) {
                    savePic();
                }
            }
        });

    }

    @Override
    protected void setListener() {
        mContentHvp.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                renderTitleTv();

            }
        });
    }

    @Override
    protected void processLogic(Bundle savedInstanceState) {
        mSavePhotoDir = (File) getIntent().getSerializableExtra(EXTRA_SAVE_PHOTO_DIR);
        if (mSavePhotoDir != null && !mSavePhotoDir.exists()) {
            mSavePhotoDir.mkdirs();
        }

        ArrayList<String> previewPhotos = getIntent().getStringArrayListExtra(EXTRA_PREVIEW_PHOTOS);
        maskContent = getIntent().getStringArrayListExtra("maskPhotos");
        bottomContentList= getIntent().getStringArrayListExtra("bottomContent");
        int currentPosition = getIntent().getIntExtra(EXTRA_CURRENT_POSITION, 0);
        mIsSinglePreview = previewPhotos.size() == 1;
        if (mIsSinglePreview) {
            currentPosition = 0;
        }

        mPhotoPageAdapter = new BGAPhotoPageAdapter(this, previewPhotos);
        mContentHvp.setAdapter(mPhotoPageAdapter);
        mContentHvp.setCurrentItem(currentPosition);
        renderTitleTv();
        if (mSavePhotoDir == null) {
            mDownloadIv.setVisibility(View.INVISIBLE);
        }
        // 过2秒隐藏标题栏
//        mToolbar.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                hiddenTitleBar();
//            }
//        }, 2000);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.item_ai_preview, menu);
//        MenuItem menuItem = menu.findItem(R.id.item_photo_preview_title);
//        View actionView = menuItem.getActionView();
//
//        mTitleTv = actionView.findViewById(R.id.tv_photo_preview_title);
//        mDownloadIv = actionView.findViewById(R.id.iv_photo_preview_download);
//        mDownloadIv.setOnClickListener(new BGAOnNoDoubleClickListener() {
//            @Override
//            public void onNoDoubleClick(View v) {
//                if (mSavePhotoTask == null) {
//                    savePic();
//                }
//            }
//        });

//        if (mSavePhotoDir == null) {
//            mDownloadIv.setVisibility(View.INVISIBLE);
//        }
//
//        renderTitleTv();

        return true;
    }

    private void renderTitleTv() {
        if (mTitleTv == null || mPhotoPageAdapter == null) {
            return;
        }

        if (mIsSinglePreview) {
            mTitleTv.setText(R.string.bga_pp_view_photo);
        } else {
            mTitleTv.setText((mContentHvp.getCurrentItem() + 1) + "/" + mPhotoPageAdapter.getCount());
        }
        if (maskContent != null) {
            String str = maskContent.get(mContentHvp.getCurrentItem());
            if (str != null) {
                mSub.setVisibility(View.VISIBLE);
                mSub.setText(str);
            } else {
                mSub.setText("");
                mSub.setVisibility(View.GONE);
            }
        }
        if(bottomContentList!=null){
            String str = bottomContentList.get(mContentHvp.getCurrentItem());
            if (str != null) {
                desc_text.setVisibility(View.VISIBLE);
                desc_text.setText(str);
            } else {
                desc_text.setText("");
                desc_text.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onViewTap(View view, float x, float y) {
//        if (System.currentTimeMillis() - mLastShowHiddenTime > 500) {
//            mLastShowHiddenTime = System.currentTimeMillis();
//            if (mIsHidden) {
//                showTitleBar();
//            } else {
//                hiddenTitleBar();
//            }
//        }
    }

//    private void showTitleBar() {
//        if (mToolbar != null) {
//            ViewCompat.animate(mToolbar).translationY(0).setInterpolator(new DecelerateInterpolator(2)).setListener(new ViewPropertyAnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(View view) {
//                    mIsHidden = false;
//                }
//            }).start();
//        }
//    }
//
//    private void hiddenTitleBar() {
//        if (mToolbar != null) {
//            ViewCompat.animate(mToolbar).translationY(-mToolbar.getHeight()).setInterpolator(new DecelerateInterpolator(2)).setListener(new ViewPropertyAnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(View view) {
//                    mIsHidden = true;
//                }
//            }).start();
//        }
//    }

    private synchronized void savePic() {
        if (mSavePhotoTask != null) {
            return;
        }

        final String url = mPhotoPageAdapter.getItem(mContentHvp.getCurrentItem());
        File file;
        if (url.startsWith("file")) {
            file = new File(url.replace("file://", ""));
            if (file.exists()) {
                BGAPhotoPickerUtil.showSafe(getString(R.string.bga_pp_save_img_success_folder, file.getParentFile().getAbsolutePath()));
                return;
            }
        }

        // 通过MD5加密url生成文件名，避免多次保存同一张图片
        file = new File(mSavePhotoDir, BGAPhotoPickerUtil.md5(url) + ".png");
        if (file.exists()) {
            BGAPhotoPickerUtil.showSafe(getString(R.string.bga_pp_save_img_success_folder, mSavePhotoDir.getAbsolutePath()));
            return;
        }

        mSavePhotoTask = new BGASavePhotoTask(this, this, file);
        BGAImage.download(url, new BGAImageLoader.DownloadDelegate() {
            @Override
            public void onSuccess(String url, Bitmap bitmap) {
                if (mSavePhotoTask != null) {
                    mSavePhotoTask.setBitmapAndPerform(bitmap);
                }
            }

            @Override
            public void onFailed(String url) {
                mSavePhotoTask = null;
                BGAPhotoPickerUtil.show(R.string.bga_pp_save_img_failure);
            }
        });
    }

    @Override
    public void onPostExecute(Void aVoid) {
        mSavePhotoTask = null;
    }

    @Override
    public void onTaskCancelled() {
        mSavePhotoTask = null;
    }

    @Override
    protected void onDestroy() {
        if (mSavePhotoTask != null) {
            mSavePhotoTask.cancelTask();
            mSavePhotoTask = null;
        }
        super.onDestroy();
    }
}
