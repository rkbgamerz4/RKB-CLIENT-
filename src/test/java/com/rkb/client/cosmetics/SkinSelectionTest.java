package com.rkb.client.cosmetics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkinSelectionTest {
    @Test
    void defaultSelectionUsesVanillaMinecraftSkin() {
        SkinSelection selection = SkinSelection.defaultSkin();
        assertFalse(selection.custom());
        assertTrue(selection.usesMinecraftDefault());
        assertEquals("Minecraft default", selection.name());
        assertEquals("", selection.sha());
    }

    @Test
    void customSelectionCarriesSkinMetadata() {
        SkinSelection selection = SkinSelection.ofCustom("My Skin", "abc123", SkinRules.MODEL_SLIM);
        assertTrue(selection.custom());
        assertFalse(selection.usesMinecraftDefault());
        assertEquals("My Skin", selection.name());
        assertEquals("abc123", selection.sha());
        assertEquals(SkinRules.MODEL_SLIM, selection.model());
    }

    @Test
    void resetReturnsDefaultSelection() {
        SkinSelection afterReset = SkinSelection.afterReset();
        assertTrue(afterReset.usesMinecraftDefault());
        assertFalse(afterReset.custom());
        assertEquals(SkinSelection.defaultSkin(), afterReset);
    }

    @Test
    void invalidCustomSelectionFallsBackToDefault() {
        assertTrue(SkinSelection.ofCustom("Bad", "", SkinRules.MODEL_CLASSIC).usesMinecraftDefault());
        assertTrue(SkinSelection.ofCustom("Bad", null, SkinRules.MODEL_CLASSIC).usesMinecraftDefault());
    }
}
