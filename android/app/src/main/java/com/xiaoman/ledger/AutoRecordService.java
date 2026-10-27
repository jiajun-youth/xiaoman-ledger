package com.xiaoman.ledger;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

/**
 * 自动记账的原生入口。
 *
 * <p>系统在用户授予「通知使用权」后回调 {@link #onNotificationPosted}。
 * 注意这是<b>系统主动推送</b>：本服务无法读取历史通知，只能拿到授权之后新产生的。
 *
 * <p>处理流程刻意做得保守：
 * <ol>
 *   <li>先按包名白名单过滤，不在名单里的通知<b>立即返回</b>，不解析、不落盘</li>
 *   <li>拼出通知文本后交给 {@link AutoRecordParser}</li>
 *   <li>金额为 0 或命中忽略词的直接丢弃</li>
 *   <li>过 {@link AutoRecordStore#markIfNew} 去重</li>
 *   <li>只有通过以上全部检查的，才写进私有目录的队列文件</li>
 * </ol>
 * 前端轮询把队列取走，变成「待确认」记录，用户点了才真正入账。
 *
 * <p><b>关于隐私</b>：通知使用权在 Android 里是权限最大的授权之一，技术上开启后
 * 可以读取所有 App 的通知内容。本类的包名过滤是「拿到之后才过滤」，
 * 也就是说系统层面仍然把全部通知交给了这个进程——这是该 API 的固有性质，
 * 无法通过代码规避。用户需要理解这一点再决定是否开启。
 *
 * <p><b>本文件根据 v1.2.0 安装包逆向重建。</b>
 */
public class AutoRecordService extends NotificationListenerService {

    private static final String TAG = "XiaomanAuto";

    /** 支付宝。 */
    static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";
    /** 微信。 */
    static final String PKG_WECHAT = "com.tencent.mm";
    /**
     * adb shell 的运行身份。加进来是为了调试：
     * {@code adb shell cmd notification post -t "支付成功" tag "¥12.34 瑞幸咖啡"}
     * 可以在不真实消费的情况下验证解析逻辑。
     * 要利用它需要 USB 调试或 root，普通用户没有暴露面。
     */
    static final String PKG_SHELL = "com.android.shell";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "notification listener connected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        try {
            handle(sbn);
        } catch (Exception e) {
            Log.w(TAG, "handle notification failed", e);
        }
    }

    private void handle(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        String source = sourceOf(pkg);
        if (source == null) {
            return;   // 不在白名单，直接丢弃
        }

        Notification notification = sbn.getNotification();
        String text = extractText(notification);
        if (TextUtils.isEmpty(text)) {
            return;
        }

        AutoRecordParser.Result result = AutoRecordParser.parse(text, source);
        if (result.isEmpty()) {
            return;
        }

        String fingerprint = fingerprintOf(source, result);
        if (!AutoRecordStore.markIfNew(this, fingerprint)) {
            Log.d(TAG, "duplicate notification skipped");
            return;
        }

        AutoRecordStore.append(this, result);
        Log.d(TAG, "captured " + (result.isIncome() ? "income" : "expense"));
    }

    /** 包名 → 来源标识；不在白名单返回 null。 */
    static String sourceOf(String pkg) {
        if (PKG_ALIPAY.equals(pkg)) {
            return "alipay";
        }
        if (PKG_WECHAT.equals(pkg)) {
            return "wechat";
        }
        if (PKG_SHELL.equals(pkg)) {
            return "shell";
        }
        return null;
    }

    /**
     * 把通知里所有可能有用的文本拼起来。
     * 支付类通知的金额有时在 title，有时在 text，有时只在 bigText 里，所以都取。
     */
    static String extractText(Notification notification) {
        StringBuilder sb = new StringBuilder(128);

        Bundle extras = notification.extras;
        if (extras != null) {
            appendPart(sb, extras.getCharSequence(Notification.EXTRA_TITLE));
            appendPart(sb, extras.getCharSequence(Notification.EXTRA_TEXT));
            appendPart(sb, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            appendPart(sb, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            appendPart(sb, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));
        }
        if (notification.tickerText != null) {
            appendPart(sb, notification.tickerText);
        }
        return sb.toString().trim();
    }

    private static void appendPart(StringBuilder sb, CharSequence part) {
        if (part == null) {
            return;
        }
        String value = part.toString().trim();
        if (value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(value);
    }

    /** 指纹只用「来源 + 金额 + 正文前 80 字」，避免时间戳导致的伪差异。 */
    static String fingerprintOf(String source, AutoRecordParser.Result result) {
        String text = result.getText() == null ? "" : result.getText();
        if (text.length() > 80) {
            text = text.substring(0, 80);
        }
        return source + "|" + result.getAmountCents() + "|" + text.hashCode();
    }

    // ------------------------------------------------------------------
    // 权限状态与设置入口
    // ------------------------------------------------------------------

    /** 判断本应用是否已获得通知使用权。 */
    public static boolean isAccessGranted(Context context) {
        if (context == null) {
            return false;
        }
        try {
            String flat = Settings.Secure.getString(
                    context.getContentResolver(), "enabled_notification_listeners");
            if (TextUtils.isEmpty(flat)) {
                return false;
            }
            ComponentName expected = new ComponentName(context, AutoRecordService.class);
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName component = ComponentName.unflattenFromString(name);
                if (component != null && component.equals(expected)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.w(TAG, "read listener settings failed", e);
            return false;
        }
    }

    /** 跳到系统「通知使用权」列表页。各 ROM 页面路径不同，失败时降级到应用详情页。 */
    public static void openSettings(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return;
        } catch (Exception e) {
            Log.w(TAG, "open listener settings failed", e);
        }

        Intent fallback = new Intent(Settings.ACTION_SETTINGS);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(fallback);
        } catch (Exception e) {
            Log.w(TAG, "open settings failed", e);
        }
    }
}
