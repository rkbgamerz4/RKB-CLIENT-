package com.rkb.client.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RkbNavigationTest {
    @Test
    void navigationOrderIsStable() {
        assertArrayEquals(new String[]{"MAIN MENU", "SETTINGS", "PROFILES", "HUD EDITOR", "WAYPOINTS",
                "COSMETICS", "ABOUT"}, RkbUi.NAV_LABELS);
    }

    @Test
    void menuPageConstantsMatchTheirNavigationIndices() {
        assertEquals(0, RkbMenuScreen.PAGE_MODS);
        assertEquals(1, RkbMenuScreen.PAGE_SETTINGS);
        assertEquals(2, RkbMenuScreen.PAGE_PROFILES);
        assertEquals(6, RkbMenuScreen.PAGE_ABOUT);
        assertTrue(RkbMenuScreen.PAGE_ABOUT < RkbUi.NAV_LABELS.length);
    }

    @Test
    void everyTopBarNavigationItemIsClickableAtCommonLandscapeSizes() {
        for (int width : new int[]{360, 640, 854, 1280}) {
            RkbLayout.TopBar bar = RkbLayout.topBar(width, 480);
            for (RkbUi.Rect item : bar.navItems()) {
                assertTrue(item.width() >= 24);
                assertTrue(item.height() == RkbUi.NAV_H);
            }
        }
    }
}
