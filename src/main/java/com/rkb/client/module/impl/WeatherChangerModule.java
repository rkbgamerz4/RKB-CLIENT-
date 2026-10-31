package com.rkb.client.module.impl;

import com.rkb.client.module.Category;
import com.rkb.client.module.Module;
import com.rkb.client.module.setting.ChoiceSetting;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/** Changes the weather you see on this client only. The server's real weather is unchanged. */
public class WeatherChangerModule extends Module {
    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Weather", List.of("Clear", "Rain", "Thunder"), "Clear"));

    public WeatherChangerModule() {
        super("weather_changer", "Weather Changer", "Shows clear, rainy or stormy skies on your screen only.",
                Category.WORLD);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (mc.world == null) {
            return;
        }
        String m = mode.get();
        mc.world.setRainGradient(m.equals("Clear") ? 0f : 1f);
        mc.world.setThunderGradient(m.equals("Thunder") ? 1f : 0f);
    }
}
