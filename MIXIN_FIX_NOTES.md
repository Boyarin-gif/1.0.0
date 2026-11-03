# Plus Visual 5.2.3 — Friend armor tint Mixin fix

## What changed
- Reworked `FriendArmorTintMixin` from `@ModifyArg` to `@ModifyArgs`.
- The callback now receives the actual `submitModel` argument bundle and the
  enclosing `EquipmentRenderer.render` arguments.
- The selected armor tint is written to `submitModel` argument index 6
  (`tintedColor`) only when `FriendManager.armorTintFor(...)` returns a
  non-zero tint.
- Kept `FriendArmorTintMixin` registered in `src/main/resources/pvpplus.mixins.json`.
- Bumped `mod_version` from 5.2.2 to 5.2.3.

## Validation status
- Source structure and JSON syntax were inspected.
- The `submitModel` argument order was checked against Yarn 1.21.11 documentation.
- A full Gradle build and in-game launch have **not** been run in this environment
  because Gradle and a Gradle wrapper are not present here.

Run `gradle build --no-daemon` in the project directory, then test friend armor
highlighting in Minecraft 1.21.11. If another Mixin error appears, provide the
new `latest.log`.
