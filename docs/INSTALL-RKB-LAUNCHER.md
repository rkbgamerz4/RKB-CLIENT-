# Installing RKB CLIENT in RKB LAUNCHER

RKB CLIENT is a normal Fabric mod JAR. It does not touch launcher internals, so the launcher only needs a working
Fabric 1.21.11 profile.

## Compatibility checklist
| Item | Needed | Where to check |
|---|---|---|
| Minecraft | 1.21.11 | Profile version |
| Fabric Loader | 0.18.2 or newer | Fabric profile name, e.g. `fabric-loader-0.18.x-1.21.11` |
| Fabric API | 0.141.1+1.21.11 (any 1.21.11 build) | `mods` folder |
| Java | 21 | Launcher runtime (`.../runtime/java21/bin/java`) |

## Steps
1. Build the JAR (see README) and copy `build/libs/rkbclient-0.1.1.jar` to your phone. Do **not** use the `-sources` JAR.
2. Install Fabric for 1.21.11 in the launcher (create a Fabric profile). If the launcher has no Fabric installer yet,
   install it with the official Fabric installer on a PC and copy the resulting version folder into the launcher's
   `versions` directory.
3. Find the game directory used by that profile and open (or create) its `mods` folder.
4. Put these two files in `mods`: `rkbclient-0.1.1.jar` and the Fabric API JAR for 1.21.11 (download from Modrinth).
5. Select the Fabric 1.21.11 profile in RKB LAUNCHER and press Play.
6. In game, press **Right Shift** or type **/rkb** in chat to open the menu. On Android, `/rkb` is the easiest route;
   alternatively map a touch button to Right Shift in the launcher's controls, if it has that option.

## Troubleshooting
- Crash on start with "requires fabric-api": the Fabric API JAR is missing from `mods`.
- "Incompatible mod set": the profile is not 1.21.11, or Fabric Loader is older than 0.18.2.
- Menu opens but nothing is clickable: the launcher must pass the touch as a left mouse button; drag in the list to scroll.
- Config is at `config/rkbclient.json` inside the game directory. Delete it to reset; a corrupt file is renamed to
  `rkbclient.json.broken` automatically.

No change to launcher source files is needed. If you later want an "Install RKB CLIENT" button in the launcher, the
smallest change is copying the JAR into the active profile's `mods` folder; share the relevant launcher files first.
