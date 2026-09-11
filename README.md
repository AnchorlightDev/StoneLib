<p align="center">
  <a href="https://anchorlight.dev/products/stonelib">
    <img src=".github/assets/stonelib-logo.png" alt="StoneLib" width="480">
  </a>
</p>

<p align="center">
  The shared foundation for Anchorlight's Paper plugins: commands, messages, config, storage,
  scheduling, menus, dialogs, holograms and cross-server messaging, without the boilerplate.
</p>

<p align="center">
  <a href="https://jitpack.io/#AnchorlightDev/StoneLib"><img src="https://jitpack.io/v/AnchorlightDev/StoneLib.svg" alt="JitPack"></a>
  <img src="https://img.shields.io/badge/Paper-26.2-blue" alt="Paper 26.2">
  <img src="https://img.shields.io/badge/Java-25-orange" alt="Java 25">
</p>

---

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Modules](#modules)
- [Quick start](#quick-start)
- [Guides](#guides)
  - [Messages and placeholders](#messages-and-placeholders)
  - [Displaying untrusted text safely](#displaying-untrusted-text-safely)
  - [Versioned config migration](#versioned-config-migration)
  - [MySQL storage](#mysql-storage)
  - [Cross-server messaging](#cross-server-messaging)
  - [Request/response over plugin messaging](#requestresponse-over-plugin-messaging)
  - [Regions](#regions)
  - [Migrating from ModularEnigma Requests](#migrating-from-modularenigma-requests)
- [Migrating from 1.x](#migrating-from-1x)
- [Building from source](#building-from-source)

## Requirements

| | Version |
|---|---|
| Server | Paper `26.2` |
| Java | `25` |
| LuckPerms *(optional, for `permission`)* | `5.4+` |
| Velocity *(optional, for `messaging.proxy`)* | `3.4+` |

Each module is constructed directly. There is no framework, service locator or global state, so
a plugin only pays for the modules it actually uses.

## Installation

StoneLib is published through [JitPack](https://jitpack.io/#AnchorlightDev/StoneLib). Add the
repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.AnchorlightDev</groupId>
        <artifactId>StoneLib</artifactId>
        <version>2.1.0</version>
    </dependency>
</dependencies>
```

StoneLib is a library, not a plugin, so **shade it into your jar** and relocate it along with the
libraries it brings in. Two plugins bundling different versions then cannot collide:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>dev.anchorlight.stonelib</pattern>
                        <shadedPattern>dev.anchorlight.yourplugin.libs.stonelib</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>dev.dejvokep.boostedyaml</pattern>
                        <shadedPattern>dev.anchorlight.yourplugin.libs.boostedyaml</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>com.zaxxer.hikari</pattern>
                        <shadedPattern>dev.anchorlight.yourplugin.libs.hikari</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### What ships with StoneLib

| Dependency | Scope | Notes |
|---|---|---|
| BoostedYAML | compile | Config updating and migration |
| HikariCP | compile | MySQL connection pooling |
| sqlite-jdbc | compile | `SqliteRepository` |
| paper-api | provided | Supplied by the server; manages the Adventure version |
| mysql-connector-j | provided | Supplied by the server at runtime |
| LuckPerms API | provided | Only needed if you use `permission` |
| velocity-api | provided, optional | Only needed on a Velocity proxy |

## Modules

All packages live under `dev.anchorlight.stonelib`.

| Package | What it gives you |
|---|---|
| `command` | `SubCommand` and `CommandRouter` for sub-command dispatch, permissions and tab completion. |
| `message` | `MessageService` (MiniMessage-backed `messages.yml` with positional and named placeholders), `MiniMessages`, `LegacyColorConverter` and `UntrustedText`. |
| `config` | `ConfigManager` for a single `config.yml`, `MultiConfigManager` for several files, and `ConfigUpdater` for versioned migrations. |
| `storage` | The `Repository` / `RecordCodec` contract with `YamlRepository`, `SqliteRepository` and `MySqlRepository` implementations, plus `LocationCodec`. |
| `storage.sql` | `DatabaseConfig`, `ConnectionPool` (HikariCP) and `SchemaMigrator` for ordered, run-once migrations. |
| `scheduler` | `SchedulerService`, a Bukkit scheduler wrapper that tracks its tasks so `cancelAll()` cleans up in `onDisable`. `supplyAsync` runs work off-thread and hands the result back on the main thread. |
| `cooldown` | `CooldownService`, in-memory per-player cooldowns with an optional bypass permission and a check-and-apply `tryUse`. `RateLimiter` is a Bukkit-free per-key minimum interval for guarding inbound requests. |
| `menu` | `MenuHolder` and `MenuListener`, a typed `InventoryHolder` where every menu is read-only and routed to its own click handler. `SlotLayout` places entries at pinned slots and centres the rest. |
| `dialog` | `FormDialog`, a builder over Paper's Dialog API (text fields, sliders, toggles, dropdowns), and `FormResponse`, which clamps and defaults instead of trusting the client. |
| `hologram` | `HologramService`, holograms on native Paper `TextDisplay` entities with no plugin dependency. |
| `render` | `RenderLoop`, one async loop drawing every registered `Renderable` with distance culling, instead of a task per player. |
| `loot` | `LootTable`, weighted loot read from config (`material` / `min` / `max` / `weight` / `enchantments`) for crates, drops and rewards. |
| `permission` | `PermissionService`, LuckPerms lookups with a join-time cache and self-expiring temporary grants. |
| `messaging` | `MessageBus` and `Message`, a cross-server bus over plugin messaging. `messaging.proxy.ProxyMessageRelay` is the Velocity side. |
| `messaging.request` | `PendingRequests`, request/response correlation with timeouts for one-way transports such as plugin messages. No Bukkit types, so it works on a proxy too. |
| `region` | `Cuboid`, `RegionIndex` (chunk-bucketed position lookup), `RegionTracker` (enter/exit detection per player), `SelectionManager` and `SelectionWand` for two-corner selections. |
| `block` | `SafeBlocks`, fills that only replace empty space and clears that only remove what was placed, plus `wouldOverwrite` for vetoing vanilla placements. |
| `display` | `TintPanel`, a translucent panel in any ARGB colour built from text displays, and `ArgbColours` for parsing `#RRGGBB`, `#AARRGGBB` and dye names. |
| `http` | `ApiClient`, an async JSON client that never throws, with `ConnectionHealth` and a bounded `RetryQueue` for delivery that survives an outage. `Request` / `RequestBuilder` / `Response` are a blocking request builder, API-compatible with ModularEnigma Requests. |
| `vanish` | `VanishStatus`, plugin-agnostic vanish detection via player metadata. `vanish.proxy.ProxyVanishStatus` is the Velocity side (PremiumVanish). |
| `time` | `Durations`, player-facing duration formatting in three shapes: `clock` (`12:34`, `1:02:33`), `human` and `compact`. |
| *(root)* | `ItemBuilder` for quick `ItemStack`s and `ConfigValidator` for checking config values on startup. |

## Quick start

Wire only the modules your plugin uses and delete the rest. Order matters: config before messages
and storage (they may read config values), storage before anything that loads data from it, and
holograms last since they are usually populated from loaded data.

```java
package dev.anchorlight.example;

import dev.anchorlight.stonelib.command.CommandRouter;
import dev.anchorlight.stonelib.config.ConfigManager;
import dev.anchorlight.stonelib.cooldown.CooldownService;
import dev.anchorlight.stonelib.hologram.HologramService;
import dev.anchorlight.stonelib.menu.MenuListener;
import dev.anchorlight.stonelib.message.MessageService;
import dev.anchorlight.stonelib.scheduler.SchedulerService;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {

    private ConfigManager config;
    private MessageService messages;
    private SchedulerService scheduler;
    private CooldownService cooldowns;
    private HologramService holograms;
    private CommandRouter commands;

    @Override
    public void onEnable() {
        this.config = new ConfigManager(this);
        this.messages = new MessageService(this, "messages.yml");
        this.scheduler = new SchedulerService(this);
        this.cooldowns = new CooldownService("example.cooldown.bypass");
        // this.repository = new YamlRepository<>(this, "data.yml", codec, Profile::id, UUID::fromString);
        // this.repository.load();
        this.holograms = new HologramService(this);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        this.commands = new CommandRouter(this, "example");
        // commands.register(new ReloadCommand(config, messages));
        getCommand("example").setExecutor((sender, command, label, args) -> commands.dispatch(sender, args));
        getCommand("example").setTabCompleter((sender, command, alias, args) -> commands.tabComplete(sender, args));
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        // if (repository != null) {
        //     repository.save();
        // }
    }
}
```

A sub-command only needs a name and an `execute`; permission, usage, description and tab
completion all have defaults:

```java
public final class ReloadCommand implements SubCommand {

    private final ConfigManager config;
    private final MessageService messages;

    public ReloadCommand(ConfigManager config, MessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    @Override public String getName() { return "reload"; }
    @Override public String getPermission() { return "example.reload"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        config.reload();
        messages.reload();
        messages.send(sender, "reloaded");
    }
}
```

For plugins with more moving parts, keep `onEnable` / `onDisable` as a short, ordered checklist and
push the construction into private `initializeX()` helpers.

## Guides

### Messages and placeholders

`MessageService.get(key, ...)` uses positional `{0}`, `{1}` placeholders. That is fine for short
messages, but it gets hard to author and re-order once a template carries three or four values.
For those, use the named-placeholder methods, where the template reads the way the message does:

```yaml
# messages.yml
payout: "<green><player></green> earned <gold><points></gold> (balance <balance>)"
```

```java
messages.sendNamed(player, "payout", "player", name, "points", 12, "balance", balance);
```

| Method | Prefix | For |
|---|---|---|
| `get` / `send` | yes | chat messages with positional placeholders |
| `getNamed` / `sendNamed` | yes | chat messages with named placeholders |
| `getNamedUnprefixed` / `sendNamedActionBar` | no | action bars, titles, sidebars |
| `showTitle` | no | a title built from two message keys |
| `raw(key)` | no | the unparsed template, for parsing later |
| `has(key)` | n/a | messages that are optional by design |

`MiniMessages.parse(template, "key", value, ...)` and `MiniMessages.plain(...)` do the same
substitution for strings that are not message keys at all: scoreboard lines, item names, log output.

### Displaying untrusted text safely

Both placeholder styles substitute values through MiniMessage's `Placeholder.unparsed(...)`, so a
placeholder value can never inject live markup (click and hover events, colours) into a message.
Only the plugin-authored template is ever parsed.

That protects the substitution step. If you build a MiniMessage string yourself by concatenating
untrusted text (a player's typed input, a name from an external API), sanitize it first:

```java
String safeName = UntrustedText.forDisplay(externalApiResponse.displayName(), 40);
Component msg = MiniMessage.miniMessage().deserialize("<yellow>" + safeName + " joined!");
```

`UntrustedText` neutralizes every `<` rather than pattern-matching specific tag names, so an
unrecognised tag cannot slip through, and it strips the legacy `§` marker so old-style colour and
obfuscation codes cannot be applied either. The escaping order matters (escaping `<` before an
existing `\` is itself bypassable); see its Javadoc for details.

### Versioned config migration

`ConfigUpdater` brings a server's config file up to date with the copy bundled in the jar, then
returns it as an ordinary Bukkit `FileConfiguration`.

Copying a resource only when the file is missing never updates an existing file, and merging in
missing keys still cannot **rename** or **remove** one. Put a `config-version` at the top of the
bundled resource and bump it whenever you restructure the file. BoostedYAML then applies the
relocations you declare for every version in between:

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

With no relocations to declare, `ConfigUpdater.update(plugin, "config.yml")` is enough. It turns on
versioning automatically when the bundled resource declares `config-version`.

- **Adoption is per file.** A resource without `config-version` falls back to a plain merge of
  missing keys. Add the key when you are ready and that file starts being versioned.
- **Reading vs writing.** The update runs through BoostedYAML, which preserves comments and key
  order. To write values back without losing comments, take the `YamlDocument` from
  `ConfigUpdater.document(...)` and save through that.
- `ConfigManager` and `MultiConfigManager` both update through `ConfigUpdater` on reload.
  `CopyResources.mirror` still works but is deprecated in favour of `ConfigUpdater`.

### MySQL storage

`storage.sql` covers the connection pool and schema; `MySqlRepository` implements the same
`Repository` contract as the YAML and SQLite backends, so call sites do not change.

```yaml
# config.yml
database:
  host: 127.0.0.1
  port: 3306
  name: example
  user: example
  password: "..."
  pool-size: 10
```

```java
ConnectionPool pool = new ConnectionPool(
        DatabaseConfig.fromSection(getConfig().getConfigurationSection("database")), "Example");

new SchemaMigrator(pool, "example")
        .migration(1, "CREATE TABLE IF NOT EXISTS example_profiles (id VARCHAR(36) PRIMARY KEY, data TEXT)")
        .migration(2, "ALTER TABLE example_profiles ADD COLUMN updated_at BIGINT")
        .migrate();
```

Each migration runs once, in order, and is recorded against its owner, so several plugins can
share a database. Close the pool in `onDisable`.

### Cross-server messaging

`MessageBus` is the fast path for telling other backends that something changed. It is not storage.

Plugin messages ride a player's connection, so a message is dropped when no player is online to
carry it, and the proxy can only reach a backend that has one. Delivery is best-effort by design:
keep the database as the source of truth and let a message only ever prompt a re-read. A missed
message then costs a stale cache until the next read, not lost state.

```java
// backend
MessageBus bus = new MessageBus(plugin, "example:sync", config.getString("server-id"));
bus.subscribe("profile-changed", message -> repository.reload(UUID.fromString(message.payload())));
bus.register();
bus.publish("profile-changed", uuid.toString());
```

```java
// Velocity proxy
new ProxyMessageRelay(proxy, logger, "example:sync").register(this);
```

The bus stamps every outgoing message with the sending server's id and ignores messages carrying
its own, so a change never bounces back to the server that made it.

### Request/response over plugin messaging

`PendingRequests` gives a one-way transport replies. Put the request id in your message, send it,
and complete the id when the answer arrives. Unanswered requests fail with a timeout.

```java
PendingRequests<BridgeMessage> pending = new PendingRequests<>(Duration.ofMillis(1500));

CompletableFuture<BridgeMessage> reply = pending.send(id ->
        player.sendPluginMessage(plugin, "example:bridge", codec.encode(new ServerListRequest(id))));

// PluginMessageListener
BridgeMessage message = codec.decode(bytes);
pending.complete(message.requestId(), message);
```

### Regions

```java
RegionIndex<Arena> arenas = new RegionIndex<>(Arena::bounds);
arenas.rebuild(arenaService.all());
RegionTracker<Arena> tracker = new RegionTracker<>(arenas, Arena::id);

// PlayerMoveEvent, on block change
tracker.update(player.getUniqueId(), world, x, y, z).entered().ifPresent(arena -> arena.join(player));
```

Rebuild the index whenever the set of regions changes, and clear the tracker on quit.

### Migrating from ModularEnigma Requests

`Request`, `RequestBuilder` and `Response` are ported from
[ModularSoftAU/Requests](https://github.com/ModularSoftAU/Requests) with the same API. Drop the
`io.github.ModularEnigma:Requests` dependency and change the imports:

```java
import dev.anchorlight.stonelib.http.Request;   // was io.github.ModularEnigma.Request
import dev.anchorlight.stonelib.http.Response;  // was io.github.ModularEnigma.Response

Response response = Request.builder()
        .setURL(baseUrl + "/api/user/create")
        .setMethod(Request.Method.POST)
        .addHeader("x-access-token", token)
        .setRequestBody(json)
        .build()
        .execute();
```

`execute()` blocks, so keep calling it off the main thread, or use `executeAsync()`. Changes from
1.0.x: a POST without a body sends an empty body instead of throwing, headers you add replace the
JSON `Accept`/`Content-Type` defaults instead of duplicating them, exceptions keep their cause, and
`Method` gains `PUT`, `PATCH` and `DELETE`. If you shade StoneLib with an include filter, add
`dev/anchorlight/stonelib/http/Re*` (or `http/**`).

## Migrating from 1.x

2.0.0 contains breaking changes:

- **Package rename.** `dev.anchorlight.StoneLib.*` is now `dev.anchorlight.stonelib.*`. Update your
  imports and any shade relocation patterns.
- **Coordinates.** The JitPack group is now `com.github.AnchorlightDev`.
- **Platform.** StoneLib targets Paper 26.2 and requires Java 25.

## Building from source

Paper 26.2 ships Java 25 class files, so an older JDK cannot read `paper-api` and fails with
`cannot access org.bukkit.*` on every import. Build with JDK 25 (`jitpack.yml` pins `openjdk25`):

```shell
mvn clean package
```

This produces `target/StoneLib-2.1.0.jar` and a sources jar, and runs the test suite.

- `paper-api` versions carry a `-stable` qualifier, so a Maven range like `[26.2.build,)` resolves
  to nothing. Pin an exact version.
- Adventure is deliberately **not** pinned. `paper-api` manages it through `adventure-bom`, and
  pinning an older version breaks the Dialog API, which needs a newer `DialogLike`.

---

<p align="center">
  Made by <a href="https://anchorlight.dev">Anchorlight</a>
</p>
