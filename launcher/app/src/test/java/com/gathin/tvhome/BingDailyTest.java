package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BingDailyTest {
    private static final String COPYRIGHT =
            "A bison in the grasslands of Theodore Roosevelt National Park, North Dakota, USA (© Tandem Stock/Adobe Stock)";
    private static final String FIXTURE = "{\"images\":[{\"startdate\":\"20261106\","
            + "\"urlbase\":\"/th?id=OHR.DakotaBison_EN-IN0635434875\","
            + "\"copyright\":\"" + COPYRIGHT + "\"}]}";

    @Test
    public void fixtureCaptionAndImageUrlAreExact() {
        String[] photo = BingDaily.parse(FIXTURE);
        assertNotNull(photo);
        assertEquals("20261106", photo[0]);
        assertEquals(COPYRIGHT, photo[1]);
        assertEquals("https://www.bing.com/th?id=OHR.DakotaBison_EN-IN0635434875_1920x1080.jpg", photo[2]);
        assertFalse(photo[1].contains("Alps"));
        assertFalse(photo[1].contains("Switzerland"));
    }

    @Test
    public void invalidJsonAndUnsafePathsAreRejected() {
        assertNull(BingDaily.parse(""));
        assertNull(BingDaily.parse("not json"));
        assertNull(BingDaily.parse("{\"images\":[]}"));
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"20261106\",\"urlbase\":\"/th?id=OHR.X\","
                + "\"copyright\":\"\"}]}"));
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"20261106\","
                + "\"urlbase\":\"https://evil.example/th?id=OHR.X\",\"copyright\":\"Place\"}]}"));
        assertNull(BingDaily.parse("{\"images\":[{\"startdate\":\"20261106\","
                + "\"urlbase\":\"/th?id=../../etc/passwd\",\"copyright\":\"Place\"}]}"));
        assertFalse(BingDaily.safeBase("/th?id=OHR.X://y"));
    }

    @Test
    public void brokenMetaDoesNotInventACaption() {
        assertNull(BingDaily.cached(null));
        assertNull(BingDaily.cached("{"));
        assertNull(BingDaily.cached("v1\n20261106\n20261107\n0\n"));
        String[] saved = BingDaily.cached(
                BingDaily.meta("20261106", COPYRIGHT, "20261107", 0));
        assertNotNull(saved);
        assertEquals(COPYRIGHT, saved[1]);
        assertEquals("20261107", BingDaily.checkedDay(
                BingDaily.meta("20261106", COPYRIGHT, "20261107", 0)));
    }

    @Test
    public void sameLocalDayDoesNotFetchAgainAndAFailureWaits() {
        long now = 1_700_000_000_000L;
        assertFalse(BingDaily.shouldFetch("20261107", "20261107", 0, now));
        assertTrue(BingDaily.shouldFetch("", "20261107", 0, now));
        assertFalse(BingDaily.shouldFetch("", "20261107", now - 60_000L, now));
        assertTrue(BingDaily.shouldFetch("", "20261107", now - BingDaily.FAILURE_BACKOFF_MS, now));
    }

    @Test
    public void picsumIsOnlyTheExplicitSource() {
        assertEquals("bing", OnlineWallpaper.source(null));
        assertEquals("bing", OnlineWallpaper.source(""));
        assertEquals("bing", OnlineWallpaper.source("bing"));
        assertEquals("picsum", OnlineWallpaper.source("picsum"));
    }

    @Test
    public void liveIndiaFeedHasACopyrightAndABoundedJpeg() throws Exception {
        OnlineWallpaper.Fetch archive = OnlineWallpaper.retrieveUrl(
                OnlineWallpaper.httpClient(), BingDaily.ARCHIVE);
        assertTrue(archive.ok());
        String json = new String(archive.body, java.nio.charset.StandardCharsets.UTF_8);
        String[] photo = BingDaily.parse(json);
        assertNotNull(photo);
        assertEquals(BingDaily.field(json, "copyright").trim(), photo[1]);
        assertTrue(json.contains(photo[1]));
        assertTrue(photo[2].startsWith("https://www.bing.com/th?id="));
        assertTrue(photo[2].endsWith("_1920x1080.jpg"));
        OnlineWallpaper.Fetch image = OnlineWallpaper.retrieveUrl(OnlineWallpaper.httpClient(), photo[2]);
        assertTrue(image.ok());
        assertTrue(image.body.length > 3);
        assertTrue(image.body.length <= OnlineWallpaper.MAX_BYTES);
        assertEquals((byte) 0xff, image.body[0]);
        assertEquals((byte) 0xd8, image.body[1]);
        assertEquals((byte) 0xff, image.body[2]);
    }
}
