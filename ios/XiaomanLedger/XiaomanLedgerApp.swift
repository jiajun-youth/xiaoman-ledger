import SwiftUI

/// 小满账本 iOS 外壳。
///
/// 与 Android 版同样的思路：界面和逻辑全部在一个 HTML 文件里，
/// 原生这边只负责把 WKWebView 升起来、指向本地打包好的页面。
///
/// 最低系统版本：iOS 15.0
@main
struct XiaomanLedgerApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.container, edges: .bottom)
                .preferredColorScheme(.light)
        }
    }
}
