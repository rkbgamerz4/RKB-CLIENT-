# Changelog

## 0.3.0
- Minimap: waypoints (saved per world and dimension), dotted line to a tracked waypoint, waypoint list screen,
  rotate with player, square/circle shape, opacity, auto cave view, coordinates, N marker, death waypoint.
- Modules can offer extra actions: each gets an unbound key binding and a Run button on its settings page.

## 0.2.1
- Minimap module (World): top-down terrain map, north-up, rebuilt a few rows per tick, run-length drawn. No
  player/mob radar by design. Nether/End use a cave-style scan from your height.

## 0.2.0
- New menu: module cards with animated toggles, per-module settings page (keybind, options, reset to defaults),
  Configuration page (3 profile slots, reset all), About page, page fade, scalable sidebar.
- HUD Editor: drag HUD elements, anchor snaps to the nearest corner.
- Categories now: HUD, Performance, Visuals, Controls, Movement, Utility, World, Configuration, About RKB.
- New modules: Armor Status, Potion Effects, Ping Display, Session Timer, Performance Stats, World Info, Toggle Sneak.
- CPS Counter moved to HUD (the Combat category was removed).

## 0.1.2
- Keyboard handling is now event-driven. Each module gets a vanilla key binding (Options > Controls > "RKB CLIENT"),
  consumed from Minecraft's own input events. Per-frame GLFW key scanning is gone: no polling loop is left for keys.
- Menu: keybind capture, menu-key close and arrow-key scrolling use Screen.keyPressed(KeyInput) instead of scanning.
- Cached module lists, per-category lists and the Interface module, so HUD/menu frames no longer allocate list copies
  or search for modules every frame.
- Mouse is still read with one guarded glfwGetMouseButton call per frame (needed for drag-scroll on touch).

## 0.1.1
- Fix Zalith crash: full GLFW key scan threw IndexOutOfBoundsException from glfwGetKey. Reads are guarded and the
  tick handler can no longer crash the game.

## 0.1.0
- Initial scaffold: module framework, in-game menu, JSON config, 11 starter modules.
