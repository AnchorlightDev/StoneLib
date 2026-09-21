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
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
</p>

---

## Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Modules](#modules)
- [Quick start](#quick-start)
- [Guides](#guides)
  - [Messages and placeholders](#messages-and-placeholders)
  - [Item sprites in messages](#item-sprites-in-messages)
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
| Java to **run** | `21+` |
| JDK to **build** | `25` |
| LuckPerms *(optional, for `permission`)* | `5.4+` |
| Velocity *(optional, for `messaging.proxy`)* | `3.4+` |

Those two Java rows are different questions and both matter. StoneLib is compiled with
`<release>21</release>`, so the classes it emits run on a Java 21 server. Building it needs JDK 25
only because `paper-api` 26.2 is itself Java 25 and javac has to be able to *read* it.

**Do not raise the release target.** StoneLib is shaded into its consumers, and shading copies
class files verbatim rather than recompiling them - so a jar can only run on a JVM new enough for
its newest bundled class. Emitting 25 here forces every downstream plugin onto a Java 25 server
whatever they set for their own code, which shows up as an `UnsupportedClassVersionError` on first
use of any StoneLib class.

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
        <version>2.3.0</version>
    </dependency>
</dependencies>
```

StoneLib is a library, not a plugin, so **shade it into your jar** and relocate it along with the
libraries it brings in. Two plugins bundling different versions then cannot collide:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.2</version>
    <!--
        Shade rewrites every class it packages to apply relocations, so it needs an ASM that
        understands the bytecode it reads. If YOUR plugin compiles above Java 21, the ASM bundled
        with the plugin may be too old and the build fails with
        "Unsupported class file major version". Pin a newer one here if that happens.
    -->
    <dependencies>
        <dependency>
            <groupId>org.ow2.asm</groupId>
            <artifactId>asm</artifactId>
            <version>9.9.1</version>
        </dependency>
        <dependency>
            <groupId>org.ow2.asm</groupId>
            <artifactId>asm-commons</artifactId>
            <version>9.9.1</version>
        </dependency>
    </dependencies>
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
| `clock` | `EventClock`, a timed event reduced to two absolute timestamps: normalised progress `p`, a session-only simulation override for testing, and `intervalScale` for stretching content pacing across runs of different lengths. `EventWindow` parses the window out of config, including the `Date` that YAML produces for an unquoted timestamp. |
| `scaling` | `Scaling` and `ScaledTarget` for community targets that scale with population, with clamps and a progress floor so a shrinking server can never invalidate work already done. `PopulationWindow` supplies the reference count as a trailing *peak*, so logging off does not shrink a shared goal. |
| `shop` | `ShopCatalog` (categories and entries from config), `ShopView` (menus, pagination, click routing) and `ShopStock` (event-wide limited stock). The action vocabulary stays in your plugin: `ShopContext` supplies the currency, the conditions and the delivery. |
| `yaml` | `YamlStore`, a flat file of plugin state with a schema-version guard and dirty tracking. Deliberately **not** `storage`: no driver behind it, so a plugin with no database can exclude `storage` from its jar and still keep its state. |
| `world` | `Terrain`, heightmap-aware placement on generated terrain - surface and floor lookups that skip leaves, a `standable` test that rejects liquid, unstable footing and low headroom, and area-uniform sampling inside the world border. |
| `sound` | `Sounds`, vanilla sound cues named from config, to a player, to everyone, at a point or within a radius. An unknown key is ignored rather than thrown. |
| `command` | `SubCommand` and `CommandRouter` for sub-command dispatch, permissions and tab completion. |
| `message` | `MessageService` (MiniMessage-backed `messages.yml` with positional and named placeholders), `MiniMessages`, `Sprites` (item and block icons inline in text), `LegacyColorConverter` and `UntrustedText`. |
| `config` | `ConfigManager` for a single `config.yml`, `MultiConfigManager` for several files, and `ConfigUpdater` for versioned migrations. |
| `jar` | `JarHasher`, SHA-256 and SHA-512 digests of files on disk cached by (path, size, last-modified) so a repeated scan does not rehash what has not changed. For identifying which build of a plugin is actually installed, verifying a download against a published checksum, or noticing a jar that changed without its version being bumped. Pure JDK. |
| `storage` | The `Repository` / `RecordCodec` contract with `YamlRepository`, `SqliteRepository` and `MySqlRepository` implementations, plus `LocationCodec`. |
| `storage.sql` | `DatabaseConfig`, `ConnectionPool` (HikariCP) and `SchemaMigrator` for ordered, run-once migrations. |
| `scheduler` | `SchedulerService`, a Bukkit scheduler wrapper that tracks its tasks so `cancelAll()` cleans up in `onDisable`. `supplyAsync` runs work off-thread and hands the result back on the main thread. |
| `aggregation` | `WindowedCounter`, folding high-frequency events into a rolling window per key and reporting a summary only once the window crosses a threshold you supply. Named counters plus optional per-bucket tallies, so "400 blocks broken" becomes "400 blocks broken across 30 chunks". Bounded key count, per-key report interval, idle sweep and an injectable clock. No Bukkit types, so it works on a proxy and in plain unit tests. |
| `cooldown` | `CooldownService`, in-memory per-player cooldowns with an optional bypass permission and a check-and-apply `tryUse`. `RateLimiter` is a Bukkit-free per-key minimum interval for guarding inbound requests. |
| `menu` | `MenuHolder` and `MenuListener`, a typed `InventoryHolder` where every menu is read-only and routed to its own click handler. `SlotLayout` places entries at pinned slots and centres the rest. |
| `dialog` | `FormDialog`, a builder over Paper's Dialog API (text fields, sliders, toggles, dropdowns), and `FormResponse`, which clamps and defaults instead of trusting the client. |
| `hologram` | `HologramService`, holograms on native Paper `TextDisplay` entities with no plugin dependency. |
| `render` | `RenderLoop`, one async loop drawing every registered `Renderable` with distance culling, instead of a task per player. |
| `loot` | `LootTable`, weighted loot read from config (`material` / `min` / `max` / `weight` / `enchantments`) for crates, drops and rewards. |
| `permission` | `PermissionService`, LuckPerms lookups with a join-time cache and self-expiring temporary grants. |
| `messaging` | `MessageBus` and `Message`, a cross-server bus over plugin messaging. `messaging.proxy.ProxyMessageRelay` is the Velocity side. |
| `messaging.request` | `PendingRequests`, request/response correlation with timeouts for one-way transports such as plugin messages. No Bukkit types, so it works on a proxy too. |
| `region` | `Cuboid`, `RegionIndex` (chunk-bucketed position lookup), `RegionTracker` (enter/exit detection per player), `SelectionManager` and `SelectionWand` for two-corner selections. `ChunkKey` packs a chunk coordinate pair into one `long` map key and floors block coordinates into chunk coordinates - both of which are quietly wrong for negative coordinates if written by hand. |
| `block` | `SafeBlocks`, fills that only replace empty space and clears that only remove what was placed, plus `wouldOverwrite` for vetoing vanilla placements. |
| `display` | `TintPanel`, a translucent panel in any ARGB colour built from text displays, and `ArgbColours` for parsing `#RRGGBB`, `#AARRGGBB` and dye names. `Bars` renders text progress bars; `ProgressBars` manages named bossbars shown either to everyone or only within a radius, applying audience changes as a delta so a scoped bar does not flicker. |
| `http` | `ApiClient`, an async JSON client that never throws, with `ConnectionHealth` and a bounded `RetryQueue` for delivery that survives an outage. `Request` / `RequestBuilder` / `Response` are a blocking request builder, API-compatible with ModularEnigma Requests. |
| `vanish` | `VanishStatus`, plugin-agnostic vanish detection via player metadata. `vanish.proxy.ProxyVanishStatus` is the Velocity side (PremiumVanish). |
| `time` | `Durations`, player-facing duration formatting in three shapes: `clock` (`12:34`, `1:02:33`), `human` and `compact`. |
| *(root)* | `ItemBuilder` for quick `ItemStack`s, including persistent-data tagging so a custom item is identified by a tag rather than by a renameable display name, and `ConfigValidator` for checking config values on startup. |

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

### Item sprites in messages

A real inventory icon can sit inline in chat, a title, an action bar, a boss bar or a menu title.

**If you know the item when you write the message, you need no StoneLib API at all.** MiniMessage's
`<sprite>` and `<head>` tags are in the default tag set, and both `MessageService` and
`MiniMessages` use a stock MiniMessage instance, so they already work everywhere:

```yaml
# messages.yml
reward: "<green>You found <sprite:\"minecraft:items\":item/diamond_sword>!"
```

**If the item is only known at runtime, build the sprite in code** and pass it as a placeholder
value. Named placeholders accept an already-built `Component`, so there is no new method to learn:

```java
messages.sendNamed(player, "reward", "icon", Sprites.of(drop.getType()));
```

| Call | Gives you |
|---|---|
| `Sprites.of(material)` | the item's sprite, cached |
| `Sprites.of(stack)` | the same, from an `ItemStack`'s type |
| `Sprites.of(atlas, sprite)` | any sprite at all, including your own resource pack's |
| `Sprites.labelled(material)` | sprite, a space, then the item's name in the viewer's language |
| `Sprites.auditPage(page, perPage)` | a page of every material, for checking sprites by eye |

**For icons authored in YAML, opt in to the `<icon:>` tag.** It is not registered globally — that
would be shared mutable state, and would hand the tag to every plugin in the JVM — so a plugin that
wants it passes it to its own `MessageService`:

```java
MessageService messages = new MessageService(this, "messages.yml",
        Sprites.iconResolver(IconOptions.defaults()));
```

```yaml
tier-open: "<gold>Tier open — bring <amount>x <icon:'diamond_sword'>"
```

The argument is a material name, case-insensitive, with or without `minecraft:`. An unknown material
never throws: it degrades to the item's name and logs once at `WARNING`.

`IconOptions` is also the kill switch. `IconOptions.disabled()` makes every sprite fall back to the
item's translated name, so if a client problem ever turns up mid-event you can turn icons off from
your own plugin's config without a StoneLib release and without touching a call site.

Two caveats worth knowing before you lean on this:

- **Blocks are the weak case, and that is vanilla, not StoneLib.** A placeable block's inventory
  icon is a rendered 3D model, not a flat sprite, so the blocks atlas can only give a single face.
  `block/stone` looks right; `block/crafting_table` gives one face of it; `block/oak_stairs` does
  not exist at all. Correct those with an entry in StoneLib's bundled `sprites.yml`.
- **The server cannot check that a sprite exists.** The atlas is entirely client-side, so a wrong
  key throws nothing and logs nothing — it renders as missing-texture checkerboard on the player's
  screen. The only reliable check is looking at it, which is what `Sprites.auditPage` is for: wire
  it behind a `SubCommand` in a consuming plugin and page through it in-game.

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
- **Platform.** StoneLib builds against Paper 26.2 but emits Java 21 bytecode, so it runs on any JVM from 21 up.
  Build with JDK 25, ship for 21: consumers shade StoneLib, and shading copies class files verbatim, so a
  25-targeted build would force every downstream plugin onto a Java 25 server regardless of that plugin's own
  `<release>`. See `<release>21</release>` in `pom.xml`.

## Building from source

Paper 26.2 ships Java 25 class files, so an older JDK cannot read `paper-api` and fails with
`cannot access org.bukkit.*` on every import. Build with JDK 25 (`jitpack.yml` pins `openjdk25`) — that is
about what the compiler must READ, and is independent of the Java 21 bytecode it EMITS:

```shell
mvn clean package
```

This produces `target/StoneLib-2.3.0.jar` and a sources jar, and runs the test suite.

- `paper-api` versions carry a `-stable` qualifier, so a Maven range like `[26.2.build,)` resolves
  to nothing. Pin an exact version.
- Adventure is deliberately **not** pinned. `paper-api` manages it through `adventure-bom`, and
  pinning an older version breaks the Dialog API, which needs a newer `DialogLike`.

---

<p align="center">
  Made by <a href="https://anchorlight.dev">Anchorlight</a>
</p>
