package com.rkb.client.module;

import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/** An extra action a module offers: gets its own key binding (unbound by default) and a Run button in the menu. */
public record KeyAction(String id, String name, Consumer<MinecraftClient> run) {
}
