package com.xiaoman.ledger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * WebView 外壳。
 *
 * <p>本类不承载业务逻辑——记账的全部实现都在 assets/www/index.html 里。
 * 这里只做三件事：
 * <ol>
 *   <li>起一个只监听回环地址的静态资源服务器，用它替代 {@code file://}</li>
 *   <li>把 WebView 指过去</li>
 *   <li>暴露 {@code XiaomanAndroid} 这个 JS Bridge</li>
 * </ol>
 *
 * <p>为什么不用 {@code file:///android_asset/...}：
 * {@code file://} 下 localStorage 的 origin 是 {@code null}，各 ROM 行为不一致，
 * 部分机型直接抛 SecurityError。走 {@code http://127.0.0.1:4178} 后 origin 稳定，
 * localStorage 才能可靠工作。
 *
 * <p><b>本文件根据 v1.2.0 安装包逆向重建</b>，类名与方法名与原包一致，
 * 具体实现为按行为还原，未与原工程逐行比对。
 */
public class MainActivity extends Activity {

    private static final String TAG = "XiaomanMain";
    private static final String SERVER_URL = "http://127.0.0.1:4178/";
    private static final String BRIDGE_NAME = "XiaomanAndroid";

    private WebView webView;
    private AssetServer assetServer;

    /** saveFile 的挂起内容：等用户选完保存位置再写。 */
    private String pendingFileName;
    private String pendingFileContent;
    private static final int REQUEST_CREATE_DOCUMENT = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        configureWebView();

        try {
            assetServer = new AssetServer(getAssets(), "www", 4178);
            assetServer.start();
        } catch (IOException e) {
            Log.e(TAG, "asset server start failed", e);
        }

        webView.loadUrl(SERVER_URL);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);          // localStorage 依赖它
        settings.setAllowFileAccess(false);           // 页面不需要读本地文件
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 只允许留在本地服务器里，其它链接一律交给系统浏览器
                if (url != null && url.startsWith(SERVER_URL)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Log.w(TAG, "open external url failed", e);
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), BRIDGE_NAME);
    }

    /**
     * 系统返回键先问网页：网页里若有打开的弹窗，由它自己关掉并返回 true。
     * 网页没有处理时，才真正退出。
     */
    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return window.xiaomanHandleBack?window.xiaomanHandleBack():false}"
                        + "catch(e){return false}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!"true".equals(value)) {
                            finish();
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (assetServer != null) {
            assetServer.stop();
            assetServer = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface(BRIDGE_NAME);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATE_DOCUMENT) {
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            writePendingFile(data.getData());
        } else {
            notifyWeb("exportCancel", "");
        }
        pendingFileName = null;
        pendingFileContent = null;
    }

    /** 把导出内容写到 SAF 返回的 Uri。 */
    private void writePendingFile(Uri uri) {
        String content = pendingFileContent;
        if (content == null) {
            return;
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                throw new IOException("openOutputStream returned null");
            }
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
            notifyWeb("exportDone", pendingFileName == null ? "" : pendingFileName);
        } catch (Exception e) {
            Log.e(TAG, "save file failed", e);
            notifyWeb("exportFailed", e.getMessage() == null ? "" : e.getMessage());
        }
    }

    /** 在页面里触发一个自定义事件，让前端知道导出结果。 */
    private void notifyWeb(String name, String detail) {
        if (webView == null) {
            return;
        }
        String script = "window.dispatchEvent(new CustomEvent('xiaoman:"
                + name + "',{detail:" + jsonString(detail) + "}))";
        webView.evaluateJavascript(script, null);
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * 暴露给网页的桥。网页侧调用 window.XiaomanAndroid.xxx()。
     *
     * <p>安全提醒：addJavascriptInterface 在 API 17+ 只允许 @JavascriptInterface
     * 标注的方法被调用，但页面内容本身不可信时仍然危险。
     * 本项目的页面来自本地 assets，不加载任何远程内容，因此风险可控。
     */
    private class AndroidBridge {

        /** 弹出系统文件选择器保存导出内容。 */
        @JavascriptInterface
        public void saveFile(String name, String content) {
            pendingFileName = name;
            pendingFileContent = content;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(guessMime(name));
            intent.putExtra(Intent.EXTRA_TITLE, name);
            try {
                startActivityForResult(intent, REQUEST_CREATE_DOCUMENT);
            } catch (Exception e) {
                Log.e(TAG, "open document picker failed", e);
                pendingFileName = null;
                pendingFileContent = null;
            }
        }

        /** 通知使用权是否已授予。 */
        @JavascriptInterface
        public boolean hasNotificationAccess() {
            return AutoRecordService.isAccessGranted(MainActivity.this);
        }

        /** 跳到系统「通知使用权」设置页。 */
        @JavascriptInterface
        public void openNotificationAccessSettings() {
            AutoRecordService.openSettings(MainActivity.this);
        }

        /** 取走并清空自动记账队列，返回 JSON 数组字符串。 */
        @JavascriptInterface
        public String drainAutoRecords() {
            return AutoRecordStore.drain(MainActivity.this);
        }
    }

    private static String guessMime(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        return "text/plain";
    }
}
