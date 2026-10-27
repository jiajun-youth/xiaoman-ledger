<!--
  下载链接用的是 releases/latest/download/ 固定地址：
  发新版本时只把附件命名为 xiaoman-ledger.apk，这里的按钮就一直有效，不用改 README。
-->
<div align="center">

# 小满账本 · Xiaoman Ledger

**把日子记清楚。**

一个离线优先的记账工具。不联网、不注册、不需要账号，数据只存在你自己的手机上。

[![Download APK](https://img.shields.io/badge/下载_APK-Android_8.0+-235c48?style=for-the-badge&logo=android&logoColor=white)](https://github.com/jiajun-youth/xiaoman-ledger/releases/latest/download/xiaoman-ledger.apk)
[![License: MIT](https://img.shields.io/badge/License-MIT-df783f?style=for-the-badge)](LICENSE)

**[⬇️ 下载安装包](https://github.com/jiajun-youth/xiaoman-ledger/releases/latest/download/xiaoman-ledger.apk)** · 145 KB · 秒下 · 无需 Root

</div>

---

## 📱 手机安装

**不需要电脑，不需要注册，也不需要 Root。**

### 第 1 步：下载

点上面的 **下载 APK** 按钮，或者点这里：

**<https://github.com/jiajun-youth/xiaoman-ledger/releases/latest/download/xiaoman-ledger.apk>**

> 💡 **如果页面打不开**：微信、QQ 的内置浏览器常会拦截 GitHub 链接。
> 点右上角「···」→ **在浏览器中打开**，或者复制链接到 Chrome / 系统浏览器。

### 第 2 步：打开下载好的文件

通知栏会提示下载完成，点它；或者去「文件管理 → 下载」里找到 `xiaoman-ledger.apk`。

### 第 3 步：允许一次「安装未知应用」

第一次装非应用商店的 App 时，系统会拦一下：

1. 弹出「为了安全，你的手机不允许安装未知应用」
2. 点 **设置**
3. 打开 **允许来自此来源**
4. 按返回键回到安装界面

> 这一步只需要做一次。之后装更新就不用再设了。

### 第 4 步：点「安装」

看到「应用已安装」就好了。**打开就能记账，不用登录。**

---

<details>
<summary><b>装不上？点这里看常见问题</b></summary>

**「解析软件包时出现问题」**
下载不完整。删除已下载的文件，重新下一次。别用迅雷/下载器加速，容易截断。

**「应用未安装」/「安装包无效」**
- 手机系统低于 Android 8.0（本应用要求 8.0+）
- 或者之前装过**其他签名**的同名版本，先卸载旧的再装

**「此应用可能有害」/ 风险提示**
所有不在应用商店上架的应用都会显示这条，属于系统对「未知来源」的统一提示。
确认来源是本仓库后，点「仍然安装」。

**安装时提示「已存在更高版本」**
你手机上已经有 v1.2.0 或更新的版本了，不需要重复安装。

**装完打开是白屏**
杀掉进程重开一次。仍然白屏的话，反馈时附上手机型号和 Android 版本。

**换手机怎么迁移账本？**
旧手机：应用内 → 设置 → **导出备份**（得到一个 .json 文件）
新手机：安装 → 设置 → **导入备份** → 选那个文件。

</details>

---

## ✨ 功能

**记一笔**

- 支出 / 收入双向记账：金额、分类、日期、备注
- 12 个内置分类，可自己改名、换图标、换颜色
- 按天分组的流水，支持分类筛选、关键词搜索、按月切换
- 记错了随时改、随时删

**看明白**

- 本月支出、日均支出、较上月涨跌
- 分类占比排行、按日柱状趋势
- 「有支出的日子」计数与月度洞察

**自动记账**（可选，需开权限）

- 监听支付宝、微信的支付通知，自动抓取金额和商户
- 三重金额规则 + 收支方向判断 + 营销通知过滤
- 先进「待确认」队列，**你点了才算入账**，不会替你记账
- 支持按商户关键词自定义分类

**数据是自己的**

- 一键导出 JSON 全量备份，换手机不丢
- 导出 CSV，Excel 直接打开
- 不做任何上传

---

## 💻 也可以不装，直接用网页版

网页版和 App 是同一份代码，功能一致，只是没有「自动记账」（那需要读通知，网页做不到）。

**在线直接用**：<https://jiajun-youth.github.io/xiaoman-ledger/>

或者下载到本地：

```bash
git clone https://github.com/jiajun-youth/xiaoman-ledger.git
cd xiaoman-ledger
```

双击 `index.html` 就能用。

> 想加到手机桌面当 App 用：用 Chrome 打开网页 → 菜单 → **添加到主屏幕**。
> 效果接近原生，但没有自动记账。

---

## 🔒 数据与隐私

| 项目 | 情况 |
|---|---|
| 联网 | **完全不联网**。代码中无任何网络请求，Android 的 `INTERNET` 权限仅用于访问手机本机的资源服务 |
| 账号 | 不注册、不登录、无用户体系 |
| 埋点 | 无 |
| 数据位置 | 网页版：浏览器本地存储<br>App：手机内部存储，其它应用无法读取 |
| 数据上传 | 没有任何上传代码 |
| 卸载 | 卸载即彻底删除，没有残留 |

**关于「通知使用权」**：开启后系统会把所有 App 的通知交给本应用，但代码只处理支付宝、微信两个来源，其余通知直接丢弃、不写入任何位置。不放心的话可以不开——自动记账是可选功能，关掉它其余功能一切正常。

---

## 📦 项目结构

```
xiaoman-ledger/
├── index.html                 # 整个应用：HTML + CSS + JS，单文件约 8,500 行
├── README.md
├── LICENSE
├── docs/
│   └── 发布到GitHub.md         # 把这个项目发布出去的完整步骤
└── android/                   # Android 工程（构建 APK 用）
```

## 🛠 技术实现

**前端**：原生 JavaScript，无框架、无构建步骤。状态变化后重渲染模板字符串，所有插入 HTML 的文本统一走 `escapeHTML()`。路由用 `location.hash`，五个页面：总览 / 流水 / 统计 / 分类 / 自动记账。

**样式**：CSS 自定义属性控制主题，一套变量决定纸色、墨色、主色与强调色。字体用系统栈，数字用衬线体，呼应账本的手写感。

**为什么是单文件**：这个项目的核心约束是「几年后打开还能用」。没有构建工具就没有依赖腐烂，没有依赖就不需要为了修一个漏洞升级整条工具链。代价是文件大、不好做代码分割——这是有意识的取舍。

**Android 外壳**：`MainActivity` 是一层 WebView 容器，另外起一个只监听 `127.0.0.1` 的迷你 HTTP 服务来托管页面资源。之所以不用 `file://` 直接加载，是因为 `file://` 下本地存储的 origin 是 `null`，不同厂商的 ROM 行为不一致，部分机型会直接报错。走本地回环地址后行为稳定统一。

## 🔧 从源码构建 APK

需要 JDK 17+、Android SDK Platform 34。

```bash
git clone https://github.com/jiajun-youth/xiaoman-ledger.git
cd xiaoman-ledger/android
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

网页资源会在构建前自动从仓库根目录的 `index.html` 同步进 `assets/www/`，不需要手工复制两份。

签名与发布细节见 [`android/README.md`](android/README.md)。

## 🤝 参与贡献

1. Fork 本仓库
2. 新建分支：`git checkout -b feat/你的功能`
3. 提交：`git commit -m "feat: 增加按周统计"`
4. 推送：`git push origin feat/你的功能`
5. 开 Pull Request

## 📄 许可协议

[MIT](LICENSE) © 2026 小满账本

---

<div align="center">

如果这个小工具帮到了你，点个 ⭐ 就是最好的支持。

</div>
