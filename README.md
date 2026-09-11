For code integration examples view:\
    - https://github.com/ModularSoftAU/zander

Instructions:
```shell
# compile library
mvn clean package
```

```shell
# specify as dependency
pom.xml +
...
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>
...
<dependency>
    <groupId>com.github.ModularSoftAU</groupId>
    <artifactId>StoneLib</artifactId>
    <version>1.0</version>
    <scope>provided</scope>
</dependency>
...
```

## Modules

- `dev.anchorlight.stonelib.command` — `SubCommand` + `CommandRouter` for sub-command dispatch.
- `dev.anchorlight.stonelib.message` — `MessageService` (MiniMessage-based messages.yml, with both positional `{0}` and named `<key>` placeholders — see below) + `MiniMessages` (named-placeholder parsing on its own) + `LegacyColorConverter` + `UntrustedText` (sanitizes untrusted values — chat input, external API data — before they reach a MiniMessage template).
- `dev.anchorlight.stonelib.config` — `ConfigUpdater` (versioned config migration, see below), plus `ConfigManager` (single `config.yml`) and `MultiConfigManager` (several YAML files at once, for plugins with a lot of tunable surface). Both update through `ConfigUpdater` on reload.
- `dev.anchorlight.stonelib.loot` — `LootTable`, weighted loot built from a config list (`material`/`min`/`max`/`weight`/`enchantments`) for crates, drops and rewards.
- `dev.anchorlight.stonelib.storage` — `Repository`/`RecordCodec` with `YamlRepository` and `SqliteRepository` implementations, plus `LocationCodec`.
- `dev.anchorlight.stonelib.scheduler` — `SchedulerService`, a Bukkit scheduler wrapper that tracks every task it creates so `cancelAll()` in `onDisable` cleans them all up; `supplyAsync` runs work off-thread and returns the result on the main thread.
- `dev.anchorlight.stonelib.cooldown` — `CooldownService`, in-memory `UUID -> key -> expiry` cooldowns with an optional bypass permission and a check-and-apply `tryUse`.
- `dev.anchorlight.stonelib.menu` — `MenuHolder` + `MenuListener`, a typed `InventoryHolder` for plugin menus. Register the listener once and every menu is read-only and routed to its own click handler.
- `dev.anchorlight.stonelib.time` — `Durations`, player-facing duration formatting in three deliberately distinct shapes (`clock`, `human`, `compact`).
- `dev.anchorlight.stonelib.hologram` — `HologramService` using native Paper `TextDisplay` entities (no external plugin dependency).
- `dev.anchorlight.stonelib.dialog` — `FormDialog`, a builder over Paper's Dialog API for text fields, sliders, toggles and dropdowns, plus `FormResponse`, which clamps and defaults rather than trusting what the client sent.
- `dev.anchorlight.stonelib.storage.sql` — `DatabaseConfig`, `ConnectionPool` (HikariCP) and `SchemaMigrator` (ordered, once-only migrations), with `storage.MySqlRepository` implementing the usual `Repository` contract on MySQL.
- `dev.anchorlight.stonelib.messaging` — `MessageBus` + `Message`, a cross-server message bus over plugin messaging, and `messaging.proxy.ProxyMessageRelay`, the Velocity side that fans a message out to the other backends.
- `dev.anchorlight.stonelib.render` — `RenderLoop`, one async loop drawing every registered `Renderable` with distance culling, instead of a task per player.
- `dev.anchorlight.stonelib.permission` — `PermissionService`, LuckPerms lookups with a join-time cache and self-expiring temporary grants.

## Build requirements

StoneLib builds against the current Paper release, which ships **Java 25** class files — an older
JDK cannot read `paper-api` at all, and fails with `cannot access org.bukkit.*` on every import.
Build with JDK 25 (`jitpack.yml` pins `openjdk25`).

Note that `paper-api` versions carry a `-stable` qualifier, so a Maven range like `[26.2.build,)`
resolves to nothing. Pin an exact version.

Adventure is deliberately **not** pinned here — `paper-api` manages it through `adventure-bom`, and
pinning an older version breaks the Dialog API, which needs a newer `DialogLike`.

## Cross-server messaging

`MessageBus` is the fast path for telling other backends that something changed. It is not storage.

Plugin messages ride a player's connection, so a message is dropped when no player is online to
carry it, and the proxy can only reach a backend that has one. Delivery is best-effort by design:
keep the database as the source of truth and let a message only ever prompt a re-read. A missed
message then costs a stale cache until the next read, not lost state.

```java
// backend
MessageBus bus = new MessageBus(plugin, "edeneffects:sync", config.getString("server-id"));
bus.subscribe("cosmetic-changed", message -> repository.reload(UUID.fromString(message.payload())));
bus.register();
bus.publish("cosmetic-changed", uuid.toString());
```

```java
// Velocity proxy
new ProxyMessageRelay(proxy, logger, "edeneffects:sync").register(this);
```

The bus stamps every outgoing message with the sending server's id and ignores messages carrying
its own, so a change never bounces back to the server that made it.

## Versioned config migration

`ConfigUpdater` brings a server's config file up to date with the copy bundled in the jar, then
returns it as an ordinary Bukkit `FileConfiguration`.

Copying the resource only when the file is missing never updates an existing file, so a plugin
update that adds a key silently reads a default the server's file does not have. Merging in missing
keys fixes that, but it still cannot **rename** or **remove** a key — once a setting is renamed,
every existing server keeps the old name forever and the plugin has to read both.

Put a `config-version` at the top of the bundled resource and bump it whenever you restructure the
file. BoostedYAML then applies the relocations you declare for the versions in between:

```yaml
# config.yml, in the jar
config-version: 2
```

```java
// "old.path" became "new.path" in version 2
ConfigUpdater.update(plugin, "config.yml", UpdaterSettings.builder()
        .setVersioning(new BasicVersioning(ConfigUpdater.VERSION_ROUTE))
        .addRelocation("2", Route.fromString("old.path"), Route.fromString("new.path"))
        .build());
```

With no relocations to declare, `ConfigUpdater.update(plugin, "config.yml")` is enough — it picks up
versioning automatically when the bundled resource declares `config-version`.

**Adopting it is per-file.** BoostedYAML throws if versioning is on and the defaults carry no
version ID, so a resource without `config-version` falls back to a plain merge — exactly the old
behaviour. Add the key to a file when you are ready and that file starts being versioned.

**Reading vs writing.** The update runs on disk through BoostedYAML, which preserves comments and
key order; what comes back is a Bukkit `FileConfiguration`, so call sites keep using `getInt` and
`getConfigurationSection`. To *write* values back without destroying comments, take the
`YamlDocument` from `ConfigUpdater.document(...)` and save through that instead.

**Shading.** BoostedYAML arrives transitively with StoneLib. Relocate it in your shade config so two
plugins bundling different versions cannot collide:

```xml
<relocation>
  <pattern>dev.dejvokep.boostedyaml</pattern>
  <shadedPattern>dev.anchorlight.yourplugin.libs.boostedyaml</shadedPattern>
</relocation>
```

`CopyResources.mirror` still works and now delegates here, so existing callers gain versioning as
soon as their resource declares a version. It is deprecated in favour of `ConfigUpdater`.

## Positional vs named placeholders

`MessageService.get(key, ...)` uses positional `{0}`, `{1}` placeholders. That is fine for short
messages, but it gets hard to author and to re-order once a template carries three or four values,
and the call site stops being readable at a glance.

For those, use the named-placeholder methods, where the template reads the way the message does:

```yaml
# messages.yml
payout: "<green><player></green> earned <gold><points></gold> (balance <balance>)"
```

```java
messages.sendNamed(player, "payout", "player", name, "points", 12, "balance", balance);
```

Both forms substitute values via MiniMessage's `Placeholder.unparsed(...)`, so neither can inject
live markup from a placeholder value.

| Method | Prefix | For |
|---|---|---|
| `getNamed` / `sendNamed` | yes | chat messages |
| `getNamedUnprefixed` / `sendNamedActionBar` | no | action bars, titles, sidebars |
| `showTitle` | no | a title built from two message keys |
| `raw(key)` | no | the unparsed template, for parsing later |
| `has(key)` | — | messages that are optional by design |

`MiniMessages.parse(template, "key", value, ...)` and `MiniMessages.plain(...)` do the same
substitution for strings that are not message keys at all — scoreboard lines, item names, log output.

## Displaying untrusted text safely

`MessageService.get(key, placeholders...)` substitutes `{0}`, `{1}`, ... placeholder values using
MiniMessage's `Placeholder.unparsed(...)` mechanism, so a placeholder value can never inject live
MiniMessage markup (click/hover events, colors, etc.) into the rendered message — only the
plugin-authored template itself (from `messages.yml`) is ever parsed as markup.

That protects the *substitution* step, but if you're building a MiniMessage string yourself by
concatenating untrusted text directly (a player's typed input, a name pulled from an external API,
anything not authored by your own plugin) instead of going through `MessageService`, sanitize it
first with `UntrustedText.forDisplay(raw, maxLength)`:

```java
import dev.anchorlight.stonelib.message.UntrustedText;

String safeName = UntrustedText.forDisplay(externalApiResponse.displayName(), 40);
Component msg = MiniMessage.miniMessage().deserialize("<yellow>" + safeName + " joined!");
```

`UntrustedText` neutralizes every `<` (the character MiniMessage needs to open a tag) rather than
pattern-matching for specific tag names, so it can't be bypassed by a tag it doesn't recognize, and
it strips the legacy `§` formatting-code marker so untrusted text can't apply obfuscation/color via
old-style chat codes either. See its Javadoc for the exact escaping order — it matters (a naive
implementation that escapes `<` before an existing `\` is itself bypassable).

## Plugin onEnable / onDisable Template

Each module is constructed directly — no framework, no service locator. Wire only the
modules your plugin actually uses; delete the rest. Order matters: config before
messages/storage (they may read config values), storage before anything that loads
data from it, holograms last since they're usually populated from loaded data.

```java
package dev.anchorlight.example;

import dev.anchorlight.stonelib.command.CommandRouter;
import dev.anchorlight.stonelib.config.ConfigManager;
import dev.anchorlight.stonelib.hologram.HologramService;
import dev.anchorlight.stonelib.message.MessageService;
import dev.anchorlight.stonelib.storage.YamlRepository;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private CommandRouter commandRouter;
    private YamlRepository<?, ?> repository; // swap in your own K/V types
    private HologramService hologramService;

    @Override
    public void onEnable() {
        getLogger().info("Enabling " + getDescription().getName() + " v" + getDescription().getVersion() + "...");

        this.configManager = new ConfigManager(this);
        this.messageService = new MessageService(this, "messages.yml");
        // this.repository = new YamlRepository<>(this, "data.yml", codec, keyExtractor, keyParser);
        // this.repository.load();
        this.hologramService = new HologramService(this);

        this.commandRouter = new CommandRouter(this, "example");
        // commandRouter.register(new YourSubCommand(...));
        getCommand("example").setExecutor((sender, command, label, args) -> commandRouter.dispatch(sender, args));
        getCommand("example").setTabCompleter((sender, command, alias, args) -> commandRouter.tabComplete(sender, args));

        getLogger().info(getDescription().getName() + " enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling " + getDescription().getName() + "...");

        // if (repository != null) {
        //     repository.save();
        // }

        getLogger().info(getDescription().getName() + " disabled successfully.");
    }
}
```

For plugins with more moving parts (multiple managers, listeners), keep `onEnable`/
`onDisable` as a short, ordered checklist and push the actual construction into
private `initializeX()` helpers — see `MineMail`'s `onEnable` for that pattern applied
at scale.
