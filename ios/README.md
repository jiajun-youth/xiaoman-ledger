# iOS 工程

> ## ⚠️ 先读这段：这里给不出 `.ipa` 文件
>
> **`.ipa` 只能在 macOS 上用 Xcode 编译并签名，Windows 上做不到。**
> 这不是配置问题——Apple 的编译工具链和签名机制都没有 Windows 版本。
>
> 所以这个目录给你的是 **源码 + 构建步骤**，需要你在 Mac 上花 10 分钟打一次包。
>
> **iPhone 用户现在就能用的方案是网页版（PWA）**，不需要 Mac、不需要花钱、不需要审核：
> 用 Safari 打开 <https://jiajun-youth.github.io/xiaoman-ledger/> → 分享 → **添加到主屏幕**。
> 之后它就是一个全屏独立的 App，图标、启动方式都和原生一样。
> 具体步骤见仓库 README 的「iOS 安装」一节。

---

## 和 Android 版的区别

| 能力 | Android | iOS |
| --- | --- | --- |
| 记账、统计、分类、导入导出 | ✅ | ✅ |
| 数据存本机 | ✅ | ✅ |
| 加到桌面、全屏运行 | ✅ | ✅（PWA 或 App 都可以） |
| **自动记账**（读支付通知） | ✅ | ❌ **不可能** |
| 安装方式 | 下载 APK 直接装 | Mac + Xcode，或 PWA |

**自动记账在 iOS 上没有对应能力。** Android 的 `NotificationListenerService` 允许第三方应用读取通知，
iOS 完全不开放这个接口——没有任何官方途径能让 App 读到微信/支付宝的通知内容。
所以 iOS 版只能是纯手动记账。这不是没做，是系统不允许。

---

## 构建步骤

**环境**：macOS 13+、Xcode 15+。

1. Xcode → **File → New → Project**
2. 选 **iOS → App**，填：
   - Product Name: `XiaomanLedger`
   - Interface: **SwiftUI**
   - Language: **Swift**
   - 取消勾选 Core Data / Tests
3. 删掉自动生成的 `XiaomanLedgerApp.swift` 和 `ContentView.swift`（右键 → Delete → Move to Trash）
4. 把本目录 `XiaomanLedger/` 下的两个 `.swift` 文件拖进项目导航器，勾选 **Copy items if needed** 和 target 成员
5. 把**仓库根目录**的这几个文件拖进项目，同样勾选 target 成员：
   - `index.html`
   - `apple-touch-icon.png`
   - `icon-192.png`
   - `icon-512.png`
   > 这步最容易漏。漏了会在启动时看到「资源缺失」提示页。
6. **Signing & Capabilities** → Team 选你的 Apple ID，Bundle Identifier 改成你自己的，
   例如 `com.yourname.xiaomanledger`
7. **Info** 标签页补三项：
   - `Display Name` → `小满账本`
   - `Supported interface orientations` → 只保留 `Portrait`
   - `Appearance` → `Light`
8. 插上 iPhone，选中设备，点 ▶️ 运行

`index.html` 必须在 **Copy Bundle Resources** 里，代码用
`Bundle.main.url(forResource: "index", withExtension: "html")` 找它。

---

## 怎么分发

做完包只是第一步，**能不能装到别人手机上取决于签名**。四条路：

| 方式 | 成本 | 谁能装 | 有效期 | 适合 |
| --- | --- | --- | --- | --- |
| **免费 Apple ID** | 0 | 只有你自己，需连 Mac | **7 天**，过期要重签 | 自己用、开发调试 |
| **Ad Hoc** | $99/年 | 最多 100 台，需登记设备 UDID | 1 年 | 给家人朋友小范围装 |
| **TestFlight** | $99/年 | 最多 10000 人，发链接即可 | 每个构建 90 天 | 公开测试 |
| **App Store** | $99/年 | 所有人 | 长期 | 正式上架 |

**免费 Apple ID 不能用 Ad Hoc 和 TestFlight**，只能装在自己设备上，而且 7 天就要重签一次。
想给别人装，绕不开每年 $99 的开发者账号。

### 上架 App Store 的现实风险

Apple 审核指南 **4.2 Minimum Functionality** 明确拒绝「只是把网页包起来」的 App。
这个项目去掉自动记账后，确实接近「网页套壳」——**被拒的概率不低**。

要提高通过率，可以补一些原生能力，比如：

- 用 `UserNotifications` 做每日记账提醒
- 用 `Charts` 或原生绘制做统计图（而不是网页里的柱状图）
- 支持 Widget 显示本月支出
- 用 `LocalAuthentication`（Face ID）给账本加锁

这三项里任意一两项做完，4.2 就不是问题了。但工作量远超「打包一下」。

---

## 结论

| 你的目标 | 建议 |
| --- | --- |
| 自己 iPhone 上用 | **PWA 添加到主屏幕**，零成本，立刻可用 |
| 给几个朋友装 | PWA 发链接最快；非要原生就用 Ad Hoc + 开发者账号 |
| 正式发布上架 | 做好补原生功能的准备，并接受审核不确定性 |
| 只想验证 iOS 上跑不跑得起来 | 免费 Apple ID + Xcode 直接运行，够了 |
