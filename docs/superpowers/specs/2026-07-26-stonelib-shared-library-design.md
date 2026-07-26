# StoneLib Shared Library — Design

## Problem

Across the ModularSoftAU / anchorlight Paper plugin projects (BuildArena, Blueprint, EdenEffects,
MineMail, Ascendra, TutorialBuddy, Riftfall, VoidColosseum, CryptGrid, Gracewave, and others),
the same handful of concerns are reimplemented per-plugin, each with small incompatible variations:

- **Sub-command dispatch** — a `SubCommand` interface (name/usage/permission/execute/tabComplete)
  duplicated in at least Blueprint, BuildArena, EdenEffects.
- **Message/config loading** — a `ConfigManager`/`MessageUtil` that loads a `messages.yml`, applies
  a prefix, and does placeholder substitution. Split between legacy `&`-color-code style
  (BuildArena, MineMail) and Adventure/MiniMessage style (TutorialBuddy).
- **Holograms** — duplicated in Ascendra, CryptGrid, Gracewave, MineMail. Three of the four wrap
  the DecentHolograms plugin via a soft-dependency + reflection detection routine that is itself
  duplicated near-verbatim across those three. Gracewave instead uses Paper's native `TextDisplay`
  entities with no external dependency.
- **YAML/SQLite persistence** — a `StorageManager` duplicated in MineMail, Riftfall, TutorialBuddy,
  each hand-rolling an async load/save + in-memory cache pattern.
- **Location (de)serialization** — a `LocationUtil` duplicated in BuildArena and VoidColosseum,
  using two different, incompatible string formats.

StoneLib already exists in this GitHub directory as a PaperMC library
(currently `org.modularsoft:StoneLib`, published via JitPack, referenced from the `zander`
project's README) — it is the intended home for this shared code. It currently contains three
unrelated utility classes under package `org.modularsoft.StoneLib`: `ConfigValidator` (typed field
validation with fallback for `FileConfiguration`), `CopyResources` (mirrors a bundled default
resource file into the plugin data folder, filling in missing keys via `setDefaults`), and
`ItemBuilder` (fluent `ItemStack` builder). As part of this work, both the Maven `groupId` and the
package root move to `dev.anchorlight.StoneLib` (capital S/L) to match the naming convention used
by current plugins — BuildArena, Blueprint, MineMail, EdenEffects, etc. all use `dev.anchorlight.*`
— while `org.modularsoft` is legacy. The three existing classes move with it unchanged in behavior,
and the new modules are added alongside them under the new package root.

## Goals

Build out StoneLib v1 to absorb the five duplicated concerns above into a single reusable,
documented library that new and existing plugins depend on instead of reimplementing.

## Non-goals

- Not attempting to unify every utility class across all ~25 GitHub plugin projects — only the
  five concerns identified above, which are the clearly duplicated ones found during the audit.
- Not migrating every existing plugin to StoneLib as part of this work. One plugin will be
  migrated as a live integration check (see Testing); the rest migrate opportunistically later.
- Not adding new gameplay features — this is a pure extraction/refactor of existing duplicated
  logic into a shared, generalized form.

## Architecture

StoneLib remains a single Maven artifact (as already scaffolded), moved to the `dev.anchorlight`
groupId/package root and organized into five independent sub-packages there, alongside the moved
`ConfigValidator`, `CopyResources`, and `ItemBuilder` classes. Consuming plugins construct the services they need in
`onEnable`, passing their own `JavaPlugin` instance — matching the existing
`new ConfigManager(plugin)` call-site pattern already used everywhere, so migrating a plugin is
largely: add the StoneLib dependency, swap the import, delete the duplicated class, adjust
call-sites where the API name differs.

## Components

### `dev.anchorlight.StoneLib.command`

- `SubCommand` interface: `getName()` (required), `getPermission()`, `getUsage()`, `getDescription()`
  as defaults, `execute(CommandSender, String[])` (required), `tabComplete(CommandSender, String[])`
  (default: empty list).
- `CommandRouter`: registered against a root command, holds a map of `SubCommand` by name, handles
  unknown-subcommand messaging, permission checks before `execute`, and delegates tab-completion.
  Plugins implement Bukkit's `CommandExecutor`/`TabCompleter` by delegating to a `CommandRouter`
  instance rather than hand-rolling dispatch.

### `dev.anchorlight.StoneLib.message`

- `MessageService(JavaPlugin plugin, String fileName)`: loads a `messages.yml`-style file (or a
  caller-specified filename), copies the bundled default from `src/main/resources` on first run
  (matching current `saveResource` behavior), and exposes `reload()`.
- Resolves message strings through Adventure `MiniMessage`, supporting both `<tag>` MiniMessage
  syntax and legacy `%placeholder%` substitution in the same call (TutorialBuddy's dual-syntax
  approach), since existing message YAMLs in the audited plugins use both forms.
- API: `send(CommandSender, key, placeholders...)`, `sendActionBar(Player, key, placeholders...)`,
  `sendTitle(Player, key, subKey, fadeIn, stay, fadeOut, placeholders...)`, `get(key, placeholders...)`
  returning a `Component`.
- `LegacyColorConverter` utility to convert existing `&`-coded message files to MiniMessage input,
  so plugins with old message YAMLs keep working without hand-editing every string.

### `dev.anchorlight.StoneLib.config`

- `ConfigManager` base helper wrapping `plugin.getConfig()`: `reload()`, typed getter passthroughs
  (`getInt`, `getBoolean`, `getString`, etc., matching `FileConfiguration`'s surface so existing
  per-plugin subclasses need minimal changes to their own typed accessor methods).
- The "add missing keys on upgrade" need (seen hand-rolled in Ascendra as `ConfigUpdater`) is
  already solved by the existing `CopyResources.mirror(plugin, filepath)` utility, which loads the
  bundled default alongside the on-disk file and calls `setDefaults` + `copyDefaults(true)` before
  saving — no new class needed here. `ConfigManager.reload()` calls `CopyResources.mirror(plugin,
  "config.yml")` before re-reading, so every consumer gets the merge behavior for free.

### `dev.anchorlight.StoneLib.storage`

- `Repository<K, V>` interface: `load()`, `save()`, `get(K)`, `put(K, V)`, `remove(K)`, `all()`,
  each plugin supplies (de)serialization via a small `RecordCodec<V>` functional interface
  (`Map<String,Object> toMap(V)` / `V fromMap(Map<String,Object>)`).
- `YamlRepository<K, V>` implementation: async load/save via the Bukkit scheduler onto a
  `ConcurrentHashMap` cache, auto-save on an interval (matching MineMail's `startAutoSave` pattern),
  file created under the plugin's data folder.
- `SqliteRepository<K, V>` implementation: same interface, backed by a JDBC SQLite connection,
  single table keyed by a string form of `K`, values stored as serialized columns per the codec.
- `LocationCodec`: single canonical (de)serialization format for `Location`
  (`world:x:y:z:yaw:pitch`, the more complete of the two existing formats — BuildArena's, which
  includes yaw/pitch, versus VoidColosseum's comma-joined block-coordinate-only format), replacing
  both duplicated `LocationUtil` classes.

### `dev.anchorlight.StoneLib.hologram`

- `HologramService(JavaPlugin plugin)`: creates/updates/removes floating text using native Paper
  `TextDisplay` entities (Gracewave's approach) tagged with a `NamespacedKey` so managed entities
  can be identified and purged on restart without external state. No external plugin dependency,
  replacing the DecentHolograms wrapper + reflection-based soft-dependency detection duplicated in
  Ascendra, CryptGrid, and MineMail.
- API: `create(id, Location, List<String> lines)`, `update(id, List<String> lines)`, `remove(id)`,
  `restoreAll(Map<id, HologramSpec>)` for startup recreation, with line templates resolved through
  the same placeholder mechanism as `message`.

## Data Flow (typical consumer)

1. Plugin `onEnable` constructs `ConfigManager`, `MessageService`, a `Repository` implementation,
   and `HologramService`, each wrapping the plugin instance.
2. `CommandRouter` is built and subcommands registered; each subcommand calls into plugin-specific
   managers/services, using `MessageService` for all player-facing text.
3. `Repository` loads persisted state asynchronously on startup; `HologramService.restoreAll` runs
   afterward on the main thread once state is loaded (matching MineMail's existing ordering).

## Error Handling

- `MessageService`: a missing key returns a visible placeholder (`[Missing message: key]`) rather
  than throwing, matching current plugin behavior.
- `ConfigUpdater`: malformed or unreadable default resource logs a warning and leaves the on-disk
  config untouched (no partial writes).
- `Repository`: I/O failures during save are logged at `SEVERE` with the exception; save failures
  do not crash the calling plugin. Load failures for an individual record are logged at `WARNING`
  and that record is skipped rather than aborting the whole load.
- `HologramService`: entity spawn/removal failures (e.g. world unloaded) are logged at `WARNING`
  and skipped; no exception propagates to the caller.

## Testing

- `command` and `message` modules get unit tests via MockBukkit (no real server needed).
- `storage` and `hologram` are harder to unit test against a real Paper server. Plan: migrate one
  real plugin onto StoneLib (candidate: MineMail, since it already uses all five concerns — YAML
  storage, DecentHolograms-style holograms, legacy messages, config, and no complex subcommand
  tree) as a live integration check before v1 is considered done. Remaining plugins migrate
  opportunistically afterward, not as part of this work.

## Migration Notes (non-binding, for future work)

Plugins currently on DecentHolograms-backed holograms (Ascendra, CryptGrid, MineMail) will need to
drop the `DecentHolograms` soft-dependency from `plugin.yml` when they migrate to
`HologramService`, since it becomes unnecessary.
