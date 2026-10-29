package com.abzed.template.common;

/** Masks contact details returned pre-auth (never expose the full address). */
public final class MaskingUtil {

    private MaskingUtil() {
    }

    /** {@code jane@example.com} -> {@code ja****@example.com}. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "****";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "****";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, Math.min(local.length(), 1)) : local.substring(0, 2);
        return visible + "****" + domain;
    }
}
