package com.xiaoman.ledger;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 极简静态资源服务器，只监听 127.0.0.1。
 *
 * <p>存在的唯一原因：让 WebView 的 origin 变成 {@code http://127.0.0.1:4178}
 * 而不是不稳定的 {@code file://}，从而保证 localStorage 正常工作。
 *
 * <p>它不对外网开放——{@link InetSocketAddress} 绑定的是回环地址，
 * 局域网内其它设备也访问不到。
 *
 * <p><b>本文件根据 v1.2.0 安装包逆向重建</b>（DEX 中可见
 * {@code xiaoman-asset-server} 线程名与 {@code http://127.0.0.1:4178/} 字面量）。
 */
public class AssetServer {

    private static final String TAG = "XiaomanAsset";

    private final AssetManager assets;
    private final String rootDir;
    private final int port;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("htm", "text/html; charset=utf-8");
        MIME.put("js", "application/javascript; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8");
        MIME.put("json", "application/json; charset=utf-8");
        MIME.put("svg", "image/svg+xml");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("webp", "image/webp");
        MIME.put("ico", "image/x-icon");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
        MIME.put("txt", "text/plain; charset=utf-8");
    }

    public AssetServer(AssetManager assets, String rootDir, int port) {
        this.assets = assets;
        this.rootDir = rootDir;
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        // 只绑回环，不绑 0.0.0.0——局域网无法访问
        serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 16);
        serverSocket.setReuseAddress(true);
        running = true;

        acceptThread = new Thread(this::acceptLoop, "xiaoman-asset-server");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "close server socket failed", e);
        }
        serverSocket = null;
    }

    private void acceptLoop() {
        while (running) {
            try (Socket client = serverSocket.accept()) {
                client.setSoTimeout(5000);
                handle(client);
            } catch (IOException e) {
                if (running) {
                    Log.w(TAG, "accept failed", e);
                }
            }
        }
    }

    private void handle(Socket client) {
        String requestLine;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            requestLine = reader.readLine();
        } catch (IOException e) {
            Log.w(TAG, "read request failed", e);
            return;
        }
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return;
        }

        String path = parts[1];
        int queryAt = path.indexOf('?');
        if (queryAt >= 0) {
            path = path.substring(0, queryAt);
        }
        int hashAt = path.indexOf('#');
        if (hashAt >= 0) {
            path = path.substring(0, hashAt);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.isEmpty()) {
            path = "index.html";
        }
        // 阻止 ../ 穿越出 assets 目录
        if (path.contains("..")) {
            respondError(client, 403, "Forbidden");
            return;
        }

        String assetPath = rootDir.isEmpty() ? path : rootDir + "/" + path;
        byte[] body;
        try (InputStream in = assets.open(assetPath)) {
            body = readAll(in);
        } catch (IOException e) {
            respondError(client, 404, "Not Found");
            return;
        }

        String mime = mimeOf(path);
        try (OutputStream out = client.getOutputStream()) {
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + mime + "\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "write response failed", e);
        }
    }

    private void respondError(Socket client, int code, String message) {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = client.getOutputStream()) {
            String header = "HTTP/1.1 " + code + " " + message + "\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            Log.w(TAG, "write error response failed", e);
        }
    }

    private static String mimeOf(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "application/octet-stream";
        }
        String ext = path.substring(dot + 1).toLowerCase();
        String mime = MIME.get(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }
}
