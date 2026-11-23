package com.rkb.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RkbLayoutTest {
    private static final int[][] SIZES = {
            {360, 240}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}
    };

    @Test
    void mainMenuAlwaysHasSixVisibleDestinations() {
        for (int[] size : SIZES) {
            int w = size[0];
            int h = size[1];
            RkbLayout.MainMenu menu = RkbLayout.mainMenu(w, h);
            assertEquals(6, menu.buttons().size(), "destinations at " + w + "x" + h);
            assertInside(w, h, menu.brand());
            assertInside(w, h, menu.account());
            for (RkbUi.Rect button : menu.buttons()) {
                assertInside(w, h, button);
                assertTrue(button.height() >= 22, "tap target at " + w + "x" + h);
            }
        }
    }

    @Test
    void mainMenuButtonsDoNotOverlapEachOtherOrTheAccountCard() {
        for (int[] size : SIZES) {
            int w = size[0];
            int h = size[1];
            RkbLayout.MainMenu menu = RkbLayout.mainMenu(w, h);
            for (int i = 0; i < menu.buttons().size(); i++) {
                for (int j = i + 1; j < menu.buttons().size(); j++) {
                    assertFalse(overlaps(menu.buttons().get(i), menu.buttons().get(j)),
                            "buttons " + i + "/" + j + " at " + w + "x" + h);
                }
            }
            for (RkbUi.Rect button : menu.buttons()) {
                assertFalse(overlaps(button, menu.account()), "button/account overlap at " + w + "x" + h);
            }
        }
    }

    @Test
    void compactLandscapeUsesTwoColumns() {
        RkbLayout.MainMenu menu = RkbLayout.mainMenu(360, 240);
        assertTrue(menu.compact());
        assertEquals(menu.buttons().get(0).y(), menu.buttons().get(1).y());
        assertTrue(menu.buttons().get(1).x() > menu.buttons().get(0).x());
        assertTrue(menu.buttons().get(2).y() > menu.buttons().get(0).y());
    }

    @Test
    void topBarHasOneNavigationItemPerLabel() {
        for (int[] size : SIZES) {
            int w = size[0];
            int h = size[1];
            RkbLayout.TopBar bar = RkbLayout.topBar(w, h);
            assertEquals(RkbUi.NAV_LABELS.length, bar.navItems().size());
            assertTrue(bar.contentTop() > bar.nav().bottom());
            for (RkbUi.Rect item : bar.navItems()) {
                assertTrue(item.width() >= 24, "nav item width at " + w + "x" + h);
                assertTrue(item.height() == RkbUi.NAV_H);
                assertInside(w, h, item);
            }
            assertInside(w, h, bar.brand());
            assertInside(w, h, bar.account());
            assertInside(w, h, bar.close());
        }
    }

    private static boolean overlaps(RkbUi.Rect a, RkbUi.Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static void assertInside(int w, int h, RkbUi.Rect r) {
        assertTrue(r.x() >= 0, "x >= 0");
        assertTrue(r.y() >= 0, "y >= 0");
        assertTrue(r.right() <= w, "right within screen");
        assertTrue(r.bottom() <= h, "bottom within screen");
        assertTrue(r.width() > 0 && r.height() > 0, "positive size");
    }
}
