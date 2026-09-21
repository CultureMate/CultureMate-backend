package com.team.cultureevents.features.events.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 행사 식별키 (명세 6번).
 * 1) 문화포털 상세 URL이 있으면 그 문자열
 * 2) 없거나 중복되면 title|startDate|place
 * DB의 event_id(512자)를 넘는 예외적인 값은 같은 원문에서 만든 SHA-256 키로 축약한다.
 */
public final class EventIdGenerator {

    private static final int MAX_ID_LENGTH = 512;

    private EventIdGenerator() {
    }

    public static String from(String portalUrl, String title, String startDate, String place) {
        if (portalUrl != null && !portalUrl.isBlank()) {
            return fit(portalUrl.trim());
        }
        return fallback(title, startDate, place);
    }

    public static String fallback(String title, String startDate, String place) {
        String t = nullToEmpty(title);
        String s = nullToEmpty(startDate);
        String p = nullToEmpty(place);
        return fit(t + "|" + s + "|" + p);
    }

    public static String sha256Short(String eventId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(eventId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fit(String raw) {
        if (raw.length() <= MAX_ID_LENGTH) {
            return raw;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
