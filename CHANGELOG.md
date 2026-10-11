# Changelog

All notable changes to StoneLib are documented here. This project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html), and the format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [2.3.2] - 2026-10-11

Build/release fix only; no API or runtime changes from 2.3.0.

### Fixed

- JitPack builds failed to produce a resolvable artifact. Two causes: no Maven wrapper existed for
  JitPack to invoke (`./mvnw` → exit 127), and JitPack could not provision a Java 25 JDK (its
  `openjdk25` token points at an Oracle build removed from SDKMAN, and its bundled SDKMAN cannot
  download any Java 25 candidate). `jitpack.yml` now downloads an Eclipse Temurin 25 tarball
  directly from Adoptium and builds with a committed Maven wrapper, so
  `com.github.AnchorlightDev:StoneLib:v2.3.2` resolves from JitPack. Verified green on JitPack.

## [2.3.1] - 2026-10-11

Superseded by 2.3.2 — this tag's JitPack build does not resolve (JDK 25 provisioning fix was
incomplete). Use 2.3.2.

## [2.3.0] - 2026-10-11

Everything in this release is additive; nothing breaks. Code shared by PixelPost and Gracewave
moved into StoneLib.

### Added

- New `map` module: `ImageProcessor`, `ImageFit`, `TileSlicer`, `MapImages`, `RevisionMapRenderer`,
  and `MapPool` with `MapIdStore`, plus `MapScreen` for wall-mounted or floating map screens.
- New `entity.EntityTags` and `file.SafeFiles`.
- `display.DisplayAnimator` with grow and shrink animations, and `display.ActionBarChannel`, an
  action bar that does not fade.
- `http.SafeDownloader` and `http.UrlSafety`.

### Changed

- `http.UrlSafety` now also blocks `0.0.0.0/8` and the carrier-grade NAT range `100.64.0.0/10`,
  which PixelPost's copy previously allowed.

### Notes

- If you shade StoneLib with an include filter, add the new packages you use (`map/**`,
  `entity/**`, `file/**`; `display/**` and `http/**` gained classes).

## [2.2.0] - 2026-09-29

Everything in this release is additive; nothing breaks.

### Added

- `scheduler.PlatformScheduler`: Folia-safe scheduling.
- `config.VersionedConfig`: never overwrites a broken file, and `set` preserves comments.
- New modules: `combat.CombatTagService`, `ownership.*`, `invite.InviteRegistry`,
  `yaml.DebouncedFileWriter`, `update.*`, `bedrock.*` and `message.Buttons`.
- `MessageService`: `MessageService(Supplier<ConfigurationSection>)` reads messages kept in a
  section of another file; `legacyPercentPlaceholders(true)` accepts `%name%` templates; `prefix()`
  and `prefixed(Component)` expose the chat prefix.
- `CommandRouter`: `feedback(...)`, `onNoArguments(...)` and `visibleNames(...)`, plus
  `SubCommand.playerOnly()`. Tab completion never returns null.
- `FormDialog`: `prompt(...)` for button dialogs, and every `FormDialog` now sends Bedrock players a
  native form automatically.

### Changed

- `ConfigUpdater`: unversioned server files are migrated as version 1, and the migrated version is
  written as a number rather than the string `'2'`.

### Fixed

- `SchedulerService` no longer keeps finished tasks until `cancelAll()`. One-shots untrack
  themselves after running, so a busy server no longer grows toward `OutOfMemoryError`. `supplyAsync`
  skips its callback if the plugin was disabled meanwhile.
- `CooldownService` sweeps expired entries, so players who never return don't accumulate.
- `FormDialog` callbacks expire after 30 minutes instead of Paper's 12-hour default.

## [2.1.0] - 2026-09-26

See the [v2.1.0 release](https://github.com/AnchorlightDev/StoneLib/releases/tag/v2.1.0).

## [2.0.0] - 2026-09-11

Major release with breaking changes.

### Changed

- **Package rename.** `dev.anchorlight.StoneLib.*` is now `dev.anchorlight.stonelib.*`. Update your
  imports and any shade relocation patterns.
- **Coordinates.** The JitPack group is now `com.github.AnchorlightDev`.
- **Platform.** StoneLib builds against Paper 26.2 but emits Java 21 bytecode, so it runs on any JVM
  from 21 up.

[2.3.0]: https://github.com/AnchorlightDev/StoneLib/releases/tag/v2.3.0
[2.2.0]: https://github.com/AnchorlightDev/StoneLib/releases/tag/v2.2.0
[2.1.0]: https://github.com/AnchorlightDev/StoneLib/releases/tag/v2.1.0
[2.0.0]: https://github.com/AnchorlightDev/StoneLib/releases/tag/v2.0.0
