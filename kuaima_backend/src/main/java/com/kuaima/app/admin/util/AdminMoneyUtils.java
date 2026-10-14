package com.kuaima.app.admin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 后台金额单位转换工具。
 * <p>项目内金额存储约定不统一：payroll_order / payroll_detail / boss_merchant_account 以「分」（BIGINT）存储，
 * boss_balance_recharge_order 以「元」（DECIMAL 18,2）存储。本工具只做两种单位之间的换算，
 * 接口出参统一同时给出 {@code xxxFen}（分）与 {@code xxxYuan}（元，字符串保留两位小数）。
 */
public final class AdminMoneyUtils {

    private static final BigDecimal FEN_PER_YUAN = new BigDecimal("100");

    private AdminMoneyUtils() {
    }

    /** 分 → 元字符串（两位小数，null 视为 0）。 */
    public static String fenToYuan(Long fen) {
        if (fen == null) {
            return "0.00";
        }
        return new BigDecimal(fen).divide(FEN_PER_YUAN, 2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 元 → 分（四舍五入，null 视为 0）。 */
    public static long yuanToFen(BigDecimal yuan) {
        if (yuan == null) {
            return 0L;
        }
        return yuan.multiply(FEN_PER_YUAN).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** 分 → 元 BigDecimal（两位小数）。 */
    public static BigDecimal fenToYuanDecimal(Long fen) {
        if (fen == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(fen).divide(FEN_PER_YUAN, 2, RoundingMode.HALF_UP);
    }
}
