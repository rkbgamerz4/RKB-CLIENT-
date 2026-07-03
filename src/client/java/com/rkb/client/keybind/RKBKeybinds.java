package com.rkb.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import com.rkb.client.gui.ClickGUI;
import com.rkb.client.gui.RKBClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class RKBKeybinds {

    private static final KeyMapping CLICK_GUI =
            KeyBindingHelper.registerKeyBinding(
                    new KeyMapping(
                            "key.rkbclient.clickgui",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_RIGHT_SHIFT,
                            "category.rkbclient"
                    )
            );

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CLICK_GUI.consumeClick()) {
                Minecraft.getInstance().setScreen(new RKBClickGuiScreen());
            }
        });
    }
}
