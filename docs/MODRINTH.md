# Modrinth publishing guide

Nothing here publishes automatically. The GitHub workflow `publish-modrinth.yml` only runs when you start it by hand and
type `PUBLISH`.

## Before the first upload
1. `./gradlew build` succeeds and you have launched the mod in a real 1.21.11 Fabric profile (menu opens, config saves).
2. Licence: the project ships with the MIT licence (`LICENSE`), fine for original code like this. Do not copy code or
   assets from other clients (including Blaze Client) unless their licence allows it. Change the copyright name in
   `LICENSE` and `fabric.mod.json` if needed. Minecraft itself is not redistributed in the JAR.
3. Replace `assets/rkbclient/icon.png` with your final logo.

## Create the project (Modrinth website)
- Type: Mod. Loader: Fabric. Environment: Client = required, Server = unsupported.
- Game version: 1.21.11. Dependency: Fabric API (required).
- Write a clear description (copy from README), add the licence, a source link and screenshots.

## First release
- Upload `build/libs/rkbclient-<version>.jar` (not `-sources`).
- Use `docs/RELEASE_NOTES_TEMPLATE.md` for the changelog. Start with version type `beta`.
- Modrinth reviews new projects before they become public.

## Optional automation
Add secret `MODRINTH_TOKEN` and variable `MODRINTH_ID`, then run the "Publish to Modrinth" workflow manually.
Check the action's current inputs first; the version used in the workflow was not verified here.
