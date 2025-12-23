## 简介
基于一些第三方库的作者不再进行维护更新，所以把项目中引用的库进行了源码下载以及维护。
目前适配到apg8.12.1，
compileSdk = "36"
targetSdk = "34"
minSdk = "24"
部分lib可能会略有不同。

> 每个Lib的名字基本和原库的名字一致。
> 
> 暂时没有把项目工件放到maven中，可以自行下载然后import library

## 模块列表

### rvadapter（bga-base-adapter）

原 BGABaseAdapter 的源码维护版本。

**功能理解：**
- 提供对 `RecyclerView.Adapter` 的进一步封装
- 简化 ViewHolder 创建、数据绑定等模板代码
- 适用于列表、网格等常见 RecyclerView 场景
- 已适配 AGP 新版本，支持 Gradle 8+

---

### pickerview

原 PickerView 的源码维护版本。

**功能理解：**
- 常用于时间选择、选项选择等弹窗组件
- 支持多列联动选择
- 封装完整的弹窗交互逻辑，业务侧只需关注数据

---

### wheelview

原 WheelView 的源码维护版本。

**功能理解：**
- 提供滚轮式选择控件（类似 iOS Picker）
- 支持自定义文字样式、行高、可见条数
- 常作为 PickerView 的底层组件使用

---

### swiper_recyclerview

原 SwiperRecyclerView 的源码维护版本。

**功能理解：**
- 为 RecyclerView 提供侧滑能力（如删除、更多操作）
- 支持自定义侧滑样式
- 新增拖拽时滑动速度控制，交互更可控

---

### recyclerview_divider

原 recyclerview-flexibledivider 的替代实现。

**功能理解：**
- 为 RecyclerView 提供灵活的分割线方案
- 支持线性、网格等多种布局
- 适配 Gradle 8.0+，可作为官方 Divider 的增强版本

---

### circleprogressbar

圆形进度条组件。

**功能理解：**
- 支持圆形进度展示
- 支持渐变色指示器
- 适合加载进度、完成度展示等场景

---

### wavesidebar

索引侧边栏组件。

**功能理解：**
- 常用于通讯录、城市列表等快速索引场景
- 支持字母 / 自定义索引
- 与 RecyclerView 联动实现快速定位

---

### MZBanner

轮播图组件（仿魅族风格）。

**功能理解：**
- 支持图片 / View 轮播
- 支持自动轮播、手动滑动
- 常用于首页 Banner、广告位展示

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
- 新增wavesidebar 索引侧边栏
- 新增MZBanner 仿魅族轮博
- swiper_recyclerview 1.0.5 新增侧滑自定义样式
- 新增websocket模块，支持日志自动重连，默认okhttp配置。