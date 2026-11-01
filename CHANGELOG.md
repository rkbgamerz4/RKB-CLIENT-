# Changelog

## 0.9.0
- Account-based cosmetics: RKB account (RKB_1234) owns the inventory; claim, redeem, pending purchase, equip/unequip, history.
- RKB Cosmetics module: your and other RKB players' capes and badges (cached, hash-checked, no network in render).
- `/rkb cloud register`, `/rkb cosmetics ...`, COSMETICS tab, new badge glyph variants.
- Supabase: rkb_* tables, RLS, service-role SQL functions, rkb-cosmetics Edge Function, public cape bucket.

## 0.8.0
- Name Tag Badge: off by default, Settings section with master toggle, style, visibility, cloud sync and status.
- UUID-based friends list (add by username/UUID, remove, duplicates and invalid input handled), 0.7 names migrated.
- Supabase integration: Auth login, Mojang-verified account link, background sync of settings and friends with a
  newer-wins rule, offline cache, cosmetics-catalog metadata cache. Migration, RLS and Edge Function in supabase/.
- Settings can be hidden while their feature is off (visibleWhen).

## 0.7.0
- Name tags now show the RKB logo as a font glyph (Feather-style icon); the letter R is still available as a style.
  Friend lookups are cached per player and the prefix text is built once.
- Optimisation pass on HUD modules: FPS, Coordinates, Info HUD, Memory, Clock, CPS and Potion Effects rebuild their
  text only when the value changes; text HUD boxes are measured only when their lines change.
- The logo also appears in the menu footer.

## 0.6.0
- New menu layout: top tab bar, filter chips, search, favourites (heart) and a grid of module cards with item icons,
  gear button, Enabled/Disabled button and NEW! badges. Compact view switch. Same RKB colours.
- Favourites are saved in rkbclient.json.

## 0.5.0
- Performance: the minimap now draws a cached, merged rectangle list instead of one fill per cell, rebuilds it only
  when needed, has a Quality setting, and defaults to range 32.
- Map looks less flat: soft hill shading, depth-shaded water, gentler colours, thinner frame.
- Full-screen map (default key M): pan, zoom, tap to select, add waypoint at the selected spot.
- Name Tag Badge module (R before name tags) with /rkb badge add|remove|list.
- New icon from the supplied RKB logo.

## 0.4.0
- 22 new modules (41 total): Night Vision, Health Display, Hunger Display, Target Health, Target Distance,
  Combo Counter, Held Item Info, Item Counter, Speedometer, Server Address, Biome Display, Light Level,
  Weather Changer, No View Bobbing, Steady FOV, Auto Jump, Reduce Particles, View Distance, FPS Limit,
  Entity Distance, Mouse Sensitivity, Advanced Tooltips.

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
