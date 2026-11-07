package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class OnlineWallpaperTest {

    private static OnlineWallpaper.Answer ok(byte[] body) {
        return new OnlineWallpaper.Answer(200, null, body.length, body);
    }

    @Test
    public void minutesStayAtFiveOutsideTheRange() {
        assertEquals(5, OnlineWallpaper.minutes(5));
        assertEquals(15, OnlineWallpaper.minutes(15));
        assertEquals(240, OnlineWallpaper.minutes(240));
        assertEquals(OnlineWallpaper.DEFAULT_MINUTES, OnlineWallpaper.minutes(4));
        assertEquals(OnlineWallpaper.DEFAULT_MINUTES, OnlineWallpaper.minutes(241));
        assertEquals(OnlineWallpaper.DEFAULT_MINUTES, OnlineWallpaper.minutes(0));
    }

    @Test
    public void requestIsHttpsPicsumAt1080p() {
        String url = OnlineWallpaper.requestUrl(10L);
        assertTrue(url.startsWith("https://picsum.photos/1920/1080"));
        assertNull(OnlineWallpaper.reject(url));
        assertEquals("not-https", OnlineWallpaper.reject("http://picsum.photos/1920/1080"));
        assertEquals("userinfo", OnlineWallpaper.reject("https://user:pass@picsum.photos/1920/1080"));
    }

    @Test
    public void successReturnsTheBody() {
        byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
        OnlineWallpaper.Fetch fetch = OnlineWallpaper.retrieve(url -> ok(jpeg), 1L);
        assertTrue(fetch.ok());
        assertFalse(fetch.useLocalCollection());
        assertEquals(4, fetch.body.length);
    }

    @Test
    public void networkFailureUsesTheLocalCollection() {
        OnlineWallpaper.Fetch fetch = OnlineWallpaper.retrieve(url -> {
            throw new IOException("offline");
        }, 1L);
        assertFalse(fetch.ok());
        assertTrue(fetch.useLocalCollection());
        assertEquals("network", fetch.error);
    }

    @Test
    public void httpErrorUsesTheLocalCollection() {
        OnlineWallpaper.Fetch fetch = OnlineWallpaper.retrieve(
                url -> new OnlineWallpaper.Answer(503, null, -1, null), 1L);
        assertTrue(fetch.useLocalCollection());
        assertEquals("status", fetch.error);
    }

    @Test
    public void declaredOversizeIsRejectedBeforeTheBody() {
        OnlineWallpaper.Fetch fetch = OnlineWallpaper.retrieve(
                url -> new OnlineWallpaper.Answer(200, null, OnlineWallpaper.MAX_BYTES + 1, new byte[] {1}), 1L);
        assertTrue(fetch.useLocalCollection());
        assertEquals("oversize", fetch.error);
    }

    @Test
    public void bodyOversizeIsRejected() {
        byte[] huge = new byte[OnlineWallpaper.MAX_BYTES + 1];
        OnlineWallpaper.Fetch fetch = OnlineWallpaper.retrieve(url -> ok(huge), 1L);
        assertEquals("oversize", fetch.error);
    }

    @Test
    public void httpsRedirectIsFollowedAndHttpRedirectIsNot() {
        AtomicInteger hops = new AtomicInteger();
        OnlineWallpaper.Fetch ok = OnlineWallpaper.retrieve(url -> {
            if (hops.getAndIncrement() == 0) {
                return new OnlineWallpaper.Answer(302, "https://images.example/photo.jpg", -1, null);
            }
            assertTrue(url.startsWith("https://images.example/photo.jpg"));
            return ok(new byte[] {1, 2, 3});
        }, 1L);
        assertTrue(ok.ok());

        OnlineWallpaper.Fetch blocked = OnlineWallpaper.retrieve(
                url -> new OnlineWallpaper.Answer(302, "http://images.example/photo.jpg", -1, null), 1L);
        assertEquals("not-https", blocked.error);
        assertTrue(blocked.useLocalCollection());
    }

    @Test
    public void tooManyRedirectsUsesTheLocalCollection() {
        OnlineWallpaper.Fetch fetch = OnlineWallpaper.retrieve(
                url -> new OnlineWallpaper.Answer(302, url + "/next", -1, null), 1L);
        assertEquals("redirects", fetch.error);
        assertTrue(fetch.useLocalCollection());
    }

    @Test
    public void creditIsTheSourceNotALocalPlaceName() {
        assertEquals("Picsum Photos", OnlineWallpaper.CREDIT);
        assertFalse(OnlineWallpaper.CREDIT.toLowerCase().contains("alps"));
        assertFalse(OnlineWallpaper.CREDIT.toLowerCase().contains("switzerland"));
        assertNotNull(OnlineWallpaper.CREDIT);
    }

    @Test
    public void relativeRedirectStaysOnHttps() {
        String next = OnlineWallpaper.resolve(
                "https://picsum.photos/1920/1080?random=1", "/id/1/1920/1080.jpg");
        assertTrue(next.startsWith("https://picsum.photos/id/1/1920/1080.jpg"));
        assertNull(OnlineWallpaper.reject(next));
    }
}
