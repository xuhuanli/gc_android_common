## 模块列表
### rvadapter
原BGABaseAdapter
- AGP插件适配
### pickerview
原PickerView
- AGP插件适配
### wheelview
原wheelview
- AGP插件适配
### swiper_recyclerview
原SwiperRecyclerView
- AGP插件适配
- OnItemMoveListener新增控制拖拽时滑动的速度方法
### recyclerview_divider
原recyclerview-flexibledivider
- agp插件适配
### circleprogressbar
原circleprogressbar
- AGP插件适配

## Maven相关
版本号 见[maven_publish.gradle](maven_publish.gradle)
maven地址 见[maven_publish.gradle](maven_publish.gradle)

## ChangeLog
- 移除gclogan
- rvadapter更名为[bga-base-adapter](bga-base-adapter)
- 新增[bga-photo-picker](bga-photo-picker)模块
- gradle文件更新
- groovy转kts
- 新增recyclerview_divider替换原recyclerview-flexibledivider在gradle8.0+中可依赖
- 新增circleprogressbar 支持渐变指示器的圆形进度条