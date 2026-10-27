package com.xiaoman.ledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动记账的本地缓冲：一个 JSONL 文件 + 一份去重指纹。
 *
 * <p>为什么用文件而不是 SharedPreferences：这里要做「追加 + 批量取走 + 清空」，
 * 文件天然支持，且不受 SharedPreferences 的大小与并发限制。数据量也确实很小。
 *
 * <p>写入位置是 {@code context.getFilesDir()}，即应用私有目录——
 * 未 root 的设备上其它 App 读不到，adb 也读不到。
 */
public final class AutoRecordStore {

    private static final String TAG = "XiaomanAuto";

    static final String PREFS = "xiaoman_auto";
    static final String KEY_RECENT = "recent_fingerprints";
    static final String QUEUE_FILE = "auto_queue.jsonl";

    /** 指纹保留时长：60 秒内完全相同的通知视为重复。 */
    static final long DEDUP_WINDOW_MS = 60_000L;

    /** 队列上限，超出后丢弃最旧的，避免无限增长。 */
    static final int MAX_LINES = 200;

    private AutoRecordStore() {
    }

    /**
     * 去重判断。第一次见到该指纹返回 true 并记下，重复出现返回 false。
     * 指纹 = hash(source + 金额 + 正文前 80 字)。
     */
    static boolean markIfNew(Context context, String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return true;
        }
        long now = System.currentTimeMillis();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT, "");
        StringBuilder next = new StringBuilder(raw.length() + 64);
        boolean seen = false;

        if (!raw.isEmpty()) {
            String[] entries = raw.split(";");
            for (String entry : entries) {
                int at = entry.indexOf('@');
                if (at <= 0) {
                    continue;
                }
                String hash = entry.substring(0, at);
                long when;
                try {
                    when = Long.parseLong(entry.substring(at + 1));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (now - when > DEDUP_WINDOW_MS) {
                    continue;   // 过期指纹顺手清掉
                }
                if (hash.equals(fingerprint)) {
                    seen = true;
                }
                if (next.length() > 0) {
                    next.append(';');
                }
                next.append(hash).append('@').append(when);
            }
        }

        if (next.length() > 0) {
            next.append(';');
        }
        next.append(fingerprint).append('@').append(now);

        try {
            prefs.edit().putString(KEY_RECENT, next.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "dedup failed", e);
            return !seen;
        }
        return !seen;
    }

    /** 追加一条记录到队列。 */
    static void append(Context context, AutoRecordParser.Result result) {
        if (result == null || result.isEmpty()) {
            return;
        }
        String line = toJson(result);
        File file = queueFile(context);
        synchronized (AutoRecordStore.class) {
            List<String> lines = readLines(file);
            lines.add(line);
            if (lines.size() > MAX_LINES) {
                lines = new ArrayList<>(lines.subList(lines.size() - MAX_LINES, lines.size()));
            }
            writeLines(file, lines);
        }
    }

    /** 取走全部记录并清空队列。返回 JSON 数组字符串（给 JS Bridge 用）。 */
    public static String drain(Context context) {
        File file = queueFile(context);
        List<String> lines;
        synchronized (AutoRecordStore.class) {
            lines = readLines(file);
            writeLines(file, new ArrayList<>());
        }
        if (lines.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(lines.size() * 96 + 2);
        sb.append('[');
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(lines.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    /** 队列里还有多少条待处理。 */
    static int pendingCount(Context context) {
        return readLines(queueFile(context)).size();
    }

    static File queueFile(Context context) {
        return new File(context.getFilesDir(), QUEUE_FILE);
    }

    static List<String> readLines(File file) {
        List<String> lines = new ArrayList<>();
        if (file == null || !file.exists()) {
            return lines;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "read queue failed", e);
        }
        return lines;
    }

    static void writeLines(File file, List<String> lines) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8);
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
            writer.flush();
        } catch (Exception e) {
            Log.w(TAG, "write queue failed", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // 关闭失败无需处理
                }
            }
        }
    }

    /** 手工拼 JSON，避免为了两条字段引入 JSON 库。 */
    static String toJson(AutoRecordParser.Result result) {
        StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        sb.append("\"amount\":").append(result.amountCents);
        sb.append(",\"merchant\":").append(quote(result.merchant));
        sb.append(",\"source\":").append(quote(result.source));
        sb.append(",\"dir\":").append(quote(result.income ? "income" : "expense"));
        sb.append(",\"text\":").append(quote(result.text));
        sb.append(",\"time\":").append(System.currentTimeMillis());
        sb.append('}');
        return sb.toString();
    }

    /** 与前端 jsonString 一致的转义规则。 */
    static String quote(String value) {
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
}
