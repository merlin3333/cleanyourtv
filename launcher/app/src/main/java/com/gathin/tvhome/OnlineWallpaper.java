package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * One random photo from Picsum while Home is visible. The request shape follows
 * audiedv/cleanyourtv branch ambient-theme (MIT), WallpaperPlayer there: HTTPS only,
 * a size cap, and the local collection when the network does not answer.
 * No API key. Picsum serves Unsplash photographs; the on-screen credit is {@link #CREDIT}.
 */
final class OnlineWallpaper {
    static final String ENDPOINT = "https://picsum.photos/1920/1080";
    /** Source line. Not a place name from the local captions file. */
    static final String CREDIT = "Picsum Photos";
    static final int MAX_BYTES = 8_000_000;
    static final int MAX_REDIRECTS = 4;
    static final int CONNECT_MS = 8_000;
    static final int READ_MS = 12_000;
    static final int MIN_MINUTES = 5;
    static final int MAX_MINUTES = 240;
    static final int DEFAULT_MINUTES = 5;

    private OnlineWallpaper() {}

    /** {@code picsum} only when the theme says that. Every other value is Bing, including a missing key. */
    static String source(String value) {
        return "picsum".equals(value) ? "picsum" : "bing";
    }

    /** Minutes between fetches. Out of range, or a missing number, stays at {@link #DEFAULT_MINUTES}. */
    static int minutes(int value) {
        return value < MIN_MINUTES || value > MAX_MINUTES ? DEFAULT_MINUTES : value;
    }

    static String requestUrl(long nowMillis) {
        return ENDPOINT + "?random=" + nowMillis;
    }

    static final class Fetch {
        final byte[] body;
        final String error;

        private Fetch(byte[] body, String error) {
            this.body = body;
            this.error = error;
        }

        boolean ok() {
            return body != null;
        }

        /** A failed fetch keeps the theme's own pictures on screen. */
        boolean useLocalCollection() {
            return !ok();
        }
    }

    /** One HTTP response. {@code location} is set for a redirect. {@code length} is -1 when undeclared. */
    interface Client {
        Answer request(String url) throws IOException;
    }

    static final class Answer {
        final int code;
        final String location;
        final long length;
        final byte[] body;

        Answer(int code, String location, long length, byte[] body) {
            this.code = code;
            this.location = location;
            this.length = length;
            this.body = body;
        }
    }

    static Fetch retrieve(Client client, long nowMillis) {
        return retrieveUrl(client, requestUrl(nowMillis));
    }

    static Fetch retrieveUrl(Client client, String url) {
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            String rejection = reject(url);
            if (rejection != null) return fail(rejection);
            Answer answer;
            try {
                answer = client.request(url);
            } catch (IOException failure) {
                return fail("network");
            }
            if (answer == null) return fail("network");
            if (answer.length > MAX_BYTES) return fail("oversize");
            if (isRedirect(answer.code)) {
                if (hop == MAX_REDIRECTS) return fail("redirects");
                url = resolve(url, answer.location);
                if (url == null) return fail("redirect");
                continue;
            }
            if (answer.code != 200 || answer.body == null || answer.body.length == 0) return fail("status");
            if (answer.body.length > MAX_BYTES) return fail("oversize");
            return new Fetch(answer.body, null);
        }
        return fail("redirects");
    }

    /** HTTPS only, no userinfo, and the host must be present. */
    static String reject(String url) {
        if (url == null) return "url";
        URL parsed;
        try {
            parsed = new URL(url);
        } catch (MalformedURLException failure) {
            return "url";
        }
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) return "not-https";
        if (parsed.getUserInfo() != null) return "userinfo";
        if (parsed.getHost() == null || parsed.getHost().isEmpty()) return "url";
        return null;
    }

    static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    /** Absolute or relative Location, or null when it is not a URL. */
    static String resolve(String current, String location) {
        if (location == null || location.isEmpty()) return null;
        try {
            return new URL(new URL(current), location).toString();
        } catch (MalformedURLException failure) {
            return null;
        }
    }

    /** The device client. Redirects are not followed here; {@link #retrieve} counts them. */
    static Client httpClient() {
        return url -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_MS);
            connection.setReadTimeout(READ_MS);
            connection.setRequestProperty("Accept", "image/jpeg,image/*;q=0.8");
            try {
                int code = connection.getResponseCode();
                String location = connection.getHeaderField("Location");
                long length = connection.getContentLengthLong();
                byte[] body = null;
                if (code == 200 && length <= MAX_BYTES) {
                    body = readCapped(connection.getInputStream());
                }
                return new Answer(code, location, length, body);
            } finally {
                connection.disconnect();
            }
        };
    }

    static byte[] readCapped(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) throw new IOException("oversize");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static Fetch fail(String error) {
        return new Fetch(null, error);
    }
}
