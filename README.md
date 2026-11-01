# RKB CLIENT

Client-side Fabric mod for Minecraft **1.21.11** with an in-game RKB menu, HUD modules and a JSON config. Built to run
in **RKB LAUNCHER** (or any launcher with a Fabric 1.21.11 profile).

> Status: source complete, **not yet compiled by its author's tooling**. Run the build below and report errors; see
> "Known risks".

## Versions
| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.18.4 build / `>=0.18.2` required |
| Fabric API | 0.141.1+1.21.11 |
| Mappings | Yarn 1.21.11+build.4 |
| Loom | 1.14-SNAPSHOT |
| Java | 21 |

Change them in `gradle.properties` (check https://fabricmc.net/develop).

## Features
Open the menu with **Right Shift** or `/rkb`. The menu has a tab bar (Mod Menu, Settings, Profiles, HUD Editor,
Waypoints, About), filter chips (All, New and one per category), search, a heart filter for favourites and a grid/compact
switch. Each module is a card with an icon, a heart (favourite), a gear (settings page: keybind, options, reset to
defaults) and an Enabled/Disabled button. Drag the list (or wheel / arrow keys) to scroll. **Profiles** has 3 profile
slots and reset-all; **HUD Editor** lets you drag HUD elements.

| Category | Modules |
|---|---|
| HUD | FPS Display, Coordinates, Info HUD, Keystrokes, CPS Counter, Armor Status, Potion Effects, Ping Display, Health Display, Hunger Display, Target Health, Target Distance, Combo Counter, Held Item Info, Item Counter, Speedometer, Server Address |
| Performance | Memory Usage, Performance Stats, Reduce Particles, View Distance, FPS Limit, Entity Distance |
| Visuals | Custom Crosshair, Zoom, Night Vision, No View Bobbing, Steady FOV, Name Tag Badge |
| Controls | Toggle Sneak, Mouse Sensitivity |
| Movement | Toggle Sprint, Auto Jump |
| Utility | Clock, Session Timer, Advanced Tooltips, Interface (menu key, accent, HUD style) |
| World | World Info, Minimap with waypoints, Weather Changer, Biome Display, Light Level |

42 modules in total. Modules that change a vanilla option (View Distance, FPS Limit, Entity Distance, Mouse Sensitivity,
Reduce Particles, No View Bobbing, Steady FOV, Auto Jump, Advanced Tooltips) restore your own value when turned off. If the
game crashes while one is on, check Options. The ones that change performance or sensitivity are never saved as "on".
Night Vision and Weather Changer are client-side only; some servers do not allow them. Target Health only shows the
entity under your crosshair; there is no radar.

No module bypasses anti-cheat or gives an unfair advantage. The menu key cannot be cleared.

## Minimap, full map and waypoints
Enable **Minimap** (World). Press **M** for the full-screen map (rebindable in Options > Controls > RKB CLIENT, or use
the Run button on the Minimap settings page). On the full map: drag to pan, scroll or Zoom +/- to zoom, tap a spot and
press **+ Waypoint**, **Center** to return to yourself.

Minimap settings: size, range (32/48/64), **quality** (Low is the fastest), square or circle, rotate with player, opacity,
cave view underground, coordinates, N marker, waypoint names, line to the tracked waypoint, death waypoint.

Performance: the map is sampled a few rows per tick and drawn as a merged list of rectangles that is rebuilt only when you
move a block, turn (rotation on) or change a setting. If it still lags, use range 32 and quality Low, or turn it off.

- **Waypoint list**, **Add waypoint here** and **Track next waypoint** are Run buttons on the Minimap page (no keyboard needed).
- A tracked waypoint gets a dotted line from you plus its name and distance under the map.
- Waypoints are saved per server or singleplayer world and per dimension in `config/rkbclient-waypoints.json`.
- Terrain only: no player, mob or item radar. Some servers ban minimaps; check the rules before using it.

## Name tag badge
Shows the **RKB logo** (or a bold R, see "Badge style") before other players' name tags, like Feather's icon. The logo is a
font glyph added through `assets/minecraft/font/default.json` (private-use character U+E7A1), so it needs no extra
rendering code. RKB CLIENT has no server, so it cannot tell who else runs it: **Friends** mode shows the badge for names
you add with `/rkb badge add <name>` (`remove`, `list` also work); **Everyone** shows it on every player and is
cosmetic only. Minecraft never draws your own name tag, so your own name cannot carry the badge.

## Cloud sync and friends (0.8.0)
The Name Tag Badge is **off by default**. Settings tab > NAME TAG BADGE has the master toggle; options appear only when
it is on. Friends: `/rkb friend add <name or UUID>`, `/rkb friend remove <...>`, `/rkb friend list`, or Manage in the
menu. Cloud: put your Supabase publishable key into `config/rkbclient-cloud.json` (`anonKey`), deploy `supabase/`
(see `supabase/README.md`), then `/rkb cloud login <email> <password>`, `/rkb cloud link`, `/rkb cloud sync`,
`/rkb cloud status`, `/rkb cloud cosmetics`. Release notes: `docs/RELEASE_NOTES_0.8.0.md`; test status:
`docs/TESTING_0.8.0.md`.

## RKB account cosmetics (0.9.0)
Cosmetics belong to your RKB account, not to a Minecraft account or launcher. Sign in (`/rkb cloud register|login <email> <password>`),
open the COSMETICS tab, claim/redeem/buy, equip a cape and a badge, and turn on the **RKB Cosmetics** module to see yours and other
RKB players'. Setup, trust model and limits: `docs/COSMETICS_0.9.0.md`, backend: `supabase/README.md`, test status: `docs/TESTING_0.9.0.md`.

## Build (GitHub Codespaces)
```bash
bash scripts/bootstrap-wrapper.sh   # once: creates gradlew + wrapper jar (binary files not in the ZIP)
java -version                       # 21
./gradlew build
ls build/libs                       # use rkbclient-0.1.1.jar (not -sources)
```
Test in dev: `./gradlew runClient`. CI: `.github/workflows/build.yml` builds on every push and uploads the JAR.

## Install
See `docs/INSTALL-RKB-LAUNCHER.md`. Termux notes: `docs/TERMUX.md`. Modrinth: `docs/MODRINTH.md`.

## Config
`config/rkbclient.json` (JSON, auto-created). Invalid values are ignored; an unreadable file is renamed
`rkbclient.json.broken` and defaults are used.

## Adding a module
Extend `Module` (or `TextHudModule` for text overlays), add `Setting`s with `add(...)`, override `onEnable`,
`onDisable`, `onTick` or `renderHud`, and register it in `ModuleManager.init()`. Settings and keybinds persist
automatically.

## Known risks (verify on first build)
- Written against 1.21.11 Yarn names from memory and docs; no compiler was available when this was produced.
  Most likely places to need a one-line fix: `HudRenderCallback` (Fabric may deprecate it), `Window.getHandle()` in
  `KeyUtil`, `TextFieldWidget.setPlaceholder` in `RkbMenuScreen`.
- The crosshair-hiding mixin targets `InGameHud.renderCrosshair` with `require = 0`: if the name is wrong the game still
  starts, but the vanilla crosshair stays visible next to the custom one.
- Keys use Minecraft's own key bindings (confirmed against the 1.21.11 Yarn javadoc). Two calls are assumed, not checked:
  `KeyBindingHelper.getBoundKeyOf` and `InputUtil.Key.getCode()` in `Module.keyCode()`.
- The mouse is still read via one guarded `glfwGetMouseButton` call per frame.

## Licence
MIT, see `LICENSE`.
