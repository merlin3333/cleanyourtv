package com.gathin.tvhome;

/**
 * Today's Bing homepage photo. The archive URL and the landscape file name follow
 * devmil/muzei-bingimageoftheday (Apache-2.0, Copyright 2014 Devmil Solutions):
 * {@code /HPImageArchive.aspx?format=js&idx=0} and {@code urlbase + "_1920x1080.jpg"}.
 * The caption is Bing's {@code copyright} field, shown unchanged.
 * Market {@code en-IN} is an official code in that client's market list and the feed answered it.
 */
final class BingDaily {
    static final String MARKET = "en-IN";
    static final String ARCHIVE =
            "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=" + MARKET;
    static final String HOST = "https://www.bing.com";
    static final String IMAGE_NAME = "bing-daily.jpg";
    static final String META_NAME = "bing-daily.json";
    /** After a failed check, wait this long before another one. A new local day retries sooner. */
    static final long FAILURE_BACKOFF_MS = 6L * 60L * 60L * 1000L;

    private BingDaily() {}

    /** {@code null} when the archive is not the public one-image document. Otherwise start, copyright, image URL. */
    static String[] parse(String json) {
        if (json == null || !json.contains("\"images\"")) return null;
        String start = field(json, "startdate");
        String copyright = field(json, "copyright");
        String base = field(json, "urlbase");
        if (start == null || copyright == null || base == null) return null;
        start = start.trim();
        copyright = copyright.trim();
        base = base.trim();
        if (!start.matches("[0-9]{8}") || copyright.isEmpty() || !safeBase(base)) return null;
        String url = HOST + base + "_1920x1080.jpg";
        if (OnlineWallpaper.reject(url) != null) return null;
        return new String[] {start, copyright, url};
    }

    /** The first JSON string value for {@code key}. Escapes are undone. Not a general parser. */
    static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int q = json.indexOf('"', json.indexOf(':', i) + 1);
        if (q < 0) return null;
        StringBuilder out = new StringBuilder();
        for (int p = q + 1; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '"') return out.toString();
            if (c == '\\' && p + 1 < json.length()) c = json.charAt(++p);
            out.append(c);
        }
        return null;
    }

    /** A path on www.bing.com, not an absolute URL and not a traversal. */
    static boolean safeBase(String base) {
        if (base == null || !base.startsWith("/th?")) return false;
        if (base.contains("..") || base.contains("\\") || base.contains("://") || base.contains("//"))
            return false;
        return base.indexOf('?') == 3;
    }

    /**
     * One archive check per local day while Home is open. A failure waits {@link #FAILURE_BACKOFF_MS}.
     * {@code checkedDay} and {@code today} are {@code yyyyMMdd}.
     */
    static boolean shouldFetch(String checkedDay, String today, long failedAtMillis, long nowMillis) {
        if (today == null || !today.matches("[0-9]{8}")) return false;
        if (today.equals(checkedDay)) return false;
        return failedAtMillis <= 0 || nowMillis - failedAtMillis >= FAILURE_BACKOFF_MS;
    }

    /** Four lines: version, start date, checked day, failure time, then the copyright text. */
    static String meta(String startDate, String copyright, String checkedDay, long failedAtMillis) {
        return "v1\n"
                + (startDate == null ? "" : startDate.replace("\n", " ")) + "\n"
                + (checkedDay == null ? "" : checkedDay.replace("\n", " ")) + "\n"
                + failedAtMillis + "\n"
                + (copyright == null ? "" : copyright);
    }

    /** Saved daily photo, or null when the metadata is not usable. The image file is checked by the caller. */
    static String[] cached(String metaText) {
        String[] lines = lines(metaText);
        if (lines == null) return null;
        if (!lines[1].matches("[0-9]{8}") || lines[4].trim().isEmpty()) return null;
        return new String[] {lines[1], lines[4], ""};
    }

    static String checkedDay(String metaText) {
        String[] lines = lines(metaText);
        return lines == null ? "" : lines[2];
    }

    static long failedAt(String metaText) {
        String[] lines = lines(metaText);
        if (lines == null) return 0;
        try {
            return Long.parseLong(lines[3]);
        } catch (NumberFormatException failure) {
            return 0;
        }
    }

    private static String[] lines(String metaText) {
        if (metaText == null || !metaText.startsWith("v1\n")) return null;
        String[] parts = metaText.split("\n", 5);
        if (parts.length < 5 || !"v1".equals(parts[0])) return null;
        return parts;
    }
}
