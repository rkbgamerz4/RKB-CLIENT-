package com.rkb.client.module.impl;

import com.rkb.client.cloud.BadgeRegistry;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.cosmetics.RemoteCosmetics;
import com.rkb.client.gui.FriendsScreen;
import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.BoolSetting;
import com.rkb.client.module.setting.ChoiceSetting;
import com.rkb.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * RKB logo (or a bold R) in front of other players' name tags. OFF by default; while it is off nothing is drawn
 * and nothing is sent anywhere.
 *
 * The logo is a font glyph shipped in the mod (assets/minecraft/font/default.json), so it costs no extra rendering.
 *  - Friends: only players in your saved Friends list (by UUID).
 *  - Everyone: your friends plus players the cloud lists as RKB users with their badge on (needs Cloud Sync).
 * Your own name tag is never touched. Per-frame work is one hash lookup and one small Text object.
 */
public class NameTagBadgeModule extends Module {
    /** Private-use character mapped to the logo in assets/minecraft/font/default.json. */
    public static final String ICON = "\uE7A1";

    private static final int[] COLORS = {0x00A8FF, 0x1B60FF, 0xFFFFFF};
    private static NameTagBadgeModule instance;
    private static Text iconPrefix;
    private static final Text[] LETTER_PREFIX = new Text[COLORS.length];

    private final ChoiceSetting style = add(
            new ChoiceSetting("style", "Badge style", List.of("Logo", "Letter R"), "Logo"), () -> isEnabled());
    private final ChoiceSetting color = add(
            new ChoiceSetting("color", "Letter color", List.of("Cyan", "Blue", "White"), "Cyan"),
            () -> isEnabled() && style.index() == 1);
    private final ChoiceSetting mode = add(
            new ChoiceSetting("mode", "Visibility", List.of("Friends", "Everyone"), "Friends"), () -> isEnabled());
    private final BoolSetting cloudSync = add(new BoolSetting("cloud_sync", "Cloud sync", false), () -> isEnabled());

    public NameTagBadgeModule() {
        super("nametag_badge", "Name Tag Badge",
                "RKB logo before name tags. Off by default. Friends list or everyone; optional cloud sync.", Category.VISUALS);
        instance = this;
        addAction("manage_friends", "Manage friends", mc -> mc.setScreen(new FriendsScreen()));
        addAction("sync_now", "Sync now", mc -> CloudService.manualSync());
    }

    public static NameTagBadgeModule get() {
        return instance;
    }

    public boolean cloudSyncOn() {
        return cloudSync.on();
    }

    public boolean everyoneMode() {
        return mode.index() == 1;
    }

    public String styleKey() {
        return style.index() == 1 ? "letter" : "logo";
    }

    public String visibilityKey() {
        return everyoneMode() ? "everyone" : "friends";
    }

    /** Settings that arrive from the cloud. The master toggle is deliberately NOT changed from the cloud. */
    public void applyFromCloud(String styleKey, String visibilityKey) {
        style.select("letter".equals(styleKey) ? "Letter R" : "Logo");
        mode.select("everyone".equals(visibilityKey) ? "Everyone" : "Friends");
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onEnable() {
        CloudService.onBadgeFeatureChanged();
    }

    @Override
    protected void onDisable() {
        CloudService.onBadgeFeatureChanged();
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        CloudService.onSettingsEdited();
    }

    // ------------------------------------------------------------------ rendering hooks (mixin)

    /** Called from the PlayerEntity mixin for every player display name. Must stay cheap. */
    public static boolean applies(Object entity) {
        NameTagBadgeModule m = instance;
        if (m == null || !m.isEnabled() || !(entity instanceof PlayerEntity player)) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || player == mc.player) {
            return false;
        }
        UUID id = player.getUuid();
        if (!RemoteCosmetics.isRkbClient(id)) {
            return false;   // a name prefix is never drawn for an unverified identity
        }
        if (FriendsManager.isFriend(id)) {
            return true;
        }
        return m.everyoneMode() && BadgeRegistry.isEligible(id);
    }

    public static Text decorate(Text original) {
        NameTagBadgeModule m = instance;
        Text prefix;
        if (m == null || m.style.index() == 0) {
            if (iconPrefix == null) {
                iconPrefix = Text.literal(ICON + " ").styled(s -> s.withColor(0xFFFFFF));
            }
            prefix = iconPrefix;
        } else {
            int ci = Math.max(0, Math.min(COLORS.length - 1, m.color.index()));
            if (LETTER_PREFIX[ci] == null) {
                int c = COLORS[ci];
                LETTER_PREFIX[ci] = Text.literal("R ").styled(s -> s.withColor(c).withBold(true));
            }
            prefix = LETTER_PREFIX[ci];
        }
        MutableText out = Text.empty();
        out.append(prefix);
        out.append(original);
        return out;
    }
}
