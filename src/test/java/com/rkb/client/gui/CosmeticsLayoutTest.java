package com.rkb.client.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CosmeticsLayoutTest {
    @Test
    void threeTabsAreStable() {
        assertArrayEquals(new String[]{"Skin", "Cape", "Redeem"}, CosmeticsLayout.TABS);
        CosmeticsLayout.Layout layout = CosmeticsLayout.of(854, 480);
        assertEquals(3, layout.tabRects().size());
        for (CosmeticsLayout.Rect r : layout.tabRects()) {
            assertTrue(r.width() > 40);
            assertTrue(r.height() >= 28);
        }
    }

    @Test
    void landscapeLayoutKeepsPreviewAndListInsidePanel() {
        CosmeticsLayout.Layout layout = CosmeticsLayout.of(854, 480);
        assertFalse(layout.compact());
        assertInside(layout.panel(), layout.preview());
        assertInside(layout.panel(), layout.list());
        assertFalse(layout.preview().overlaps(layout.list()));
        assertTrue(layout.list().x() >= layout.preview().right());
        assertTrue(layout.preview().height() > 120);
    }

    @Test
    void compactLayoutStacksWithoutOverlap() {
        CosmeticsLayout.Layout layout = CosmeticsLayout.of(360, 640);
        assertTrue(layout.compact());
        assertInside(layout.panel(), layout.preview());
        assertInside(layout.panel(), layout.list());
        assertFalse(layout.preview().overlaps(layout.list()));
        assertTrue(layout.list().y() >= layout.preview().bottom());
    }

    @Test
    void smallLandscapeHasNoOverlapAndSafeMargin() {
        CosmeticsLayout.Layout layout = CosmeticsLayout.of(640, 360);
        CosmeticsLayout.Rect panel = layout.panel();
        assertTrue(panel.x() >= 6);
        assertTrue(panel.y() >= 6);
        assertTrue(panel.right() <= 640 - 6);
        assertTrue(panel.bottom() <= 360 - 6);
        assertFalse(layout.preview().overlaps(layout.list()));
        for (CosmeticsLayout.Rect tab : layout.tabRects()) {
            assertInside(panel, tab);
        }
    }

    private static void assertInside(CosmeticsLayout.Rect outer, CosmeticsLayout.Rect inner) {
        assertTrue(inner.x() >= outer.x());
        assertTrue(inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right());
        assertTrue(inner.bottom() <= outer.bottom());
    }
}
