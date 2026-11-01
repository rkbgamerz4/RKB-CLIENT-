package com.rkb.client.gui;

import com.rkb.client.module.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Menu-only metadata: the Minecraft item shown as each module's icon, and which modules carry the NEW! badge. */
public final class ModuleMeta {
    private static final Set<String> NEW_IDS = Set.of(
            "minimap", "nametag_badge", "rkb_cosmetics", "night_vision", "target_health", "health_display", "weather_changer");

    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static ItemStack fallback;

    private ModuleMeta() {
    }

    public static boolean isNew(Module m) {
        return NEW_IDS.contains(m.id());
    }

    public static ItemStack icon(Module m) {
        if (ICONS.isEmpty()) {
            fill();
        }
        ItemStack s = ICONS.get(m.id());
        if (s != null) {
            return s;
        }
        if (fallback == null) {
            fallback = new ItemStack(Items.PAPER);
        }
        return fallback;
    }

    private static void put(String id, net.minecraft.item.Item item) {
        ICONS.put(id, new ItemStack(item));
    }

    private static void fill() {
        put("fps", Items.REDSTONE_TORCH);
        put("coordinates", Items.COMPASS);
        put("info_hud", Items.BOOK);
        put("keystrokes", Items.OAK_BUTTON);
        put("crosshair", Items.TARGET);
        put("zoom", Items.SPYGLASS);
        put("memory", Items.REPEATER);
        put("clock", Items.CLOCK);
        put("interface", Items.REDSTONE);
        put("cps", Items.STONE_SWORD);
        put("toggle_sprint", Items.DIAMOND_BOOTS);
        put("toggle_sneak", Items.LEATHER_BOOTS);
        put("armor_hud", Items.IRON_CHESTPLATE);
        put("potion_hud", Items.POTION);
        put("ping", Items.LEAD);
        put("session_timer", Items.CLOCK);
        put("perf_stats", Items.COMPARATOR);
        put("world_info", Items.CLOCK);
        put("minimap", Items.FILLED_MAP);
        put("no_view_bobbing", Items.FEATHER);
        put("steady_fov", Items.ENDER_EYE);
        put("auto_jump", Items.SLIME_BLOCK);
        put("reduce_particles", Items.FIREWORK_STAR);
        put("view_distance", Items.ENDER_PEARL);
        put("fps_limit", Items.CLOCK);
        put("entity_distance", Items.ZOMBIE_HEAD);
        put("sensitivity", Items.LEVER);
        put("advanced_tooltips", Items.WRITABLE_BOOK);
        put("night_vision", Items.GOLDEN_CARROT);
        put("weather_changer", Items.WATER_BUCKET);
        put("health_display", Items.APPLE);
        put("hunger_display", Items.COOKED_BEEF);
        put("target_health", Items.IRON_SWORD);
        put("target_distance", Items.ARROW);
        put("combo_counter", Items.DIAMOND_SWORD);
        put("held_item", Items.CRAFTING_TABLE);
        put("item_counter", Items.CHEST);
        put("speedometer", Items.SUGAR);
        put("server_address", Items.OAK_SIGN);
        put("biome", Items.GRASS_BLOCK);
        put("light_level", Items.TORCH);
        put("nametag_badge", Items.NAME_TAG);
        put("rkb_cosmetics", Items.ELYTRA);
    }
}
