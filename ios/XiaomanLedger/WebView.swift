import SwiftUI
import WebKit

/// 承载 index.html 的 WebView。
///
/// ## 为什么用 loadFileURL 而不是本地 HTTP 服务
///
/// Android 版起了一个 `127.0.0.1:4178` 的迷你服务器，因为 Android WebView
/// 在 `file://` 下 localStorage 的 origin 是 `null`，部分 ROM 会直接抛异常。
///
/// iOS 的 WKWebView 没有这个问题：只要 `allowingReadAccessTo` 给到 bundle 根目录，
/// `file://` 页面就能正常读写 localStorage 并持久化。所以这里不引入服务器，
/// 少一个后台线程、少一个端口、也少一处出错点。
///
/// 如果将来某个 iOS 版本上发现 localStorage 不持久，
/// 再照 Android 的思路补一个回环服务即可。
struct ContentView: View {
    var body: some View {
        WebView()
            .background(Color(red: 0.957, green: 0.945, blue: 0.914)) // #F4F1E9
    }
}

struct WebView: UIViewRepresentable {

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.allowsInlineMediaPlayback = true

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator

        // 页面内容自己用 env(safe-area-inset-*) 处理刘海和底部横条，
        // 所以关掉 WKWebView 的自动内边距，避免双重留白。
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.bounces = false
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.allowsBackForwardNavigationGestures = false

        if let url = Bundle.main.url(forResource: "index", withExtension: "html") {
            webView.loadFileURL(url, allowingReadAccessTo: Bundle.main.bundleURL)
        } else {
            // 打包时漏了 index.html——这是最常见的构建错误
            let html = """
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <body style="font:16px -apple-system;padding:40px;color:#173f32;text-align:center">
              <h2>资源缺失</h2>
              <p>没有在 App 包里找到 index.html。<br>
              请把仓库根目录的 index.html 加入 target 的
              <b>Copy Bundle Resources</b>。</p>
            </body>
            """
            webView.loadHTMLString(html, baseURL: nil)
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator: NSObject, WKNavigationDelegate {

        /// 站外链接交给 Safari 打开，别把浏览器困在 App 里。
        func webView(_ webView: WKWebView,
                     decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            if url.isFileURL {
                decisionHandler(.allow)
                return
            }
            if url.scheme == "http" || url.scheme == "https" {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }
    }
}
