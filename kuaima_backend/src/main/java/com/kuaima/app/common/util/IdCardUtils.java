package com.kuaima.app.common.util;

import java.time.LocalDate;
import java.time.Period;

/** 身份证号解析工具：从 18/15 位身份证号推导性别与年龄。 */
public final class IdCardUtils {

    private IdCardUtils() {
    }

    /** 返回 male/female，无法判定返回 null。 */
    public static String gender(String idCard) {
        if (idCard == null || idCard.length() < 17) {
            return null;
        }
        char c = idCard.charAt(16);
        if (!Character.isDigit(c)) {
            return null;
        }
        return ((c - '0') % 2 == 1) ? "male" : "female";
    }

    /** 返回周岁年龄，非法或超出 [16,100] 返回 null。 */
    public static Integer age(String idCard) {
        if (idCard == null || idCard.length() < 14) {
            return null;
        }
        try {
            int year = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day = Integer.parseInt(idCard.substring(12, 14));
            if (year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            int age = Period.between(LocalDate.of(year, month, day), LocalDate.now()).getYears();
            return (age >= 16 && age <= 100) ? age : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 身份证脱敏：保留前 6 后 4，中间打码。 */
    public static String mask(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}
