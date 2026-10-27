# 小满账本 · Xiaoman Ledger

> 把日子记清楚。

一个**离线优先**的记账工具。整个应用就是一个 HTML 文件，不依赖任何框架、不联网、不需要账号，数据只存在你本机。

[![License: MIT](https://img.shields.io/badge/License-MIT-235c48.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Web%20%7C%20Android-173f32.svg)
![Size](https://img.shields.io/badge/size-single%20file-e0783f.svg)

---

## 目录

- [为什么做这个](#为什么做这个)
- [功能](#功能)
- [快速开始](#快速开始)
- [数据与隐私](#数据与隐私)
- [项目结构](#项目结构)
- [技术实现](#技术实现)
- [打包成 Android APK](#打包成-android-apk)
- [开发约定](#开发约定)
- [参与贡献](#参与贡献)
- [许可协议](#许可协议)

---

## 为什么做这个

市面上的记账 App 大多要求注册账号、上传云端、绑定手机号。记账是件很私人的事，没必要把每一笔消费交给服务器。

小满账本的做法是反过来的：

- **一个 HTML 文件就是整个 App**，双击就能用，拷到 U 盘也能用；
- **零依赖**，没有 npm、没有打包器、没有 node_modules；
- **零联网**，代码里没有任何 `fetch` / `XMLHttpRequest`，断网、飞行模式下功能完全一致；
- **数据是你的**，存在浏览器 `localStorage`，随时可以导出成 JSON / CSV。

Android 版只是给这个网页套了一层 WebView 外壳，额外加了一个「通知监听自动记账」的原生能力。

## 功能

**记账**

- 支出 / 收入双向记账，金额、分类、日期、备注
- 12 个内置分类（餐饮、交通、购物、居住、娱乐、医疗、学习、人情、宠物、通讯、运动、其他），支持自定义名称、图标、颜色
- 按天分组的流水列表，支持按分类筛选、关键词搜索、按月份切换
- 记错了可以直接改、可以删

**统计**

- 月度总览：本月支出、日均支出、较上月涨跌
- 分类占比排行
- 按日柱状趋势图
- 「有支出的日子」计数与月度洞察文案

**数据**

- JSON 全量备份 / 恢复（带 `schemaVersion` 校验，可跨版本迁移）
- CSV 导出（Excel 友好，做了公式注入防护：`= + - @` 开头的内容会被转义）
- 存储空间占用显示

**自动记账（Android 独有，v1.1.0 起）**

- 监听支付宝、微信的支付通知，自动提取金额、商户、时间
- 三重金额正则：`¥12.34` / `12.34元` / 纯数字 + 关键词
- 支出词 / 收入词判断方向，忽略词过滤营销通知
- 60 秒内相同通知去重（指纹存 SharedPreferences）
- 先进「待确认」队列，你点头才算入账——**不会替你花钱记账**
- 支持按商户关键词自定义分类规则

## 快速开始

### 网页版（推荐先试这个）

```bash
git clone https://github.com/<你的用户名>/xiaoman-ledger.git
cd xiaoman-ledger
```

然后直接双击 `index.html`，或者在项目目录起一个静态服务器：

```bash
# Python（系统自带）
python -m http.server 8000

# 或 Node
npx serve .
```

打开 <http://localhost:8000> 即可。

> 直接 `file://` 打开也能用，功能不缺。但浏览器对 `file://` 下的 `localStorage` 有隔离策略，建议用 `http://localhost` 访问，换浏览器/换路径时数据不会「丢」。

### Android 版

见 [打包成 Android APK](#打包成-android-apk)。

## 数据与隐私

这一节请务必读完，它决定了你敢不敢用。

| 项目 | 情况 |
| --- | --- |
| 联网 | **完全不联网**。源码中无任何网络请求，Android 端的 `INTERNET` 权限仅用于访问本机 `127.0.0.1` 的静态资源服务器 |
| 账号 | 不注册、不登录、无用户体系 |
| 埋点 | 无 |
| 数据位置 | 网页版：浏览器 `localStorage`（键名 `xiaoman-ledger-v1`）<br>Android 版：WebView 的 `localStorage` + 应用私有目录 |
| 数据上传 | 没有任何上传代码 |
| 删除数据 | 在「设置」里清空账本，或清除浏览器站点数据；卸载 App 即彻底删除 |

**关于 Android 的「通知使用权」**：这是 Android 里权限最大的授权之一，技术上开启后可以读取**所有** App 的通知内容。本项目的 `AutoRecordService` 在拿到通知后，会**先按包名过滤**，只处理以下三个来源，其余通知直接丢弃、不落盘：

```kotlin
private const val PKG_ALIPAY = "com.eg.android.AlipayGphone"  // 支付宝
private const val PKG_WECHAT = "com.tencent.mm"               // 微信
private const val PKG_SHELL  = "com.android.shell"            // 仅用于 adb 调试注入测试通知
```

过滤后的通知只写入应用私有目录下的 `auto_queue.jsonl`，由前端通过 JS Bridge 取走并存入本地账本，**不会离开设备**。

如果你不放心，完全可以不开这个权限——自动记账是可选功能，关掉它其余功能一切正常。

## 项目结构

```
xiaoman-ledger/
├── index.html                 # 整个应用：HTML + CSS + JS，单文件，约 8,500 行
├── README.md
├── LICENSE
├── .gitignore
├── docs/
│   ├── APK分析报告.md          # 对 v1.2.0 安装包的逆向分析
│   └── 发布到GitHub.md         # 从零发布到 GitHub 的完整步骤
└── android/                   # Android WebView 外壳（构建 APK 用）
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle.properties
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── assets/www/index.html   # 构建时由脚本从上层的 index.html 同步
            ├── res/...
            └── java/com/xiaoman/ledger/
                ├── MainActivity.kt
                ├── AssetServer.kt
                ├── AutoRecordService.kt
                ├── AutoRecordParser.kt
                └── AutoRecordStore.kt
```

## 技术实现

**前端**：原生 JavaScript（ES2020+），无框架。渲染是「状态 → 模板字符串 → `innerHTML`」，所有插入到 HTML 的文本统一走 `escapeHTML()`。路由用 `location.hash`（`#/overview`、`#/records`、`#/stats`、`#/categories`、`#/auto`），五个页面。

**样式**：CSS 自定义属性做主题，一套变量控制纸色、墨色、绿色主色与橙色强调色。字体用系统栈（Avenir Next / 苹方 / 微软雅黑），数字用衬线体，呼应「账本」的手写感。响应式靠 `clamp()` 和容器查询宽度断点，手机、平板、桌面共用一套布局。

**为什么用单文件**：这个项目的核心约束是「十年后打开还能用」。没有构建工具，就没有依赖腐烂；没有依赖，就不需要为了修一个漏洞升级整条工具链。代价是文件大、不好做代码分割——这是有意识的取舍。

**Android 外壳**：

- `MainActivity` 是一层 WebView 容器，`setJavaScriptEnabled(true)`，注册 JS Bridge 名为 `XiaomanAndroid`；
- 不用 `file:///android_asset/...` 加载页面，因为 `file://` 下 `localStorage` 的 origin 是 `null`，行为不可靠。改起一个只监听 `127.0.0.1` 的迷你 HTTP 服务（`AssetServer`，端口 4178）托管 `assets/www`，WebView 加载 `http://127.0.0.1:4178/`，此时 origin 稳定、`localStorage` 可用；
- JS Bridge 暴露四个方法：`saveFile(name, content)`、`hasNotificationAccess()`、`openNotificationAccessSettings()`、`drainAutoRecords()`；
- 系统返回键走 `onBackPressed` 回调给网页（`lambda$onBackPressed`），网页内先关弹窗再关页面。

## 打包成 Android APK

**前置**：JDK 17+、Android SDK Platform 34、Android Studio（或纯命令行 Gradle）。

```bash
cd android
# 把最新的网页同步进 assets
cp ../index.html app/src/main/assets/www/index.html
# Windows PowerShell:
# Copy-Item ..\index.html app\src\main\assets\www\index.html

./gradlew assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # 需要先配签名，见下
```

**关于签名**，重要：

1. 现在仓库里的 `android/` 是**根据 v1.2.0 安装包逆向重建的参考实现**，不是原始工程，也不保证能原样编译通过——API 行为按反编译结果还原，可能有个别细节需要你自己补。
2. 自己打包请用**你自己的** keystore。**绝对不要把 `.jks` / `.keystore` 提交到 Git**（`.gitignore` 里已经挡住）。
3. 自己签的 APK 无法覆盖安装官方 v1.2.0（签名不同），需要先卸载。卸载会清掉 `localStorage`，**升级前务必先导出 JSON 备份**。

生成 keystore：

```bash
keytool -genkeypair -v -keystore my-release.jks -alias xiaoman \
  -keyalg RSA -keysize 2048 -validity 10950
```

然后在 `android/keystore.properties`（已 gitignore）里写：

```properties
storeFile=../my-release.jks
storePassword=你的密码
keyAlias=xiaoman
keyPassword=你的密码
```

## 开发约定

- 单文件应用，**不要**引入 npm 依赖或构建步骤；
- 任何用户输入渲染到 HTML 前必须过 `escapeHTML()`；
- 金额一律以**分**为单位存整数（`amountToCents`），只在显示时格式化成元，避免浮点误差；
- 数据结构变更必须同时升 `SCHEMA_VERSION` 并在 `normalizeStore()` 里写迁移；
- 提交信息用祈使句，一次提交只做一件事。

## 参与贡献

1. Fork 本仓库
2. 新建分支：`git checkout -b feat/你的功能`
3. 提交：`git commit -m "feat: 增加按周统计"`
4. 推送：`git push origin feat/你的功能`
5. 开 Pull Request

Issue 里反馈问题时，请附上系统版本、浏览器版本、复现步骤。涉及数据丢失的请先说清楚你用的是网页版还是 APK 版。

## 许可协议

[MIT](LICENSE) © 2026 小满账本
