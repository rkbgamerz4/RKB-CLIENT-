# Changed files, 0.7.0 to 0.8.0

- modified: build.gradle
- modified: gradle.properties
- modified: src/main/java/com/rkb/client/RkbClient.java
- modified: src/main/java/com/rkb/client/RkbCommands.java
- modified: src/main/java/com/rkb/client/gui/RkbMenuScreen.java
- modified: src/main/java/com/rkb/client/module/Module.java
- modified: src/main/java/com/rkb/client/module/impl/NameTagBadgeModule.java
- modified: src/main/java/com/rkb/client/module/setting/Setting.java
- modified: src/main/resources/assets/rkbclient/lang/en_us.json
- new: src/main/java/com/rkb/client/gui/FriendsScreen.java
- new: src/main/java/com/rkb/client/cloud/BadgeRegistry.java
- new: src/main/java/com/rkb/client/cloud/CloudException.java
- new: src/main/java/com/rkb/client/cloud/CloudService.java
- new: src/main/java/com/rkb/client/cloud/CloudStore.java
- new: src/main/java/com/rkb/client/cloud/CosmeticsCatalog.java
- new: src/main/java/com/rkb/client/cloud/FriendsManager.java
- new: src/main/java/com/rkb/client/cloud/MojangLookup.java
- new: src/main/java/com/rkb/client/cloud/RateLimiter.java
- new: src/main/java/com/rkb/client/cloud/SyncMerge.java
- new: src/main/java/com/rkb/client/cloud/UuidUtil.java
- new: supabase/README.md
- new: supabase/inspect_schema.sql
- new: supabase/migrations/20261029000000_rkb_badges.sql
- new: supabase/functions/rkb-badges/index.ts
- new: src/test/java/com/rkb/client/cloud/CloudLogicTest.java

Not touched: every other module, the HUD, the logo glyph, minimap, waypoints, the mixins, the Gradle versions.
