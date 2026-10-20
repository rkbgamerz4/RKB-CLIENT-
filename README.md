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
Open the menu with **Right Shift** or `/rkb`. Click a card to toggle a module, **Settings** for its page (keybind,
options, reset to defaults). Drag the list (or wheel / arrow keys) to scroll. Search covers all categories.
**Configuration** has 3 profile slots, HUD Editor and reset-all; **HUD Editor** lets you drag HUD elements.

| Category | Modules |
|---|---|
| HUD | FPS Display, Coordinates, Info HUD, Keystrokes, CPS Counter, Armor Status, Potion Effects, Ping Display |
| Performance | Memory Usage, Performance Stats (avg/low FPS, frame time) |
| Visuals | Custom Crosshair, Zoom |
| Controls | Toggle Sneak (vanilla option) |
| Movement | Toggle Sprint |
| Utility | Clock, Session Timer, Interface (menu key, accent, HUD style) |
| World | World Info (day, time, weather), Minimap with waypoints |

No module bypasses anti-cheat or gives an unfair advantage. The menu key cannot be cleared.

## Minimap and waypoints
Enable **Minimap** (World), then open its Settings page: size, range (32/48/64), square or circle, rotate with player,
opacity, cave view underground, coordinates, N marker, waypoint names, line to the tracked waypoint, death waypoint.
Move it with the HUD Editor.

- **Add waypoint here** / **Open waypoint list** / **Track next waypoint**: Run buttons on the Minimap settings page
  (works on Android with no keyboard), and unbound keys under Options > Controls > RKB CLIENT.
- The waypoint list lets you add by name and X/Y/Z, track one (a dotted line from you to it plus name and distance
  under the map), hide or delete. Off-map waypoints stick to the map edge.
- Waypoints are saved per server or singleplayer world and per dimension in `config/rkbclient-waypoints.json`.
- Terrain only: no player, mob or item radar. Some servers ban minimaps; check the rules before using it.

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
