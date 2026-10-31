package com.rkb.client.module;

import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/** An extra action a module offers: gets its own key binding and a Run button on the module page. */
public record KeyAction(String id, String name, int defaultKey, Consumer<MinecraftClient> run) {
}
