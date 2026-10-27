# 小满账本 v1.2.0 安装包分析报告

> 分析对象：`小满账本-1.2.0-Android.apk`
> 分析时间：2026-10-27
> 分析方式：静态分析（解包 + 清单解析 + DEX 字符串提取 + 签名证书解析），未在真机运行

---

## 一、结论速览

| 维度 | 结论 |
| --- | --- |
| 应用类型 | **WebView 套壳应用**，业务逻辑 100% 在前端单文件 HTML 里 |
| 包名 / 版本 | `com.xiaoman.ledger` / versionName `1.2.0`、versionCode `4` |
| 体积 | 148,932 字节（约 145 KB），19 个文件 |
| 联网行为 | **无任何外网请求**。`INTERNET` 权限仅用于本机回环地址 |
| 数据外传 | **未发现**。无 HTTP 客户端、无上报端点、无加密回传 |
| 安装包性质 | 自签名（非 Google Play / 应用商店分发），可正常侧载安装 |
| 主要风险 | 通知监听权限范围极大；`allowBackup=true`；`usesCleartextTraffic=true`；未上架导致无更新渠道 |
| 是否恶意 | **从静态证据看不像恶意软件**，但需理解其权限代价后再决定是否安装 |

一句话：这是一个**诚实的小工具**——技术上该有的隐私声明都对得上，代码里也确实没有偷偷联网。真正需要你权衡的只有一件事：为了「自动记账」，要不要把「读取全部通知」的权力交出去。

---

## 二、包结构

解包后一共 19 个文件：

```
AndroidManifest.xml          3,312 B    二进制 XML（AXML）
classes.dex                 17,996 B    DEX 1
classes2.dex                14,428 B    DEX 2
resources.arsc               2,404 B    资源索引表
assets/www/index.html      228,770 B    ★ 整个应用主体
res/mipmap-*/ic_launcher*            应用图标 6 套密度
META-INF/MANIFEST.MF / .SF / .RSA     v1 签名文件
```

没有 `lib/` 目录 → **不含任何原生 `.so`**，没有 JNI，没有第三方加固壳。
没有 `kotlin/` 目录 → 原生部分用 Java 写（`AutoRecordParser.java`、`AutoRecordService.java`、`AutoRecordStore.java` 三个源文件名留在 DEX 里）。

**体积为什么只有 145 KB**：因为重的东西全在 `assets/www/index.html` 一个文件里（228 KB，压缩后约 60 KB），原生代码只有 32 KB 的 DEX，图标是全套 PNG 里最大的开销。这个包没有任何第三方 SDK。

---

## 三、AndroidManifest.xml

清单是编译后的二进制格式（AXML），本次用自写解析器还原出完整的元素树：

```xml
<manifest package="com.xiaoman.ledger"
          versionCode="4" versionName="1.2.0"
          compileSdkVersion="33" platformBuildVersionName="13.0.0">

  <uses-sdk minSdkVersion="26" targetSdkVersion="34" />

  <uses-permission name="android.permission.INTERNET" />

  <application allowBackup="true"
               hardwareAccelerated="true"
               supportsRtl="false"
               usesCleartextTraffic="true"
               appCategory="7"
               enableOnBackInvokedCallback="false">

    <activity name=".MainActivity"
              exported="true"
              launchMode="2"          <!-- singleTask -->
              configChanges="0xFA0"
              windowSoftInputMode="0x10">
      <intent-filter>
        <action   name="android.intent.action.MAIN" />
        <category name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>

    <service name=".AutoRecordService"
             permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
             exported="true">
      <intent-filter>
        <action name="android.service.notification.NotificationListenerService" />
      </intent-filter>
    </service>
  </application>
</manifest>
```

### 逐项解读

**`minSdkVersion=26`（Android 8.0）** —— 覆盖 2017 年后的设备。取 26 是因为从 8.0 起通知渠道（Notification Channel）和 `NotificationListenerService` 行为稳定，不用为老版本做兼容分支。

**`targetSdkVersion=34`（Android 14）** —— targetSdk 34 意味着要遵守 Android 13+ 的通知权限模型和 Android 14 的前台服务限制。这是个相对新的目标版本，说明包构建于 2024 年之后。

**只申请了一个权限：`INTERNET`** —— 没有读通讯录、读短信、读存储、定位、相机。这点很干净，值得肯定。

**`usesCleartextTraffic="true"`** —— 允许明文 HTTP。单独看像红旗，但结合架构就合理了：App 起了一个本地 HTTP 服务器（`127.0.0.1:4178`）来托管 `assets/www`，WebView 通过 `http://` 访问它。**明文流量不出本机回环口**，不经过任何网卡。

**`allowBackup="true"`** —— 允许 `adb backup` 导出应用数据。这意味着**物理接触设备的人可以导出你的账本**（`localStorage` 和 `auto_queue.jsonl` 都在导出范围内）。记账数据本身不算高敏，但如果你把它当隐私，这项偏宽松。

**`supportsRtl="false"`** —— 不支持阿拉伯语等从右到左排版，中文应用无所谓。

**`appCategory="7"`** —— 声明为「财务类」应用，影响系统设置里的归类展示。

**`enableOnBackInvokedCallback="false"`** —— 未启用 Android 13 的新返回键回调机制，仍在用旧的 `onBackPressed()`。这解释了 DEX 里为什么有 `lambda$onBackPressed$1`。

**`AutoRecordService` 的 `exported="true"` + `permission=BIND_NOTIFICATION_LISTENER_SERVICE`** —— 这是 `NotificationListenerService` 的**标准写法**，不是配置错误。`exported="true"` 是必须的（系统要能绑定它），而 `permission` 属性保证**只有持 `BIND_NOTIFICATION_LISTENER_SERVICE` 签名的系统组件**能绑定。第三方 App 无法直接调用这个服务。这一点常被误读为漏洞，实际是模板生成的正确用法。

---

## 四、运行时架构

这是理解整个包的关键。它不是「原生 App + 一点网页」，而是「**网页是本体，原生只是外壳**」。

```
┌─────────────────────────────────────────────────────────┐
│  AutoRecordService (NotificationListenerService)        │
│    系统回调 onNotificationPosted()                       │
│         ↓ 按包名过滤（支付宝/微信/com.android.shell）      │
│    AutoRecordParser  正则提金额、商户、收支方向            │
│         ↓                                               │
│    AutoRecordStore   去重 + 追加写私有目录                 │
│         ↓                                               │
│    files/auto_queue.jsonl   +  SharedPreferences          │
└──────────────────────┬──────────────────────────────────┘
                       │ 读取队列
┌──────────────────────▼──────────────────────────────────┐
│  MainActivity                                            │
│    AssetServer ── 监听 127.0.0.1:4178，托管 assets/www    │
│         ↑                                               │
│    WebView ── loadUrl("http://127.0.0.1:4178/")          │
│         ↕  addJavascriptInterface("XiaomanAndroid")      │
│    index.html（HTML+CSS+JS 全在里面）                     │
│         ↓ localStorage['xiaoman-ledger-v1']              │
└─────────────────────────────────────────────────────────┘
```

### 为什么不用 `file://`

`file:///android_asset/www/index.html` 也能加载页面，但 `file://` 下 `localStorage` 的 origin 是 `null`，不同 ROM 上行为不一致，甚至直接抛 `SecurityError`。起本地 HTTP 服务后，origin 变成稳定的 `http://127.0.0.1:4178`，`localStorage` 正常工作。**这是本项目最巧妙的一处工程决策**，也解释了 `INTERNET` 权限和明文流量的由来。

DEX 里的物证：

```
http://127.0.0.1:4178/        ← 唯一的 URL 字面量
xiaoman-asset-server          ← 服务线程名
Ljava/net/ServerSocket;       ← 只用了 ServerSocket，没有 HttpURLConnection
```

全部网络相关的类只有 `InetSocketAddress` / `ServerSocket` / `Socket` / `SocketAddress`，**没有一个 HTTP 客户端**，也没有 `okhttp`、`HttpURLConnection`、`Retrofit`。

### JS Bridge

`addJavascriptInterface(..., "XiaomanAndroid")`，网页侧通过 `window.XiaomanAndroid` 调用，DEX 和 HTML 双向印证，暴露四个方法：

| 方法 | 作用 |
| --- | --- |
| `saveFile(name, content)` | 把导出的 JSON/CSV 写入用户选择的位置（`lambda$saveFile$0`） |
| `hasNotificationAccess()` | 查询通知使用权是否已授予 |
| `openNotificationAccessSettings()` | 跳转系统「通知使用权」设置页 |
| `drainAutoRecords()` | 取走并清空 `auto_queue.jsonl`，返回 JSON 数组 |

前端每 5 秒（`setInterval 5000`）且页面可见时同步一次队列，单次最多吃 120 条。

---

## 五、前端分析

`assets/www/index.html`：**218,910 字符 / 8,503 行**，单文件包含全部 HTML、CSS、JS。

### 干净程度

| 检查项 | 结果 |
| --- | --- |
| `<script src=...>` 外链 | 无 |
| `<link href=...>` 外链 | 无（只有 `data:` 内联 SVG 图标） |
| `fetch(` / `XMLHttpRequest` / `WebSocket` / `sendBeacon` | **0 处** |
| `https?://` 字面量 | **0 个** |
| 第三方库（React/Vue/jQuery/Chart.js） | **0 个**，纯原生 JS |
| `eval` / `new Function` | 无 |

`meta generator` 写得很有意思：`小满账本单文件构建 · v1.2.0`——作者自己知道这是单文件构建产物。

### 数据模型

```js
STORAGE_KEY    = 'xiaoman-ledger-v1'
BACKUP_FORMAT  = 'xiaoman-ledger-backup'
SCHEMA_VERSION = 1
```

存储结构 `{ categories, records, incomes, auto, pending, updatedAt }`。

**金额用「分」存整数**（`amountToCents`），显示时才除以 100——避开了 `0.1 + 0.2 !== 0.3` 的经典浮点坑。这是记账应用的及格线，作者做对了。

### 路由

`location.hash` 驱动的 5 个页面：

```js
ROUTES = new Set(['overview', 'records', 'stats', 'categories', 'auto'])
```

总览 / 流水 / 统计 / 分类 / 自动记账。

### 代码质量亮点

1. **`escapeHTML()` 全程覆盖**——所有拼接进 `innerHTML` 的用户输入（分类名、备注、商户名）都过一遍转义，防 XSS。
2. **CSV 公式注入防护**——`csvCell()` 里对 `= + - @` 开头的内容加 `'` 前缀，防止导出的 CSV 在 Excel 里被当成公式执行。**这个细节很多商业产品都没做**。
3. **数据损坏兜底**——`readStore()` 的 `JSON.parse` 包在 try/catch 里，解析失败时把原始字符串另存到 `xiaoman-ledger-v1-damaged` 再降级为空账本，不静默丢数据。
4. **`normalizeStore()` / `validateBackup()`**——导入备份时逐字段校验类型和范围，不信任外部文件。
5. **存储满额提示**——`saveStore()` 捕获 `QuotaExceededError` 并提示用户先导出备份。

---

## 六、自动记账的实现细节

这块是权限的来源，也是全包唯一需要动脑评估的地方。

### 包名白名单

`classes2.dex` 里的常量：

```java
private static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";
private static final String PKG_WECHAT = "com.tencent.mm";
private static final String PKG_SHELL  = "com.android.shell";
```

`onNotificationPosted(StatusBarNotification sbn)` 拿到通知后，**第一步就是比对 `sbn.getPackageName()`**，不在白名单里的直接 return，连解析都不做、不写盘。

`com.android.shell` 是个值得注意的条目：它是 `adb shell` 的运行身份。把它放进来，作者显然是为了**调试**——用 `adb shell cmd notification post` 造一条假通知来测试解析逻辑，不用真的花钱。这不算后门（需要 USB 调试或 root 才能利用），但如果你是纯粹的用户，可以理解为「一条开发期留的测试通道」。

### 金额解析

三条正则（从 DEX 字符串池还原）：

```
[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)     → ¥12.34
([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元          → 12.34元
([0-9][0-9,]*(?:\.[0-9]{1,2})?)              → 纯数字兜底
```

外加 `AMOUNT_SYMBOL` / `AMOUNT_YUAN` 两个常量做模式选择，`parseDouble` → `Math.round` → 转成分。

### 方向判断

```
EXPENSE_WORDS / INCOME_WORDS / TRANSACTION_WORDS / IGNORE_WORDS
```

- 命中 `INCOME_WORDS`（收款、到账、退款…）→ 收入
- 命中 `EXPENSE_WORDS`（支付、付款、消费…）→ 支出
- 命中 `IGNORE_WORDS` → 丢弃（过滤「优惠券即将过期」这类营销通知）
- 都不命中 → 默认按支出

### 去重

`recent_fingerprints` 存在 `SharedPreferences("xiaoman_auto")`，`DEDUP_WINDOW_MS` 窗口内的相同指纹（`markIfNew`）直接跳过，日志里那句 `duplicate notification skipped` 就是它。

### 存储

`QUEUE_FILE = "auto_queue.jsonl"`，写在 `context.getFilesDir()`（应用私有目录，其他 App 无法读取，未 root 的 adb 也读不到）。`MAX_LINES` 限制队列长度防止无限增长。落盘用的是 `FileOutputStream` + `OutputStreamWriter("UTF-8")`，逐行 JSON（字段：`amount` / `merchant` / `source` / `dir` / `text` / 时间戳）。

**注意：这里没有网络代码。** `classes2.dex` 里除了 `java.io` 和 `android.*`，没有任何网络相关类。

---

## 七、签名信息

```
Subject      CN=Xiaoman Ledger, OU=App, O=Xiaoman, L=Beijing, C=CN
Issuer       CN=Xiaoman Ledger, OU=App, O=Xiaoman, L=Beijing, C=CN   （自签名）
算法          sha384RSA
有效期        2026-10-26 → 2059-09-03
Thumbprint   393182EBCE68B805787ECA6B0B01ECE7E0105A8B
签名方案      v1 (JAR) + v2 + v3（X-Android-APK-Signed: 2, 3）
```

几点说明：

1. **自签名是侧载 APK 的常态**，不代表恶意——所有个人/小众 App 都这样。它只意味着「没有第三方背书」，你必须自己信任来源。
2. **有效期到 2059 年**，密钥生成时给了超长有效期，作者没打算频繁换签。
3. `CN/OU/O/L` 全是占位式的组织信息，没有任何真实公司实体。
4. **签名一致性的现实后果**：如果你日后自己重新打包，签名不同，**无法覆盖安装**这个 v1.2.0，必须先卸载——卸载会清空 `localStorage`。**升级前一定先导出 JSON 备份。**
5. 因为不是从 Google Play / 应用商店分发，**没有自动更新渠道**，也不会被商店的恶意软件扫描覆盖。

---

## 八、风险清单

按「需要你采取行动」的优先级排序：

| # | 风险 | 严重度 | 说明与建议 |
| --- | --- | --- | --- |
| 1 | 通知监听权限范围极大 | **高（本质风险）** | 技术上可读全部 App 的通知。代码虽已按包名过滤，但**授权本身**就是最大让步。不接受就别开自动记账，其他功能不受影响 |
| 2 | `allowBackup="true"` | 中 | 物理接触设备者可通过 `adb backup` 导出全部账本数据。介意就自己重打包时改成 `false` |
| 3 | 无更新渠道 | 中 | 侧载 App 不会被商店扫描，也没有安全更新。建议关注来源，别用来路不明的 v1.3.0 |
| 4 | `usesCleartextTraffic="true"` | 低 | 仅为本地回环服务所需，流量不出本机。若重打包可改用 `networkSecurityConfig` 只对 `127.0.0.1` 放行明文 |
| 5 | `com.android.shell` 在通知白名单 | 低 | 开发调试用。利用需 adb/root 权限，普通用户无实际暴露面 |
| 6 | `exported="true"` 的 Service | 低 | 有 `BIND_NOTIFICATION_LISTENER_SERVICE` 权限保护，第三方 App 无法绑定。标准写法，非缺陷 |
| 7 | 未混淆（R8 `compilation-mode: debug`） | 低 | 源码逻辑容易被逆向。开源项目无所谓 |
| 8 | 数据只存本机，无云备份 | 低 | 手机丢失 / 清数据 = 账本归零。**请定期导出 JSON 备份到电脑** |

### 关于「是否恶意」的判断依据

我按常见的恶意软件特征逐条核对：

| 恶意特征 | 本包情况 |
| --- | --- |
| 硬编码 C2 域名 / IP | **无**。唯一 URL 是 `127.0.0.1:4178` |
| 动态加载 DEX / 插件化 | **无**。无 `DexClassLoader`、无 `System.load` |
| 反射调用系统隐藏 API | **无** |
| 读取短信 / 通讯录 / 通话记录 | **无**，未申请相关权限 |
| 设备指纹采集 | **无**。无 `getImei`、无 `ANDROID_ID`、无 `Build.SERIAL` |
| 后台常驻 / 开机自启 | **无**。没有 `BOOT_COMPLETED`、没有前台服务、没有 JobScheduler |
| 混淆 + 加密字符串 | **无**，字符串明文可读 |
| 申请大量无关权限 | **无**，只有 `INTERNET` |

**结论：静态层面没有恶意行为证据。** 它的权限申请（通知监听）与宣称功能（从支付通知自动记账）严格对应，且在代码里能看到完整、克制的实现——先过滤包名、只写私有目录、不联网。这更像一个开发者自己要用、顺手开源的小工具，而不是有商业动机的软件。

---

## 九、想自己继续研究？

```bash
# 1. 解包（APK 就是 ZIP，改后缀即可）
cp 小满账本-1.2.0-Android.apk app.zip && unzip app.zip -d unpacked

# 2. 读二进制清单（需要 aapt2 或 apkanalyzer）
aapt2 dump xmltree --file AndroidManifest.xml 小满账本-1.2.0-Android.apk

# 3. 反编译 DEX 成 Java（需要 jadx）
jadx -d out 小满账本-1.2.0-Android.apk

# 4. 校验签名
apksigner verify --print-certs 小满账本-1.2.0-Android.apk

# 5. 直接读前端源码（不需要任何工具）
cat unpacked/assets/www/index.html
```

第 5 步是关键：**这个 App 的「源码」本质上就是那个 HTML 文件**，不需要反编译就能完整阅读。本仓库根目录的 `index.html` 就是从安装包里原样提取的。

---

*报告基于静态分析，未覆盖运行时行为、动态加载、服务端交互（因为不存在服务端）。如需更高置信度，可在隔离设备上抓包验证——预期结果：除 `127.0.0.1` 外无任何网络流量。*
