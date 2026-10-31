package com.rkb.client;

import net.fabricmc.loader.api.FabricLoader;

/** Version strings read from Fabric Loader metadata (no Minecraft internals needed). */
public final class VersionInfo {
    private static String minecraft;
    private static String mod;
    private static String loader;

    private VersionInfo() {
    }

    public static String minecraft() {
        if (minecraft == null) {
            minecraft = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        }
        return minecraft;
    }

    public static String loader() {
        if (loader == null) {
            loader = FabricLoader.getInstance().getModContainer("fabricloader")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        }
        return loader;
    }

    public static String mod() {
        if (mod == null) {
            mod = FabricLoader.getInstance().getModContainer(RkbClient.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
        }
        return mod;
    }
}
