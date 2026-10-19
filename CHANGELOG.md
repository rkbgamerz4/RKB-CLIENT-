# Changelog

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
