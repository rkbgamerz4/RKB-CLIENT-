package com.rkb.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rkb.client.cloud.CloudService;
import com.rkb.client.cloud.CloudStore;
import com.rkb.client.cloud.CosmeticsCatalog;
import com.rkb.client.cloud.FriendsManager;
import com.rkb.client.cosmetics.CosmeticsManager;
import com.rkb.client.gui.CosmeticsScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * /rkb                      open the menu
 * /rkb friend add|remove <player or UUID>, /rkb friend list
 * /rkb badge add|remove|list   same as friend (kept from version 0.7)
 * /rkb cloud register|login <email> <password> | logout | link | sync | status | cosmetics
 * /rkb cosmetics list|inventory|refresh|open|redeem <code>|equip <id>|unequip <slot>
 * history/claim/buy are kept only to report that RKB Cosmetics V2 has no such actions.
 * Client commands are not sent to the game server, but never type your password on a shared screen.
 */
public final class RkbCommands {
    private RkbCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("rkb")
                        .executes(ctx -> {
                            RkbClient.requestMenu();
                            return 1;
                        })
                        .then(friends("friend"))
                        .then(friends("badge"))
                        .then(cosmetics())
                        .then(cloud())));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> friends(String root) {
        return ClientCommandManager.literal(root)
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    FriendsManager.addByInput(StringArgumentType.getString(ctx, "player"), CloudService::notifyPlayer);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> {
                                    FriendsManager.removeByInput(StringArgumentType.getString(ctx, "player"), CloudService::notifyPlayer);
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("list")
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder();
                            for (FriendsManager.Friend f : FriendsManager.list()) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(FriendsManager.displayName(f));
                            }
                            CloudService.notifyPlayer(sb.length() == 0
                                    ? "Your friends list is empty. Use /rkb friend add <player>"
                                    : "Friends (" + FriendsManager.size() + "): " + sb);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> cloud() {
        return ClientCommandManager.literal("cloud")
                .then(ClientCommandManager.literal("register")
                        .then(ClientCommandManager.argument("email", StringArgumentType.word())
                                .then(ClientCommandManager.argument("password", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CloudService.register(StringArgumentType.getString(ctx, "email"),
                                                    StringArgumentType.getString(ctx, "password"));
                                            return 1;
                                        }))))
                .then(ClientCommandManager.literal("login")
                        .then(ClientCommandManager.argument("email", StringArgumentType.word())
                                .then(ClientCommandManager.argument("password", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CloudService.login(StringArgumentType.getString(ctx, "email"),
                                                    StringArgumentType.getString(ctx, "password"));
                                            return 1;
                                        }))))
                .then(ClientCommandManager.literal("logout").executes(ctx -> {
                    CloudService.logout();
                    return 1;
                }))
                .then(ClientCommandManager.literal("link").executes(ctx -> {
                    CloudService.link();
                    return 1;
                }))
                .then(ClientCommandManager.literal("sync").executes(ctx -> {
                    CloudService.manualSync();
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    String who = CloudStore.signedIn() ? CloudStore.email() : "not signed in";
                    String link = CloudService.isLinked() ? "linked to " + CloudStore.linkedName() : "not linked";
                    CloudService.notifyPlayer("Cloud: " + CloudService.statusText() + " | " + who + " | " + link);
                    return 1;
                }))
                .then(ClientCommandManager.literal("cosmetics").executes(ctx -> {
                    var cached = CosmeticsCatalog.cached();
                    CloudService.notifyPlayer("Cosmetics cache: " + cached.size() + " entries. Refreshing...");
                    CosmeticsCatalog.refresh();
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> cosmetics() {
        return ClientCommandManager.literal("cosmetics")
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    var st = CosmeticsManager.state();
                    if (st.items().isEmpty()) {
                        CloudService.notifyPlayer("The shop is empty or not loaded. Try /rkb cosmetics refresh");
                    }
                    int shown = 0;
                    for (var i : st.items()) {
                        if (shown++ >= 12) {
                            CloudService.notifyPlayer("... more in the Cosmetics screen (/rkb cosmetics open)");
                            break;
                        }
                        CloudService.notifyPlayer(i.id() + " | " + i.name() + " | " + i.type() + " | "
                                + (i.priceCents() == 0 ? "free" : i.priceCents() / 100 + "." + String.format("%02d", i.priceCents() % 100) + " " + i.currency())
                                + (i.owned() ? " | owned" : ""));
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("inventory").executes(ctx -> {
                    var st = CosmeticsManager.state();
                    int n = 0;
                    for (var i : st.items()) {
                        if (i.owned()) {
                            n++;
                            CloudService.notifyPlayer(i.id() + " | " + i.name() + " | " + i.type()
                                    + (i.id().equals(st.equipped().get(i.type())) ? " | equipped" : ""));
                        }
                    }
                    if (n == 0) {
                        CloudService.notifyPlayer("Nothing in your inventory yet. Account: " + (st.rkbId().isEmpty() ? "not loaded" : st.rkbId()));
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("history").executes(ctx -> {
                    CloudService.notifyPlayer("RKB Cosmetics V2 has no history action. Use /rkb cosmetics inventory.");
                    return 1;
                }))
                .then(ClientCommandManager.literal("refresh").executes(ctx -> {
                    CosmeticsManager.refresh();
                    return 1;
                }))
                .then(ClientCommandManager.literal("open").executes(ctx -> {
                    RkbClient.requestScreen(CosmeticsScreen::new);
                    return 1;
                }))
                .then(ClientCommandManager.literal("claim")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                            CosmeticsManager.claim(StringArgumentType.getString(ctx, "id"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("buy")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                            CosmeticsManager.purchaseStart(StringArgumentType.getString(ctx, "id"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("redeem")
                        .then(ClientCommandManager.argument("code", StringArgumentType.greedyString()).executes(ctx -> {
                            CosmeticsManager.redeem(StringArgumentType.getString(ctx, "code"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("equip")
                        .then(ClientCommandManager.argument("id", StringArgumentType.word()).executes(ctx -> {
                            CosmeticsManager.equip(StringArgumentType.getString(ctx, "id"));
                            return 1;
                        })))
                .then(ClientCommandManager.literal("unequip")
                        .then(ClientCommandManager.argument("slot", StringArgumentType.word()).executes(ctx -> {
                            CosmeticsManager.unequip(StringArgumentType.getString(ctx, "slot"));
                            return 1;
                        })));
    }
}
