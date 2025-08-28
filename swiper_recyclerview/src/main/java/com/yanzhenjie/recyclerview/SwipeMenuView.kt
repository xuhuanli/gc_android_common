package com.yanzhenjie.recyclerview

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by Yan Zhenjie on 2016/7/26.
 */
class SwipeMenuView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener {
    private var mViewHolder: RecyclerView.ViewHolder? = null
    private var mItemClickListener: OnItemMenuClickListener? = null

    init {
        gravity = Gravity.CENTER_VERTICAL
    }

    fun createMenu(
        viewHolder: RecyclerView.ViewHolder, swipeMenu: SwipeMenu, controller: Controller?,
        direction: Int, itemClickListener: OnItemMenuClickListener?
    ) {
        removeAllViews()

        this.mViewHolder = viewHolder
        this.mItemClickListener = itemClickListener

        // 随便写了下 有别的需求不满足自己改这个库
        val items = swipeMenu.menuItems
        // 如果任何一个菜单项有 customView，则优先使用它
        val customItem = items.firstOrNull { it.customView != null }
        customItem?.customView?.let {
            addView(it)
            it.setOnClickListener(this)
            val menuBridge = SwipeMenuBridge(controller, direction, 0)
            it.tag = menuBridge
            return
        }

        for (i in items.indices) {
            val item = items[i]

            val params =
                LayoutParams(item.width, item.height)
            params.weight = item.weight.toFloat()
            val parent = LinearLayout(context)
            parent.setId(i)
            parent.gravity = Gravity.CENTER
            parent.orientation = VERTICAL
            parent.setLayoutParams(params)
            ViewCompat.setBackground(parent, item.background)
            parent.setOnClickListener(this)
            addView(parent)

            val menuBridge = SwipeMenuBridge(controller, direction, i)
            parent.tag = menuBridge

            if (item.image != null) {
                val iv = createIcon(item)
                parent.addView(iv)
            }

            if (!TextUtils.isEmpty(item.text)) {
                val tv = createTitle(item)
                parent.addView(tv)
            }
        }
    }

    override fun onClick(v: View) {
        if (mItemClickListener != null) {
            mItemClickListener!!.onItemClick(
                v.tag as SwipeMenuBridge?,
                mViewHolder!!.getAdapterPosition()
            )
        }
    }

    private fun createIcon(item: SwipeMenuItem): ImageView {
        val imageView = ImageView(context)
        imageView.setImageDrawable(item.image)
        return imageView
    }

    private fun createTitle(item: SwipeMenuItem): TextView {
        val textView = TextView(context)
        textView.text = item.text
        textView.setGravity(Gravity.CENTER)
        val textSize = item.textSize
        if (textSize > 0) textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize.toFloat())
        val textColor: ColorStateList? = item.titleColor
        if (textColor != null) textView.setTextColor(textColor)
        val textAppearance = item.textAppearance
        if (textAppearance != 0) TextViewCompat.setTextAppearance(textView, textAppearance)
        val typeface: Typeface? = item.textTypeface
        if (typeface != null) textView.setTypeface(typeface)
        return textView
    }
}