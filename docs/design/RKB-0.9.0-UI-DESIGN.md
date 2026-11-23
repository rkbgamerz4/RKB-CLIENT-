# RKB CLIENT 0.9.0 — UI Redesign Design Spec

**Status:** Design handoff / pre-implementation  
**Branch:** `feature/account-cosmetics-0.9.0`  
**Date:** 2026-11-23  
**Scope:** Visual redesign and implementation plan for RKB menu UI only. No mod implementation is included in this deliverable.

## 0. Deliverables

| File | Purpose |
|---|---|
| `docs/design/concept-01-main-menu.png` | Main menu concept board |
| `docs/design/concept-02-settings.png` | RKB Settings concept board |
| `docs/design/concept-03-modules.png` | RKB Modules concept board |
| `docs/design/concept-04-cosmetics-skin.png` | Cosmetics / Skin tab concept board |
| `docs/design/concept-05-cosmetics-cape.png` | Cosmetics / Cape tab concept board |
| `docs/design/concept-06-cosmetics-redeem.png` | Cosmetics / Redeem tab concept board |
| `docs/design/concept-07-account.png` | Account / signed-out concept board |
| `docs/design/build_boards.py` | SVG board generator (source for the concepts) |
| `docs/design/RKB-0.9.0-UI-DESIGN.md` | This handoff spec |

**Important:** these are vector concept boards rendered locally with `python3` + `rsvg-convert`. They are **not in-game screenshots**. AI image generation was unavailable (`Invalid credential`), so no generated screenshots were fabricated.

---

## 1. Reference Analysis

### 1.1 Reference 1 — Feather Mod Menu

**What works**
- Strong desktop-client information hierarchy: left navigation, content area, clear active state.
- Modules are scannable as cards/list rows with visible state and short descriptions.
- Search and category filters are immediately discoverable.
- The layout feels like a product, not a debug menu.

**What should not be copied**
- Teal/neon accent glow and high-contrast dark gradients.
- Dense, dashboard-like visual noise.
- Overly stylized card treatment that feels like a web app overlay.

**RKB takeaway**
- Keep the navigation/content structure and module browsing clarity.
- Replace the glow/neon with flat surfaces, quiet borders, and RKB blue as a restrained accent.

### 1.2 Reference 2 — RKB Cosmetics Concept

**What works**
- Already aligned with RKB identity: dark flat surfaces, blue accent, clear tab separation.
- Preview pane + details pane is the right mental model for cosmetics.
- Skin and cape actions are understandable and local to the selected item.

**What to refine**
- Make tab/content spacing more consistent across Skin/Cape/Redeem.
- Make ownership/equipped/unavailable states more explicit.
- Keep preview dominant, but avoid making the right panel feel crowded.
- Make account/sign-in state visible without forcing cloud flows.

**RKB takeaway**
- Preserve the concept's structure and tone.
- Treat it as the cosmetics baseline and normalize it into the same shell/system as Settings/Modules/Account.

### 1.3 Reference 3 — RKB Main Menu Concept

**What works**
- Centered brand hero fits a Minecraft title screen better than a dashboard.
- RKB logo/wordmark is restrained and recognizable.
- Action grouping gives a clear path: play, customize, configure, quit.

**What to refine**
- Avoid button stacks that become cramped at 854×480.
- Keep account/profile status visible but secondary.
- Separate Minecraft destinations from RKB destinations without duplicating labels confusingly.

**RKB takeaway**
- Use a centered hero for the main menu only.
- Use the sidebar shell for interior screens so the product feels cohesive after entering the client UI.

---

## 2. Design Direction

**Visual keywords:** premium, calm, flat, sharp, readable, PvP-client, desktop-first but mobile-safe.

**Rules**
- No neon, glow, bloom, shiny gradients, heavy shadows, glassmorphism, or decorative shapes.
- No emoji/icons-as-decoration. Icons are simple 16px line icons only where they aid recognition.
- Corners: 4px for inputs/small controls, 6px for panels/buttons/cards, 8px for major containers/preview frame.
- Borders are 1px `#292E38`; active/selected border may use `#3988D8`.
- Hover is subtle: fill shifts by one step (`#1C2028` → `#222733` / button hover `#3988D8` → `#4B98E6`). No motion required.
- Spacing uses an 4px grid: 4/8/12/16/24/32.
- Branding appears in logo/wordmark and small footer/status labels; it is not repeated as decorative background text.

---

## 3. Design System

### 3.1 Color Tokens

| Token | Hex | Usage | Contrast note |
|---|---:|---|---|
| `bg` | `#101216` | App background | Base canvas |
| `bg.secondary` | `#171A20` | Sidebar / inset areas | Subtle separation from bg |
| `panel` | `#1C2028` | Cards, panels, inputs, rows | Main surface |
| `panel.alt` | `#222733` | Selected row / hover surface | One-step elevation |
| `panel.soft` | `#1F2530` | Selected card/state | Quiet active |
| `border` | `#292E38` | Dividers, panel borders | Low contrast line |
| `border.strong` | `#343B47` | Input focus only when needed | Use sparingly |
| `text` | `#F0F1F3` | Primary text | ~14.5:1 on bg, passes AAA |
| `muted` | `#A0A6B0` | Secondary text | ~8.0:1 on bg, passes AAA |
| `dim` | `#6F7682` | Tertiary labels/meta | Use for non-essential metadata only |
| `accent` | `#3988D8` | Primary action, active nav, focus | ~5.2:1 on bg for large/UI; use with white/bold label where needed |
| `accent.hover` | `#4B98E6` | Primary hover | subtle only |
| `success` | `#4E9E6A` | Linked/equipped/owned/success | status only |
| `warn` | `#C9A24B` | Loading/warning | status only |
| `danger` | `#C05A5A` | Destructive/error | status only |

**Accent rule:** `#3988D8` is the fixed UI chrome accent. The existing user HUD accent (`InterfaceModule.accent()`) remains HUD-only and must not recolor the new menu chrome.

### 3.2 Typography

**Preferred menu font:** Inter (bundled, optional).  
**Fallback:** vanilla Minecraft font / existing font renderer.

| Style | Size / Weight | Usage |
|---|---|---|
| Display | 34 / 700 | Main menu wordmark only |
| Title | 17 / 700 | Screen title |
| Section | 11 / 700 | Panel/section heading |
| Body | 9.5 / 600-700 | Buttons, row titles, values |
| Secondary | 8-8.5 / 400 | Descriptions, helper text |
| Meta | 7-7.5 / 700 | Uppercase labels/status chips |

Rules:
- No all-caps for long sentences; all-caps only for short section labels.
- Keep line lengths short in side panels.
- HUD retains its own font/rendering behavior and is not forced to Inter.

### 3.3 Components

| Component | Spec |
|---|---|
| Primary button | Fill `#3988D8`, text `#FFFFFF`, 6px radius, hover `#4B98E6` |
| Secondary button | Fill `#1C2028`, 1px `#292E38`, text `#F0F1F3` |
| Ghost button | No fill/border, muted text, for low-emphasis actions |
| Danger button | Muted red fill/border, red text; no bright alarm styling |
| Disabled | `#232833` fill, `#6F7682` text, no hover |
| Search/filter input | `#171A20` fill, `#292E38` border, 4px radius, dim placeholder |
| Toggle | 28×14 track, off `#222733`, on `#3988D8`, white knob |
| Slider | 4px track, accent fill to value, 10px knob |
| Dropdown | Same as input, right chevron, selected value in primary text |
| Keybind | Small button with key label; listening state uses accent border + "Press key..." |
| Chip/filter | 24px height, inactive border-only muted, active filled accent |
| List row / card | 1px border, selected row gets `#1F2530` + 2px left accent bar |
| Status dot | 5-6px, success/warn/danger/dim; always paired with text |

### 3.4 Iconography

- 16px line icons, 1.5px stroke, muted by default, accent on active nav.
- Use icons only for nav, search, upload/link, refresh/check/power, and compact actions.
- Do not add decorative icons to every row.

---

## 4. Navigation & Information Architecture

### 4.1 Main Menu (no sidebar)

Centered hero with two action columns and one full-width quit button.

| Group | Buttons | Destination | Implementation note |
|---|---|---|---|
| Minecraft | Singleplayer | `SelectWorldScreen(this)` | Existing vanilla destination |
| Minecraft | Multiplayer | `MultiplayerScreen(this)` | Existing vanilla destination |
| Minecraft | Mods | Fabric Mod Menu screen if present | **Not currently a dependency**; if absent show unobtrusive modal: "Fabric Mod Menu is not installed." Never open RKB Modules. |
| Minecraft | Settings | `OptionsScreen(this, mc.options)` | Existing vanilla destination; preserve parent screen |
| RKB | Cosmetics | `CosmeticsScreen` | Existing screen, restyled |
| RKB | RKB Modules | `RKB Modules` screen | New dedicated screen/shell state |
| RKB | RKB Settings | `RKB Settings` screen | New dedicated screen/shell state |
| System | Quit Game | `mc.scheduleStop()` | Existing behavior |

Top-right account chip: player head, username, local/cloud state, quick icons (cosmetics/settings/quit) or equivalent compact actions; click opens Account screen.

Footer: `RKB CLIENT 0.9.0 · Minecraft 1.21.11` left, `FPS 120 · Local mode/RKB Cloud` right.

### 4.2 Interior Shell (sidebar)

Persistent left sidebar, 216px at 1280-wide concept canvas.

| Sidebar group | Item | Destination |
|---|---|---|
| Minecraft | Singleplayer | world select |
| Minecraft | Multiplayer | server select |
| Minecraft | Mods | Mod Menu or unavailable modal |
| Minecraft | Settings | vanilla options |
| RKB | Cosmetics | cosmetics |
| RKB | RKB Modules | modules browser |
| RKB | RKB Settings | settings |
| System | Quit Game | exit |

Sidebar footer: compact account state card (`Signed out / Local mode` or `Signed in / email`) with "Manage account" action.

**Preserved existing screens not in sidebar:** Profiles, HUD Editor, Waypoints, Friends, Map, About. These remain reachable from RKB Settings (Other/tools), existing keybinds/commands, or existing in-game flows. They are not removed.

---

## 5. Screen Designs

### 5.1 Main Menu (`concept-01-main-menu.png`)

- Canvas: 1280×720 concept.
- Center logo: 72px RKB mark, wordmark `RKB CLIENT`, subtitle `MINECRAFT PVP CLIENT · 1.21.11`.
- Two columns, 238px button width, 34px height, 14px gap, 42px row stride.
- Primary action: Singleplayer only.
- Quit Game: full-width danger secondary, separated below main grid.
- No player render in the hero; identity lives in the top-right account chip and footer state.

### 5.2 RKB Settings (`concept-02-settings.png`)

- Title: `RKB Settings`; subtitle: `Tune the client, not the game.`
- Top-right search: `Search settings`.
- Left panel inside content: category list.
- Right panel: scrollable settings rows.

**Categories (sidebar inside settings):**
1. **General** — curated overview: Menu key, FPS in menu header, Version in menu header, HUD background, HUD text shadow, HUD accent color, HUD text color, link to Other tools. This is a convenience page; values are the same saved settings.
2. **Interface** — menu/HUD appearance settings from `InterfaceModule` and any real interface toggles.
3. **Performance** — real performance modules/settings only: View Distance, FPS Limit, Entity Distance, Reduce Particles, Memory, Performance Stats as applicable.
4. **Controls** — menu key and real keybind-related settings; module keybinds are edited in module config rows/cards.
5. **HUD** — HUD element toggles/quick config; HUD Editor entry point.
6. **Modules** — module defaults/behavior, reset confirmation, profile-related module management entry.
7. **Other** — Profiles, HUD Editor, Waypoints, Friends, account/cloud status, local files note, reset-all with confirmation.

**Row patterns**
- Toggle row: label, description, right-aligned switch.
- Slider row: label, description, value, track.
- Dropdown row: label, description, closed dropdown.
- Keybind row: label, description, key button.
- Action row: label, description, secondary button.

### 5.3 RKB Modules (`concept-03-modules.png`)

- Title: `RKB Modules`; subtitle: `Enable, search, and configure modules.`
- Search input at top-left of content.
- Category chips: `All`, `HUD`, `Performance`, `Visuals`, `Controls`, `Movement`, `Utility`, `World`, `Favorites` (Favorites only if favorites exist / filter supported; do not invent data).
- Module cards in a 3-column grid at desktop width; 2-column below ~1100; 1-column/stacked below ~720.
- Card content: module name, short description, category label, config icon, enable toggle.
- Clicking card body or config icon opens a detail/config drawer or detail panel; toggle does not open config.
- Footer/meta: `12 modules · 7 enabled` style count reflects current filter/search result.

**Real categories:** HUD, Performance, Visuals, Controls, Movement, Utility, World.  
**Non-module pages excluded from module grid:** Configuration/About are not fake module categories.

### 5.4 Cosmetics — Skin (`concept-04-cosmetics-skin.png`)

- Tabs: `Skin | Cape | Redeem` (exactly three).
- Left preview: real 3D player preview in implementation; concept shows blocky Steve placeholder. Must support rotate/drag in implementation.
- Right panel: skin collection, upload PNG, import URL, model toggle Classic/Slim, Apply, Reset to Default, local skin sets.
- Default skin row uses vanilla Steve/Alex texture by UUID; local skin operation must not imply upload.
- Copy: "Local skin · nothing is uploaded" / "Applying changes only your local appearance."

### 5.5 Cosmetics — Cape (`concept-05-cosmetics-cape.png`)

- Same shell and tab strip.
- Left preview shows back view/cape preview.
- Right panel: `RKB` section then `COUNTRY CAPES` section.
- Row states: `EQUIPPED`, `OWNED`, `UNAVAILABLE`, with appropriate button state (`Remove`, `Equip`, disabled `Equip`).
- Only show/equip capes confirmed available by assets/backend state. Bundled designs are visual fallback assets, not ownership grants.
- Copy: "Availability is confirmed by RKB Cosmetics V2." and "Local previews never grant ownership."

### 5.6 Cosmetics — Redeem (`concept-06-cosmetics-redeem.png`)

- Left: code input, Redeem button, example result, state behavior list.
- Right: cloud inventory card with sign-in state, link state, owned count, sync/manage actions.
- No redemption history unless V2 exposes a real history action; current V2 has no history action.
- Signed-out state: form disabled or button becomes sign-in CTA; local skins/capes still available.

### 5.7 Account (`concept-07-account.png`)

- Left: identity summary and state rows.
  - Minecraft account: `Online · Mojang session` or `Offline · local session`.
  - RKB Cloud: `Signed in` / `Signed out`.
  - Minecraft link: `Linked` / `Not linked` with action.
- Right: sign-in/create account form, continue offline, status, local-mode note.
- Privacy copy must be explicit: "Minecraft access tokens never leave this device. RKB Cloud never receives your Minecraft token."
- No service-role key, no Supabase secret, no fake verification.

---

## 6. Responsive / Landscape Rules

### 6.1 Breakpoints (landscape)

| Width | Layout |
|---:|---|
| >= 1280 | Desktop concept layout: sidebar 216px, modules 3-col, cosmetics preview + list side-by-side |
| 960–1279 | Sidebar 190-216px; modules 2-col; cosmetics preview narrower |
| 854–959 | Compact sidebar (icon+label or narrower labels); modules 2-col or 1-col depending on height; settings right panel full width |
| < 854 / small height | Stacked/compact: top bar or collapsible nav, content scrolls vertically; cosmetics preview above list |

### 6.2 Rules

- Minimum supported target: **854×480**. Must also be sane at 960×540 and 1280×720.
- No horizontal overflow. No overlapping hit targets. No hidden primary action.
- Touch targets >= 28px height; preferred 32-34px for primary actions.
- Scroll only inside content panels/list areas, never the whole shell unless in compact stacked mode.
- Text truncates with ellipsis; full value available by layout wrap only where safe.
- Preview panel may shrink before controls become unusable; at very small heights, preview moves above list (existing `CosmeticsLayout` already supports compact mode).

### 6.3 Implementation mapping

- Replace `RkbLayout` top-bar geometry with a sidebar shell layout for interior screens.
- Keep `CosmeticsLayout` as the base for cosmetics responsive geometry; restyle visuals, not behavior.
- Main menu should compute button grid from available width/height and fall back to single-column stacked layout below 620px width or 340px height.

---

## 7. Component/State Matrix

| Component | Default | Hover | Active/Selected | Disabled | Error/Warning |
|---|---|---|---|---|---|
| Nav item | muted text/icon | `#222733` bg | `#222733` + left accent bar + white text | n/a | n/a |
| Primary button | accent fill | accent.hover | pressed same as hover | dark fill/dim text | n/a |
| Secondary button | panel fill + border | panel.alt | selected border accent | dim | n/a |
| Toggle | off track | subtle border brighten | on accent track | dimmed track | n/a |
| Module card | panel border | panel.alt | selected card border/accent bar for config open | n/a | n/a |
| Cape row | panel | panel.alt | equipped row left bar + `EQUIPPED` | `UNAVAILABLE` + disabled Equip | missing asset/catalog -> unavailable |
| Redeem | enabled when signed in+linked as required | accent.hover | Redeeming... disabled | signed out / missing link / busy | server error verbatim |
| Account form | editable | focus border | submitting disabled | cloud key missing / busy | auth error text |

---

## 8. Implementation Handoff

### 8.1 Files/classes to modify

| Area | File | Planned change |
|---|---|---|
| Title screen | `src/main/java/com/rkb/client/gui/RkbMainMenuScreen.java` | Rebuild hero layout, account chip, button destinations, Mods availability check, footer states. Keep `TitleScreenMixin` install path. |
| Shared shell | `src/main/java/com/rkb/client/gui/RkbLayout.java` | Replace top-bar-only layout with `MainMenuLayout` + `SidebarShellLayout` responsive geometry. |
| Shared chrome | `src/main/java/com/rkb/client/gui/RkbChrome.java` | Replace top nav chip bar with left sidebar renderer; keep hit sink pattern. |
| UI theme/tokens | `src/main/java/com/rkb/client/gui/RkbUi.java`, `Theme.java` | Introduce new flat token palette. Keep `Theme.accent()` as HUD accent; add `RkbUi.CHROME_ACCENT` or equivalent fixed token for menu chrome. |
| Nav model | `src/main/java/com/rkb/client/gui/RkbScreens.java` | Add destinations for RKB Settings, RKB Modules, Mods/ModMenu unavailable handling. Keep existing `account()`, `back()`, `close()`. |
| Modules/settings | `src/main/java/com/rkb/client/gui/RkbMenuScreen.java` | Split into/replace with dedicated `RkbSettingsScreen` and `RkbModulesScreen` (or clear internal modes). Preserve all settings values and module configuration. |
| Cosmetics | `src/main/java/com/rkb/client/gui/CosmeticsScreen.java` | Restyle to new system; preserve Skin/Cape/Redeem behavior, real preview, local skin sets, V2 catalog/inventory/redeem/equip/unequip. |
| Cosmetics layout | `src/main/java/com/rkb/client/gui/CosmeticsLayout.java` | Keep geometry logic; adjust spacing constants for new shell if needed. |
| Account | `src/main/java/com/rkb/client/gui/RkbAccountScreen.java` | Restyle and clarify state matrix; preserve login/register/logout/link/manualSync. |
| Optional ModMenu | `build.gradle`, `gradle.properties`, `fabric.mod.json` | Only if approved: add optional ModMenu compile/runtime dependency and `modmenu` entrypoint/suggests. Otherwise use reflection/FabricLoader mod presence check and modal. |

### 8.2 New classes recommended

| Class | Responsibility |
|---|---|
| `RkbSidebarLayout` / shell layout record | Responsive sidebar/content/footer geometry for interior screens. |
| `RkbSettingsScreen` | Dedicated RKB settings categories and rows. |
| `RkbModulesScreen` | Module search/filter/grid/detail. |
| `RkbUiTokens` or constants in `RkbUi` | New color/spacing/radius constants. |
| `ModMenuBridge` | Optional ModMenu detection/open with no hard dependency. |

### 8.3 Reuse directly

- `ConfigManager` load/save/reset/profile logic.
- `ModuleManager`, `Module`, `Setting`, `BoolSetting`, `IntSetting`, `ChoiceSetting`, keybind handling.
- `CosmeticsManager`, `CosmeticsV2Api`, `CapeCollection`, `SkinSetStore`, `SkinSelection`, `PlayerTextures`, `RemoteCosmetics`.
- `CloudService`, `CloudStore`, `AuthState`.
- `HudEditorScreen`, `WaypointScreen`, `FriendsScreen`, `MapScreen` (restyle later if desired, but not required by this brief).

### 8.4 Mods button behavior

```java
if (FabricLoader.getInstance().isModLoaded("modmenu")) {
    // open ModMenu screen via reflection/bridge
} else {
    // show small modal: "Fabric Mod Menu is not installed."
}
```

Do **not** route Mods to `RkbMenuScreen(PAGE_MODS)`. Do **not** add a hard dependency without approval.

### 8.5 Theme/accent migration

- Current: `Theme.accent()` returns user HUD accent and is used by menu chrome.
- Target: add fixed chrome accent constant `#3988D8`; use it for all new menu UI.
- Keep `InterfaceModule.accent()`/`hudTextColor()` for HUD rendering only.
- Audit HUD classes before changing `Theme.accent()` globally; `TextHudModule`, `KeystrokesModule`, `MinimapModule` currently read `Theme.accent()`.

### 8.6 Responsive implementation notes

- Use layout records with pure geometry calculations (testable, no Minecraft draw calls).
- Avoid absolute positions in render code; compute from layout rectangles.
- For small widths, switch module grid columns and stack cosmetics preview above list.
- Sidebar can collapse to icon rail below a threshold if labels overlap; full labels at >=960px.

---

## 9. Data / Settings / Module Mapping

### 9.1 Main menu destinations

| Button | Exact destination |
|---|---|
| Singleplayer | `new SelectWorldScreen(this)` |
| Multiplayer | `new MultiplayerScreen(this)` |
| Mods | Fabric Mod Menu if installed; else unavailable modal |
| Settings | `new OptionsScreen(this, mc.options)` |
| RKB Settings | new RKB Settings screen |
| RKB Modules | new RKB Modules screen |
| Cosmetics | `new CosmeticsScreen()` |
| Quit Game | `mc.scheduleStop()` |

### 9.2 RKB Settings mapping

| Category | Real source/settings | Notes |
|---|---|---|
| General | curated overview | No invented settings; links/duplicates existing values |
| Interface | `InterfaceModule`: accent, HUD text color, HUD background, HUD shadow, FPS in menu header, Version in menu header | UI chrome accent becomes fixed; HUD accent remains setting |
| Performance | Performance-category modules | View Distance, FPS Limit, Entity Distance, Reduce Particles, Memory, Performance Stats |
| Controls | `InterfaceModule` menu key, module keybinds | Keybind capture must keep menu key non-clearable behavior |
| HUD | HUD-category modules and HUD Editor entry | Keep module enable/config |
| Modules | module defaults/reset/profile flow | Reset all requires confirm |
| Other | Profiles, HUD Editor, Waypoints, Friends, account/cloud/local files | Preserves existing screens/features |

### 9.3 Module categories (real)

Use `Category` enum values as-is: **HUD, Performance, Visuals, Controls, Movement, Utility, World**.  
Do not create fake Combat/Render/Misc categories.

### 9.4 Real modules (43 registered)

HUD: FPS Display, Coordinates, Info HUD, Keystrokes, CPS Counter, Armor Status, Potion HUD, Ping Display, Session Timer, Performance Stats, World Info, Health Display, Hunger Display, Target Health, Reach Display, Combo Counter, Held Item Info, Item Counter, Speedometer, Server Address.  
Performance: Memory Usage, FPS Limit, Entity Distance, Reduce Particles, View Distance.  
Visuals: Custom Crosshair, Zoom, No View Bobbing, Steady FOV, Night Vision, Weather Changer, Name Tag Badge, RKB Cosmetics.  
Controls: Sensitivity.  
Movement: Toggle Sprint, Toggle Sneak, Auto Jump.  
Utility: Clock, Interface, Advanced Tooltips.  
World: Minimap, Biome Display, Light Level.

*Source: `ModuleManager` registration; names/descriptions from module constructors.*

### 9.5 Cosmetics/cape/account

- Skin: `SkinSetStore`, `SkinSelection.defaultSkin()`, `SkinTexturesCache`, `PlayerTextures`, `SkinPng`, `SkinRules`.
- Cape: `CosmeticsManager`, `CapeCollection`, `CapeTextures`, `CosmeticsV2Api`.
- Redeem: `CosmeticsManager.redeem()`; states loading/success/error/signed-out.
- Account: `CloudService.login/register/logout/link/manualSync`, `AuthState`, `CloudStore`.
- V2 actions available: `link_minecraft`, `catalog`, `inventory`, `equip`, `unequip`, `redeem`, `presence`, `lookup`. No `history`.

---

## 10. Visual QA Checklist

- [ ] No neon/glow/gradient/glass/shadow-heavy panels.
- [ ] Palette uses only approved tokens (+ status colors).
- [ ] Main menu has no sidebar; interior screens have sidebar.
- [ ] Mods never opens RKB Modules.
- [ ] Minecraft Settings opens vanilla options with correct parent.
- [ ] RKB Settings and RKB Modules are separate destinations.
- [ ] Search fields visible and non-overlapping.
- [ ] Module cards show name/description/category/state/config.
- [ ] Only real module categories appear.
- [ ] Cosmetics has exactly three tabs.
- [ ] Cape rows show equipped/owned/unavailable correctly.
- [ ] Redeem has no fake success/history.
- [ ] Account screen distinguishes offline Minecraft, online Minecraft, RKB signed out, RKB signed in, linked/unlinked.
- [ ] No Minecraft token/service-role key exposure.
- [ ] 854×480, 960×540, 1280×720: no clipped controls.
- [ ] HUD accent setting still affects HUD only.

---

## 11. Risks / Open Questions

| Item | Risk | Recommended default |
|---|---|---|
| AI image generation unavailable | User asked for generated visual; only vector concepts available | Present SVG/PNG concept boards clearly labeled as concepts, not screenshots. |
| No in-game screenshot/verification in Termux | Layout may need tuning in real Minecraft renderer | Implement with layout records and test on desktop + Android Pojav launchers. |
| Mod Menu dependency absent | Mods button cannot open Fabric Mod Menu directly | Use `FabricLoader.isModLoaded("modmenu")` + reflection bridge; show unavailable modal if missing. Add optional dependency only with approval. |
| UI font bundling | Inter may increase jar size/licensing work | Optional: bundle Inter OFL font; default to vanilla font if not approved. |
| HUD accent vs chrome accent | Current `Theme.accent()` feeds menu and some HUD elements | Split into `hudAccent` and `chromeAccent`; audit HUD classes before global change. |
| Offline cape equip policy | Ownership confirmation requires V2; offline use can be confusing | Allow equip only for capes with previously confirmed cached ownership; show "Ownership not confirmed" otherwise. Never grant ownership locally. |
| Authenticated V2 schema | Response fields may vary | Keep tolerant parsing in `CosmeticsV2Api`; do not invent missing fields. |
| In-world cape rendering / multiplayer name-tag | Not verifiable in this environment | Do not claim verified; include in post-implementation QA. |
| Touch overlays / safe areas | Pojav/launcher overlays may cover edges | Keep margins and avoid edge-critical controls; test on real Android. |

---

## 12. Preserved Functionality Checklist

- [ ] Existing module enable/disable states.
- [ ] Existing module settings and keybinds.
- [ ] Existing config files: `config/rkbclient.json`, profiles, cloud store.
- [ ] Local skin import PNG/URL, rename/delete/set model/activate/reset.
- [ ] Default Steve/Alex selection.
- [ ] Cosmetics V2 catalog/inventory/equip/unequip/redeem/presence/lookup.
- [ ] Cape ownership cache and unavailable-state behavior.
- [ ] RKB account sign in/register/sign out/link/sync.
- [ ] Friends, Profiles, HUD Editor, Waypoints, Map, commands.
- [ ] No unrelated gameplay/mechanic changes.

