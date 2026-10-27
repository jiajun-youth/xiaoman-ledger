package com.xiaoman.ledger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从支付通知的文本里抽金额、商户、收支方向。
 *
 * <p>输入是支付宝 / 微信支付通知的标题与正文拼接后的字符串，典型形态：
 * <pre>
 *   "微信支付"  "微信支付凭证"  "￥12.34"  "收款方备注:二维码收款"
 *   "支付宝"    "支付成功"      "¥28.00"  "瑞幸咖啡"
 * </pre>
 *
 * <p>解析策略是「三条金额正则依次尝试 + 关键词判方向 + 忽略词过滤」，
 * 不追求覆盖所有文案，宁可漏也不误记——漏了用户能手动补，误记会污染账本。
 *
 * <p><b>本文件根据 v1.2.0 安装包逆向重建。</b>常量名与正则片段来自 DEX 字符串池，
 * 但完整的正则表达式原文无法逐字符还原，此处按片段拼合，
 * 若与线上实际文案不匹配，请优先调整这里的 Pattern。
 */
public final class AutoRecordParser {

    private AutoRecordParser() {
    }

    /** 带货币符号或 RMB 字样的金额。 */
    static final Pattern AMOUNT_SYMBOL =
            Pattern.compile("(?:[¥￥]|RMB|rmb)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");

    /** 「12.34元」形态。 */
    static final Pattern AMOUNT_YUAN =
            Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元");

    /** 兜底：裸数字（放在最后试，容易误命中，所以要求不在长数字串中间）。 */
    static final Pattern AMOUNT_BARE =
            Pattern.compile("(?<![0-9])([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![0-9])");

    /** 商户：常见于「收款方备注:xxx」「商户:xxx」或直接跟在金额后面。 */
    static final Pattern MERCHANT_LABEL =
            Pattern.compile("(?:收款方|商户|付款方|对方)\\s*[:：]\\s*([^,\\s]{1,24})");

    static final String[] EXPENSE_WORDS = {
            "支付成功", "付款成功", "已支付", "支付", "付款", "消费", "扣款", "支出", "转出",
    };

    static final String[] INCOME_WORDS = {
            "收款", "到账", "已收款", "收入", "退款", "转入", "红包入账",
    };

    /** 命中这些词直接丢弃，不进入待确认队列。 */
    static final String[] IGNORE_WORDS = {
            "优惠券", "红包即将", "即将过期", "领取", "推荐", "广告", "活动", "会员",
            "积分", "抽奖", "恭喜", "邀请", "更新", "版本",
    };

    static final String[] TRANSACTION_WORDS = {
            "支付成功", "付款成功", "消费", "收款", "到账", "退款",
    };

    /** 解析结果。amountCents 为 0 表示没抽到金额。 */
    public static final class Result {
        public final long amountCents;
        public final String merchant;
        public final boolean income;
        public final String source;
        public final String text;

        Result(long amountCents, String merchant, boolean income, String source, String text) {
            this.amountCents = amountCents;
            this.merchant = merchant;
            this.income = income;
            this.source = source;
            this.text = text;
        }

        boolean isEmpty() {
            return amountCents <= 0;
        }

        public long getAmountCents() {
            return amountCents;
        }

        public String getMerchant() {
            return merchant;
        }

        public boolean isIncome() {
            return income;
        }

        public String getSource() {
            return source;
        }

        public String getText() {
            return text;
        }
    }

    /**
     * 主入口。
     *
     * @param text   通知标题 + 正文拼接
     * @param source 来源标识：{@code "alipay"} / {@code "wechat"} / {@code "shell"}
     * @return 解析结果；金额为 0 时调用方应丢弃
     */
    public static Result parse(String text, String source) {
        if (text == null || text.trim().isEmpty()) {
            return new Result(0, "", false, source, "");
        }

        String normalized = normalize(text);

        if (containsAny(normalized, IGNORE_WORDS)) {
            return new Result(0, "", false, source, normalized);
        }
        // 不含任何交易词，视为营销 / 系统通知
        if (!containsAny(normalized, TRANSACTION_WORDS)) {
            return new Result(0, "", false, source, normalized);
        }

        long cents = matchAmount(normalized);
        if (cents <= 0) {
            return new Result(0, "", false, source, normalized);
        }

        boolean income = isIncome(normalized);
        String merchant = parseMerchant(normalized);
        return new Result(cents, merchant, income, source, normalized);
    }

    /** 全角转半角、去多余空白，让正则只在一种形态上工作。 */
    static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u3000') {                 // 全角空格
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {   // 全角 ASCII
                sb.append((char) (c - 0xFEE0));
            } else if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    static long matchAmount(String text) {
        long cents = extract(AMOUNT_SYMBOL, text);
        if (cents > 0) {
            return cents;
        }
        cents = extract(AMOUNT_YUAN, text);
        if (cents > 0) {
            return cents;
        }
        return extract(AMOUNT_BARE, text);
    }

    private static long extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return 0;
        }
        String raw = matcher.group(1);
        if (raw == null) {
            return 0;
        }
        try {
            // 去掉千分位逗号后按「元」解析，再四舍五入到分
            double yuan = Double.parseDouble(raw.replace(",", ""));
            if (yuan <= 0 || yuan > 10_000_000d) {     // 上限一千万，防脏数据
                return 0;
            }
            return Math.round(yuan * 100d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static boolean isIncome(String text) {
        boolean hasIncome = containsAny(text, INCOME_WORDS);
        boolean hasExpense = containsAny(text, EXPENSE_WORDS);
        if (hasIncome && !hasExpense) {
            return true;
        }
        if (hasExpense && !hasIncome) {
            return false;
        }
        // 同时命中时按「退款/收款」优先，因为这类通知里常带「原支付」字样
        return hasIncome;
    }

    static String parseMerchant(String text) {
        Matcher matcher = MERCHANT_LABEL.matcher(text);
        if (matcher.find()) {
            return sanitizeMerchant(matcher.group(1));
        }
        // 没标签时，取金额后面的第一段连续中文（常见于「支付成功 ¥12.34 瑞幸咖啡」）
        Matcher amount = AMOUNT_SYMBOL.matcher(text);
        if (amount.find()) {
            String tail = text.substring(amount.end()).trim();
            Matcher cn = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,20})").matcher(tail);
            if (cn.find()) {
                return sanitizeMerchant(cn.group(1));
            }
        }
        return "";
    }

    static String sanitizeMerchant(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        cleaned = cleaned.replaceAll("[,，。;；:：\\s]+$", "");
        if (cleaned.length() > 24) {
            cleaned = cleaned.substring(0, 24);
        }
        return cleaned;
    }

    static boolean containsAny(String text, String[] words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
