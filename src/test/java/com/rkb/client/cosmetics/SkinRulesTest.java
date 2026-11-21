package com.rkb.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class SkinRulesTest {
    /** A minimal PNG header: only the IHDR fields that the validator reads are meaningful. */
    private static byte[] png(int width, int height) {
        byte[] data = new byte[33];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(sig, 0, data, 0, sig.length);
        data[11] = 13;
        data[12] = 'I';
        data[13] = 'H';
        data[14] = 'D';
        data[15] = 'R';
        data[19] = (byte) width;
        data[23] = (byte) height;
        return data;
    }

    @Test
    void readsPngDimensionsWithoutDecoding() {
        assertArrayEquals(new int[]{64, 64}, SkinRules.pngSize(png(64, 64)));
        assertArrayEquals(new int[]{64, 32}, SkinRules.pngSize(png(64, 32)));
        assertNull(SkinRules.pngSize(new byte[]{1, 2, 3}));
        byte[] notPng = png(64, 64);
        notPng[1] = 'X';
        assertNull(SkinRules.pngSize(notPng));
    }

    @Test
    void acceptsOnlyStandardMinecraftSkinDimensions() {
        assertTrue(SkinRules.validDimensions(64, 64));
        assertTrue(SkinRules.validDimensions(64, 32));
        assertFalse(SkinRules.validDimensions(128, 128));
        assertEquals("", SkinRules.validatePng(png(64, 64)));
        assertEquals("", SkinRules.validatePng(png(64, 32)));
        assertTrue(SkinRules.validatePng(png(128, 128)).contains("64x64 or 64x32"));
        assertTrue(SkinRules.validatePng(null).contains("empty"));
        assertTrue(SkinRules.validatePng(new byte[300 * 1024]).contains("larger"));
    }

    @Test
    void validatesSetNamesModelsAndIds() {
        assertTrue(SkinRules.validName("My Skin #1".replace("#", "")));
        assertTrue(SkinRules.validName("Дарк Скин"));
        assertFalse(SkinRules.validName(""));
        assertFalse(SkinRules.validName("a".repeat(33)));
        assertFalse(SkinRules.validName("bad/name"));
        assertEquals("  Nether Skin  ".trim(), SkinRules.cleanName("  Nether Skin  "));

        assertTrue(SkinRules.validModel(SkinRules.MODEL_CLASSIC));
        assertTrue(SkinRules.validModel(SkinRules.MODEL_SLIM));
        assertFalse(SkinRules.validModel("alex"));
        assertEquals(SkinRules.MODEL_SLIM, SkinRules.modelFromLabel("Slim"));
        assertEquals("Slim (Alex)", SkinRules.label(SkinRules.MODEL_SLIM));

        assertTrue(SkinRules.validUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertFalse(SkinRules.validUuid("069a79f444e94726a5befca90e38aaf5"));
        assertFalse(SkinRules.validUuid(null));
        assertTrue(SkinRules.validSetId("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertFalse(SkinRules.validSetId("not-a-uuid"));
    }

    @Test
    void shipsExactlyTheTenOfficialCapes() {
        List<CapeCollection.Design> all = CapeCollection.all();
        assertEquals(10, all.size());
        assertEquals(List.of("cape_bangladesh", "cape_india", "cape_usa", "cape_pakistan", "cape_brazil",
                "cape_japan", "cape_uk", "cape_nepal", "cape_rkb_original", "cape_rkb_shadow"),
                all.stream().map(CapeCollection.Design::id).toList());

        for (CapeCollection.Design d : all) {
            byte[] bundled = CapeCollection.bundledBytes(d.id());
            assertNotNull(bundled, d.id() + " must be bundled in the mod jar");
            int[] size = SkinRules.pngSize(bundled);
            assertNotNull(size, d.id() + " must be a PNG");
            assertArrayEquals(new int[]{64, 32}, size, d.id() + " must be a standard 64x32 cape");
            String sha = CapeCollection.bundledSha(d.id());
            assertNotNull(sha);
            assertTrue(CapeCollection.matchesBundled(d.id(), sha));
            assertFalse(CapeCollection.matchesBundled(d.id(), "0".repeat(64)));
        }
    }
}
