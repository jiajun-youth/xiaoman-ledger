# Android 外壳工程

> ⚠️ **重要说明，先读这一段。**
>
> 这个目录是**根据 v1.2.0 安装包逆向重建的参考实现**，不是原始工程。
>
> - 包名、版本号、权限、组件、`minSdk`/`targetSdk`、图标资源，全部从原 APK 提取，**准确**；
> - Java 源码的类名、方法名、常量名来自 DEX 字符串池，**准确**；
> - 但方法体是按行为还原的，正则表达式的完整原文无法逐字符恢复，**可能与原实现有差异**；
> - **本目录未经编译验证**（重建环境没有 JDK / Android SDK）。首次构建可能需要对几处细节做修正。
>
> 如果你只想要能用的应用，直接用仓库根目录的 `index.html`，或者下载 Release 里的 APK。
> 这个目录只在你**要自己打包**时才需要。

---

## 前置条件

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 或更高 | 编译源码与 Gradle 都要求 |
| Android SDK | Platform 34 + Build-Tools 34 | `targetSdk=34` 必须有对应平台 |
| Gradle | 8.7+ | 或直接用 Android Studio 自带的 |

检查：

```bash
java -version          # 需要 17+
echo $ANDROID_HOME     # Windows: echo $env:ANDROID_HOME
```

## 构建

### 方式一：Android Studio（推荐新手）

1. Android Studio → **Open** → 选中 `android/` 目录
2. 等 Gradle 同步完成（首次会下载依赖，几分钟）
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
4. 产物在 `app/build/outputs/apk/debug/app-debug.apk`

### 方式二：命令行

Gradle Wrapper 没有随仓库提供（生成它需要本机有 Gradle）。首次先补上：

```bash
cd android
gradle wrapper --gradle-version 8.7
```

之后：

```bash
# Windows
.\gradlew.bat assembleDebug
# macOS / Linux
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 网页资源怎么进包

`app/build.gradle.kts` 里有个 `syncWebAssets` 任务，会把**仓库根目录的 `index.html`**
复制到 `app/src/main/assets/www/`，并在 `preBuild` 前执行。

所以：**改网页只改根目录那一个 `index.html`**，这里会自动同步，不要手工维护两份。

## 签名

### 1. 生成 keystore

```bash
keytool -genkeypair -v \
  -keystore my-release.jks \
  -alias xiaoman \
  -keyalg RSA -keysize 2048 -validity 10950
```

有效期 10950 天 ≈ 30 年。Android 要求签名密钥的有效期**长于应用的预期寿命**，别设 1 年。

### 2. 写配置

在 `android/` 下建 `keystore.properties`（已被 `.gitignore` 挡住）：

```properties
storeFile=../my-release.jks
storePassword=你的密码
keyAlias=xiaoman
keyPassword=你的密码
```

参考 `keystore.properties.example`。

### 3. 打 release 包

```bash
./gradlew assembleRelease
```

`app/build.gradle.kts` 里的 `signingConfigs` 会自动读取上面的配置。
配置不存在时 release 包不签名（会构建失败或产出不可安装的文件），这是刻意的：
**宁可失败，也不要意外产出用调试密钥签名的 release 包。**

## ⚠️ 签名一致性

你自签的包**无法覆盖安装**官方 v1.2.0（签名不同，`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

必须：**先导出 JSON 备份 → 卸载旧版 → 安装新版 → 导入备份**。

卸载会清空 WebView 的 `localStorage`，也就是你所有的账。

## 目录

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── keystore.properties.example     # 复制成 keystore.properties 再填
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/www/index.html   # 构建时自动同步
        ├── res/
        │   ├── mipmap-*/           # 启动图标（从原包提取）
        │   ├── mipmap-anydpi-v26/ic_launcher.xml
        │   └── values/{colors,strings,themes}.xml
        └── java/com/xiaoman/ledger/
            ├── MainActivity.java      # WebView 容器 + JS Bridge
            ├── AssetServer.java       # 127.0.0.1:4178 静态资源服务
            ├── AutoRecordService.java # NotificationListenerService
            ├── AutoRecordParser.java  # 金额/商户/方向解析
            └── AutoRecordStore.java   # JSONL 队列 + 去重
```

## 已知待办

如果你要让这个工程真正可用，按优先级：

1. **跑通编译**。没有编译验证是最大风险，先 `assembleDebug` 跑一次，把报错逐个修掉。
2. **`MainActivity.onBackPressed()`** 在 API 33+ 已废弃。当前用 `enableOnBackInvokedCallback=false` 走旧路径（与原包一致），但建议迁到 `OnBackInvokedDispatcher`。
3. **补 `network_security_config.xml`**，把明文流量限制到 `127.0.0.1`，替换掉全局的 `usesCleartextTraffic="true"`。
4. **主题**。当前用系统 `Theme.DeviceDefault.Light.NoActionBar`，与原包的 Material 主题视觉上有差异。要一致就引入 `com.google.android.material` 并换 parent。
5. **`android:allowBackup`** 建议改 `false`，防止 `adb backup` 导出账本。
6. **`AutoRecordParser` 的正则**需要拿真实通知文案回归测试。DEX 里只能看到片段，这里的还原未必准确。
