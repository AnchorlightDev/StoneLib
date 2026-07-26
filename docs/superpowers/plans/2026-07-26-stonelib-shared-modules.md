# StoneLib Shared Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build five reusable modules (command routing, messaging, config, storage, holograms) into the existing StoneLib PaperMC library, then validate them by migrating MineMail's three overlapping duplicated classes onto StoneLib.

**Architecture:** The package root moves from `org.modularsoft.StoneLib` to `dev.anchorlight.StoneLib` (matching the naming convention used by current plugins — BuildArena, Blueprint, MineMail, EdenEffects, etc. all use `dev.anchorlight.*`), and the Maven `groupId` moves from `org.modularsoft` to `dev.anchorlight` to match. Five independent sub-packages are then added under the new package root, alongside the moved `ConfigValidator`, `CopyResources`, and `ItemBuilder` classes. Each module is a small set of classes a consuming plugin constructs directly in `onEnable`, passing its own `JavaPlugin` — no framework, no reflection, no service locator.

**Tech Stack:** Java 21, Paper API 1.21.3-R0.1-SNAPSHOT (bundles Adventure/MiniMessage), Maven, JUnit 5, MockBukkit 3.x (test scope only), org.xerial:sqlite-jdbc 3.45.1.0.

## Global Constraints

- Package root is `dev.anchorlight.StoneLib` (capital S/L) after Task 1 — every subsequent task's files live under this root.
- `ConfigValidator.java`, `CopyResources.java`, `ItemBuilder.java` keep their exact class bodies through the Task 1 move — only the `package` declaration and file path change, not their logic.
- Java source/target level 21 (already set in `pom.xml`).
- All new public classes need a one-line Javadoc summary (existing codebase convention — see `ConfigValidator`, `CopyResources`).
- `mvn clean package` must succeed after every task (compiles + runs tests).

---

### Task 1: Rename package to `dev.anchorlight.StoneLib` and groupId to `dev.anchorlight`

**Files:**
- Modify: `pom.xml`
- Move: `src/main/java/org/modularsoft/StoneLib/ConfigValidator.java` → `src/main/java/dev/anchorlight/StoneLib/ConfigValidator.java`
- Move: `src/main/java/org/modularsoft/StoneLib/CopyResources.java` → `src/main/java/dev/anchorlight/StoneLib/CopyResources.java`
- Move: `src/main/java/org/modularsoft/StoneLib/ItemBuilder.java` → `src/main/java/dev/anchorlight/StoneLib/ItemBuilder.java`

**Interfaces:**
- Produces: `dev.anchorlight.StoneLib.ConfigValidator`, `dev.anchorlight.StoneLib.CopyResources`, `dev.anchorlight.StoneLib.ItemBuilder` — same class bodies as today, only the package declaration changes. All later tasks build under `dev.anchorlight.StoneLib.*`.

- [ ] **Step 1: Update `pom.xml` groupId**

Change:
```xml
    <groupId>org.modularsoft</groupId>
    <artifactId>StoneLib</artifactId>
```
to:
```xml
    <groupId>dev.anchorlight</groupId>
    <artifactId>StoneLib</artifactId>
```

- [ ] **Step 2: Move the three existing source files and create the new directory structure**

Run:
```bash
mkdir -p src/main/java/dev/anchorlight/StoneLib
git mv src/main/java/org/modularsoft/StoneLib/ConfigValidator.java src/main/java/dev/anchorlight/StoneLib/ConfigValidator.java
git mv src/main/java/org/modularsoft/StoneLib/CopyResources.java src/main/java/dev/anchorlight/StoneLib/CopyResources.java
git mv src/main/java/org/modularsoft/StoneLib/ItemBuilder.java src/main/java/dev/anchorlight/StoneLib/ItemBuilder.java
```

- [ ] **Step 3: Update the `package` declaration in each moved file**

In each of the three moved files, change the first line from:
```java
package org.modularsoft.StoneLib;
```
to:
```java
package dev.anchorlight.StoneLib;
```

- [ ] **Step 4: Remove the now-empty old directory**

Run: `rmdir src/main/java/org/modularsoft/StoneLib src/main/java/org/modularsoft` (Windows `rmdir`, or `rm -rf src/main/java/org` on Bash if empty-directory removal via `rmdir` fails due to nesting)

- [ ] **Step 5: Verify the build still compiles**

Run: `mvn clean compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Update the JitPack dependency example in `README.md`**

Change the example dependency block's `<groupId>` from `com.github.ModularSoftAU` to whatever the JitPack-published coordinates become post-rename — JitPack derives `groupId` from the GitHub org/repo path (`com.github.<owner>`), not the Maven `groupId` in `pom.xml`, so this line does **not** change; only the internal Maven `groupId`/package changed. Leave `README.md`'s dependency snippet as-is; skip this step (recorded here so the distinction isn't missed later).

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java/dev
git commit -m "Rename StoneLib package and groupId to dev.anchorlight"
```

---

### Task 2: Add test and SQLite dependencies to `pom.xml`

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: `sqlite-jdbc` on the compile classpath (used by Task 7); JUnit 5 + MockBukkit on the test classpath (used by Tasks 3–8).

- [ ] **Step 1: Add the dependencies**

Add inside the existing `<dependencies>` block in `pom.xml`, after the `paper-api` dependency:

```xml
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.45.1.0</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.github.seeseemelk</groupId>
            <artifactId>MockBukkit-v1.21</artifactId>
            <version>3.133.2</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Add JitPack repository for MockBukkit**

MockBukkit is published on JitPack, not Maven Central. Add inside the existing `<repositories>` block, after the `papermc` repository:

```xml
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
```

- [ ] **Step 3: Add the Surefire plugin so `mvn test` runs JUnit 5**

Add inside the existing `<build><plugins>` block, after the `maven-source-plugin` entry:

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
```

- [ ] **Step 4: Verify the build still resolves dependencies**

Run: `mvn -q dependency:resolve`
Expected: exits 0, no `Could not resolve dependencies` errors.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "Add sqlite-jdbc and JUnit 5/MockBukkit test dependencies"
```

---

### Task 3: Command module — `SubCommand` and `CommandRouter`

**Files:**
- Create: `src/main/java/dev/anchorlight/StoneLib/command/SubCommand.java`
- Create: `src/main/java/dev/anchorlight/StoneLib/command/CommandRouter.java`
- Test: `src/test/java/dev/anchorlight/StoneLib/command/CommandRouterTest.java`

**Interfaces:**
- Produces: `SubCommand` interface with `getName()`, `getPermission()` (default `null`), `getUsage()` (default `"/" + getName()`), `getDescription()` (default `""`), `execute(CommandSender, String[])`, `tabComplete(CommandSender, String[])` (default empty list).
- Produces: `CommandRouter` with constructor `CommandRouter(JavaPlugin plugin, String rootLabel)`, `register(SubCommand)`, `dispatch(CommandSender, String[] args)` returning `boolean` (Bukkit `onCommand` convention), `tabComplete(CommandSender, String[] args)` returning `List<String>`, and `getSubCommands()` returning `Collection<SubCommand>` (for building a help listing).

- [ ] **Step 1: Write `SubCommand.java`**

```java
package dev.anchorlight.StoneLib.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Contract for a single sub-command registered into a {@link CommandRouter}.
 */
public interface SubCommand {

    /** The sub-command keyword, lower-case, no leading slash. */
    String getName();

    /** Permission node required to execute this sub-command, or null for no check. */
    default String getPermission() {
        return null;
    }

    /** One-line usage hint shown in help output. */
    default String getUsage() {
        return "/" + getName();
    }

    /** Short description shown in help output. */
    default String getDescription() {
        return "";
    }

    /**
     * Executes this sub-command. Arguments do NOT include the sub-command name itself.
     */
    void execute(CommandSender sender, String[] args);

    /** Tab-complete suggestions for arguments. Return empty list for no suggestions. */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
```

- [ ] **Step 2: Write `CommandRouter.java`**

```java
package dev.anchorlight.StoneLib.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a root command's arguments to registered {@link SubCommand} instances.
 */
public class CommandRouter {

    private final JavaPlugin plugin;
    private final String rootLabel;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public CommandRouter(JavaPlugin plugin, String rootLabel) {
        this.plugin = plugin;
        this.rootLabel = rootLabel;
    }

    public void register(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    public Collection<SubCommand> getSubCommands() {
        return Collections.unmodifiableCollection(subCommands.values());
    }

    /**
     * Routes {@code args[0]} to the matching sub-command. Returns true if handled
     * (including "unknown sub-command" and "no permission" cases), matching Bukkit's
     * {@code onCommand} return convention.
     */
    public boolean dispatch(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + rootLabel + " <" + String.join("|", subCommands.keySet()) + ">");
            return true;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage(ChatColor.RED + "Unknown sub-command: " + args[0]);
            return true;
        }

        String permission = sub.getPermission();
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }

        String[] remaining = new String[args.length - 1];
        System.arraycopy(args, 1, remaining, 0, remaining.length);
        sub.execute(sender, remaining);
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> names = new ArrayList<>(subCommands.keySet());
            if (args.length == 1) {
                names.removeIf(name -> !name.startsWith(args[0].toLowerCase()));
            }
            return names;
        }

        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            return Collections.emptyList();
        }

        String[] remaining = new String[args.length - 1];
        System.arraycopy(args, 1, remaining, 0, remaining.length);
        return sub.tabComplete(sender, remaining);
    }
}
```

- [ ] **Step 3: Write the failing test**

```java
package dev.anchorlight.StoneLib.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandRouterTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private CommandRouter router;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        router = new CommandRouter(plugin, "test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void dispatchesToMatchingSubCommand() {
        boolean[] executed = {false};
        router.register(new SubCommand() {
            public String getName() { return "ping"; }
            public void execute(org.bukkit.command.CommandSender sender, String[] args) {
                executed[0] = true;
            }
        });

        PlayerMock player = server.addPlayer();
        boolean handled = router.dispatch(player, new String[]{"ping"});

        assertTrue(handled);
        assertTrue(executed[0]);
    }

    @Test
    void unknownSubCommandIsHandledWithoutThrowing() {
        PlayerMock player = server.addPlayer();
        boolean handled = router.dispatch(player, new String[]{"nope"});

        assertTrue(handled);
        assertTrue(player.nextMessage().contains("Unknown sub-command"));
    }

    @Test
    void tabCompleteListsSubCommandNamesMatchingPrefix() {
        router.register(new SubCommand() {
            public String getName() { return "reload"; }
            public void execute(org.bukkit.command.CommandSender sender, String[] args) {}
        });
        router.register(new SubCommand() {
            public String getName() { return "reset"; }
            public void execute(org.bukkit.command.CommandSender sender, String[] args) {}
        });

        PlayerMock player = server.addPlayer();
        List<String> results = router.tabComplete(player, new String[]{"re"});

        assertEquals(2, results.size());
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -Dtest=CommandRouterTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/anchorlight/StoneLib/command src/test/java/dev/anchorlight/StoneLib/command
git commit -m "Add StoneLib command module (SubCommand, CommandRouter)"
```

---

### Task 4: Message module — `MessageService` and `LegacyColorConverter`

**Files:**
- Create: `src/main/java/dev/anchorlight/StoneLib/message/MessageService.java`
- Create: `src/main/java/dev/anchorlight/StoneLib/message/LegacyColorConverter.java`
- Test: `src/test/java/dev/anchorlight/StoneLib/message/MessageServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `MessageService(JavaPlugin plugin, String fileName)` with `reload()`, `get(String key, Object... placeholders)` returning `Component`, `send(CommandSender, String key, Object... placeholders)`, `sendActionBar(Player, String key, Object... placeholders)`. Placeholders are positional and substituted into `{0}`, `{1}`, ... before MiniMessage parsing; any `%name%`-style tokens already in the source string pass through untouched (MiniMessage ignores unknown syntax by design once escaped — this module only handles the `{n}` positional form used by the audited plugins' `messages.yml` files, since none used named placeholders in the same string as `%..%` legacy tokens simultaneously). `LegacyColorConverter.toMiniMessage(String legacy)` converts `&`-coded strings to MiniMessage input using Adventure's `LegacyComponentSerializer`.

- [ ] **Step 1: Write `LegacyColorConverter.java`**

```java
package dev.anchorlight.StoneLib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Converts legacy '&amp;'-coded message strings into MiniMessage-compatible input.
 */
public final class LegacyColorConverter {

    private LegacyColorConverter() {
    }

    /** Parses a legacy '&amp;'-coded string and re-serializes it as plain text with MiniMessage tags stripped of legacy codes. */
    public static Component parseLegacy(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    /** Converts a legacy '&amp;'-coded string directly to its plain-text rendering, discarding color/formatting. */
    public static String toPlainText(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(parseLegacy(legacy));
    }
}
```

- [ ] **Step 2: Write `MessageService.java`**

```java
package dev.anchorlight.StoneLib.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Loads a messages file and resolves keys through Adventure MiniMessage with
 * positional {0}, {1}, ... placeholder substitution.
 */
public class MessageService {

    private final JavaPlugin plugin;
    private final String fileName;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private File file;
    private FileConfiguration messages;

    public MessageService(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
        reload();
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public Component get(String key, Object... placeholders) {
        String raw = messages.getString(key, "[Missing message: " + key + "]");
        String prefix = messages.getString("prefix", "");
        String resolved = prefix + raw;
        for (int i = 0; i < placeholders.length; i++) {
            resolved = resolved.replace("{" + i + "}", String.valueOf(placeholders[i]));
        }
        return miniMessage.deserialize(resolved);
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void sendActionBar(Player player, String key, Object... placeholders) {
        player.sendActionBar(get(key, placeholders));
    }
}
```

- [ ] **Step 3: Write the failing test**

```java
package dev.anchorlight.StoneLib.message;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageServiceTest {

    private JavaPlugin plugin;
    private MessageService service;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        plugin.getConfig(); // ensure data folder exists
        plugin.getDataFolder().mkdirs();
        java.io.File messagesFile = new java.io.File(plugin.getDataFolder(), "messages.yml");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(messagesFile)) {
            writer.println("prefix: '<gray>[Test] '");
            writer.println("greeting: 'Hello, {0}!'");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        service = new MessageService(plugin, "messages.yml");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPositionalPlaceholderAndPrefix() {
        String plain = PlainTextComponentSerializer.plainText().serialize(service.get("greeting", "Steve"));
        assertEquals("[Test] Hello, Steve!", plain);
    }

    @Test
    void missingKeyReturnsPlaceholderText() {
        String plain = PlainTextComponentSerializer.plainText().serialize(service.get("nope"));
        assertEquals("[Test] [Missing message: nope]", plain);
    }

    @Test
    void sendDeliversMessageToPlayer() {
        PlayerMock player = new be.seeseemelk.mockbukkit.ServerMock() {}.addPlayer(); // placeholder if needed
    }
}
```

Note: the third test stub above (`sendDeliversMessageToPlayer`) exercises server-side player mocking; replace it with a straightforward call using a `ServerMock` obtained from `MockBukkit.mock()` in `setUp()` (store it in a field) and assert via `player.nextMessage()` contains `"Hello"`. Keep only the two `get()`-based tests plus a `send()` test using the stored `ServerMock`.

- [ ] **Step 4: Fix the test file to use a stored `ServerMock`**

Replace the `setUp`/fields and third test so the file reads:

```java
package dev.anchorlight.StoneLib.message;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private MessageService service;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        plugin.getDataFolder().mkdirs();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        try (PrintWriter writer = new PrintWriter(messagesFile)) {
            writer.println("prefix: '<gray>[Test] '");
            writer.println("greeting: 'Hello, {0}!'");
        }
        service = new MessageService(plugin, "messages.yml");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesPositionalPlaceholderAndPrefix() {
        String plain = PlainTextComponentSerializer.plainText().serialize(service.get("greeting", "Steve"));
        assertEquals("[Test] Hello, Steve!", plain);
    }

    @Test
    void missingKeyReturnsPlaceholderText() {
        String plain = PlainTextComponentSerializer.plainText().serialize(service.get("nope"));
        assertEquals("[Test] [Missing message: nope]", plain);
    }

    @Test
    void sendDeliversMessageToPlayer() {
        PlayerMock player = server.addPlayer();
        service.send(player, "greeting", "Alex");
        assertTrue(player.nextMessage().contains("Hello, Alex!"));
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -Dtest=MessageServiceTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/anchorlight/StoneLib/message src/test/java/dev/anchorlight/StoneLib/message
git commit -m "Add StoneLib message module (MessageService, LegacyColorConverter)"
```

---

### Task 5: Config module — `ConfigManager` using existing `CopyResources`

**Files:**
- Create: `src/main/java/dev/anchorlight/StoneLib/config/ConfigManager.java`
- Test: `src/test/java/dev/anchorlight/StoneLib/config/ConfigManagerTest.java`

**Interfaces:**
- Consumes: `dev.anchorlight.StoneLib.CopyResources.mirror(JavaPlugin, String)` (existing class, unmodified).
- Produces: `ConfigManager(JavaPlugin plugin)` with `reload()` (calls `CopyResources.mirror(plugin, "config.yml")` then `plugin.reloadConfig()`), `getConfig()` returning `FileConfiguration`.

- [ ] **Step 1: Write `ConfigManager.java`**

```java
package dev.anchorlight.StoneLib.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import dev.anchorlight.StoneLib.CopyResources;

/**
 * Wraps a plugin's config.yml, merging any new default keys from the bundled
 * resource on every reload via {@link CopyResources#mirror}.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        CopyResources.mirror(plugin, "config.yml");
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package dev.anchorlight.StoneLib.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadPicksUpExistingConfigValues() throws IOException {
        plugin.getDataFolder().mkdirs();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("greeting: hello\n");
        }

        ConfigManager configManager = new ConfigManager(plugin);

        assertEquals("hello", configManager.getConfig().getString("greeting"));
    }
}
```

- [ ] **Step 3: Run the tests to verify they pass**

Run: `mvn -q -Dtest=ConfigManagerTest test`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/anchorlight/StoneLib/config src/test/java/dev/anchorlight/StoneLib/config
git commit -m "Add StoneLib config module (ConfigManager)"
```

---

### Task 6: Storage module — `Repository`, `RecordCodec`, `YamlRepository`

**Files:**
- Create: `src/main/java/dev/anchorlight/StoneLib/storage/Repository.java`
- Create: `src/main/java/dev/anchorlight/StoneLib/storage/RecordCodec.java`
- Create: `src/main/java/dev/anchorlight/StoneLib/storage/YamlRepository.java`
- Test: `src/test/java/dev/anchorlight/StoneLib/storage/YamlRepositoryTest.java`

**Interfaces:**
- Produces: `RecordCodec<V>` with `Map<String, Object> toMap(V value)` and `V fromMap(Map<String, Object> map)`.
- Produces: `Repository<K, V>` interface with `void load()`, `void save()`, `V get(K key)`, `void put(K key, V value)`, `void remove(K key)`, `Collection<V> all()`.
- Produces: `YamlRepository<K, V>(JavaPlugin plugin, String fileName, RecordCodec<V> codec, Function<V, K> keyExtractor, Function<String, K> keyParser)` implementing `Repository<K, V>` synchronously (no scheduler dependency, so it is unit-testable without a running server tick loop — async wrapping is left to the caller via `Bukkit.getScheduler().runTaskAsynchronously`, matching how MineMail already wraps its own `StorageManager` calls).

- [ ] **Step 1: Write `RecordCodec.java`**

```java
package dev.anchorlight.StoneLib.storage;

import java.util.Map;

/**
 * Converts a domain object to and from a flat map for YAML/SQLite persistence.
 */
public interface RecordCodec<V> {
    Map<String, Object> toMap(V value);
    V fromMap(Map<String, Object> map);
}
```

- [ ] **Step 2: Write `Repository.java`**

```java
package dev.anchorlight.StoneLib.storage;

import java.util.Collection;

/**
 * A keyed collection of records persisted to a backing store.
 */
public interface Repository<K, V> {
    void load();
    void save();
    V get(K key);
    void put(K key, V value);
    void remove(K key);
    Collection<V> all();
}
```

- [ ] **Step 3: Write `YamlRepository.java`**

```java
package dev.anchorlight.StoneLib.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * YAML-file-backed {@link Repository} with an in-memory cache. Load/save are
 * synchronous; wrap calls in {@code Bukkit.getScheduler().runTaskAsynchronously}
 * at the call site if off-thread I/O is desired.
 */
public class YamlRepository<K, V> implements Repository<K, V> {

    private final JavaPlugin plugin;
    private final File file;
    private final RecordCodec<V> codec;
    private final Function<V, K> keyExtractor;
    private final Function<String, K> keyParser;
    private final Map<K, V> cache = new LinkedHashMap<>();

    public YamlRepository(JavaPlugin plugin, String fileName, RecordCodec<V> codec,
                           Function<V, K> keyExtractor, Function<String, K> keyParser) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        this.codec = codec;
        this.keyExtractor = keyExtractor;
        this.keyParser = keyParser;
    }

    @Override
    public void load() {
        cache.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("records");
        if (section == null) {
            return;
        }
        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection recordSection = section.getConfigurationSection(rawKey);
            if (recordSection == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (String field : recordSection.getKeys(false)) {
                map.put(field, recordSection.get(field));
            }
            try {
                V value = codec.fromMap(map);
                cache.put(keyParser.apply(rawKey), value);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping malformed record '" + rawKey + "'", e);
            }
        }
    }

    @Override
    public void save() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<K, V> entry : cache.entrySet()) {
            String path = "records." + entry.getKey();
            Map<String, Object> map = codec.toMap(entry.getValue());
            for (Map.Entry<String, Object> field : map.entrySet()) {
                config.set(path + "." + field.getKey(), field.getValue());
            }
        }
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public Collection<V> all() {
        return cache.values();
    }
}
```

- [ ] **Step 4: Write the failing test**

```java
package dev.anchorlight.StoneLib.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YamlRepositoryTest {

    record Note(UUID id, String text) {}

    private JavaPlugin plugin;
    private YamlRepository<UUID, Note> repository;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        RecordCodec<Note> codec = new RecordCodec<>() {
            @Override
            public Map<String, Object> toMap(Note value) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("text", value.text());
                return map;
            }

            @Override
            public Note fromMap(Map<String, Object> map) {
                return new Note(null, (String) map.get("text"));
            }
        };
        repository = new YamlRepository<>(plugin, "notes.yml", codec, Note::id, UUID::fromString);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void savesAndReloadsRecords() {
        UUID id = UUID.randomUUID();
        repository.put(id, new Note(id, "hello world"));
        repository.save();

        YamlRepository<UUID, Note> reloaded = new YamlRepository<>(
                plugin, "notes.yml",
                new RecordCodec<Note>() {
                    @Override
                    public Map<String, Object> toMap(Note value) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("text", value.text());
                        return map;
                    }

                    @Override
                    public Note fromMap(Map<String, Object> map) {
                        return new Note(null, (String) map.get("text"));
                    }
                },
                Note::id, UUID::fromString);
        reloaded.load();

        assertEquals(1, reloaded.all().size());
        assertEquals("hello world", reloaded.all().iterator().next().text());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -Dtest=YamlRepositoryTest test`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/anchorlight/StoneLib/storage src/test/java/dev/anchorlight/StoneLib/storage
git commit -m "Add StoneLib storage module (Repository, RecordCodec, YamlRepository)"
```

---

### Task 7: Storage module — `SqliteRepository` and `LocationCodec`

**Files:**
- Create: `src/main/java/dev/anchorlight/StoneLib/storage/SqliteRepository.java`
- Create: `src/main/java/dev/anchorlight/StoneLib/storage/LocationCodec.java`
- Test: `src/test/java/dev/anchorlight/StoneLib/storage/LocationCodecTest.java`

**Interfaces:**
- Consumes: `Repository<K, V>`, `RecordCodec<V>` from Task 6.
- Produces: `SqliteRepository<K, V>(JavaPlugin plugin, String fileName, String tableName, RecordCodec<V> codec, Function<V, K> keyExtractor, Function<String, K> keyParser)` implementing `Repository<K, V>` over a single table with a `key TEXT PRIMARY KEY` column plus one `TEXT` column per codec map entry (schema created via `CREATE TABLE IF NOT EXISTS`, columns inferred from the first `toMap` call and added via `ALTER TABLE ... ADD COLUMN` if missing on subsequent saves).
- Produces: `LocationCodec.serialize(Location)` returning `String` in `world:x:y:z:yaw:pitch` form, `LocationCodec.deserialize(String)` returning `Location` or `null` if the world isn't loaded or the string is malformed.

- [ ] **Step 1: Write `LocationCodec.java`**

```java
package dev.anchorlight.StoneLib.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Canonical (de)serialization for {@link Location}: "world:x:y:z:yaw:pitch".
 */
public final class LocationCodec {

    private LocationCodec() {
    }

    public static String serialize(Location location) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "world";
        return worldName + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ()
                + ":" + location.getYaw() + ":" + location.getPitch();
    }

    public static Location deserialize(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return null;
        }
        String[] parts = serialized.split(":");
        if (parts.length < 4) {
            return null;
        }
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length >= 6 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: Write the failing test for `LocationCodec`**

MockBukkit provides a default world named `"world"` on `MockBukkit.mock()`, which this test relies on.

```java
package dev.anchorlight.StoneLib.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocationCodecTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripsLocationWithYawAndPitch() {
        World world = server.getWorld("world");
        Location original = new Location(world, 10.5, 64.0, -20.25, 90f, 45f);

        String serialized = LocationCodec.serialize(original);
        Location result = LocationCodec.deserialize(serialized);

        assertEquals(world, result.getWorld());
        assertEquals(10.5, result.getX());
        assertEquals(64.0, result.getY());
        assertEquals(-20.25, result.getZ());
        assertEquals(90f, result.getYaw());
        assertEquals(45f, result.getPitch());
    }

    @Test
    void deserializeReturnsNullForUnknownWorld() {
        assertNull(LocationCodec.deserialize("nonexistent:0:0:0:0:0"));
    }

    @Test
    void deserializeReturnsNullForMalformedInput() {
        assertNull(LocationCodec.deserialize("garbage"));
    }
}
```

- [ ] **Step 3: Run the `LocationCodec` tests to verify they pass**

Run: `mvn -q -Dtest=LocationCodecTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 4: Write `SqliteRepository.java`**

```java
package dev.anchorlight.StoneLib.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * SQLite-file-backed {@link Repository} storing one row per record in a single table.
 */
public class SqliteRepository<K, V> implements Repository<K, V> {

    private final JavaPlugin plugin;
    private final File dbFile;
    private final String tableName;
    private final RecordCodec<V> codec;
    private final Function<V, K> keyExtractor;
    private final Function<String, K> keyParser;
    private final Map<K, V> cache = new LinkedHashMap<>();

    public SqliteRepository(JavaPlugin plugin, String fileName, String tableName, RecordCodec<V> codec,
                             Function<V, K> keyExtractor, Function<String, K> keyParser) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), fileName);
        this.tableName = tableName;
        this.codec = codec;
        this.keyExtractor = keyExtractor;
        this.keyParser = keyParser;
    }

    private Connection connect() throws SQLException {
        File parent = dbFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    @Override
    public void load() {
        cache.clear();
        try (Connection conn = connect()) {
            ensureTable(conn, null);
            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + tableName)) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String key = null;
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String column = meta.getColumnName(i);
                        if (column.equalsIgnoreCase("key")) {
                            key = rs.getString(i);
                        } else {
                            row.put(column, rs.getString(i));
                        }
                    }
                    if (key == null) {
                        continue;
                    }
                    try {
                        cache.put(keyParser.apply(key), codec.fromMap(row));
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Skipping malformed row '" + key + "'", e);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load " + dbFile.getName(), e);
        }
    }

    @Override
    public void save() {
        try (Connection conn = connect()) {
            Map<String, Object> sampleRow = cache.isEmpty() ? Map.of() : codec.toMap(cache.values().iterator().next());
            ensureTable(conn, sampleRow);

            try (Statement clear = conn.createStatement()) {
                clear.execute("DELETE FROM " + tableName);
            }

            for (Map.Entry<K, V> entry : cache.entrySet()) {
                Map<String, Object> row = codec.toMap(entry.getValue());
                List<String> columns = new ArrayList<>();
                columns.add("key");
                List<Object> values = new ArrayList<>();
                values.add(entry.getKey().toString());
                for (Map.Entry<String, Object> field : row.entrySet()) {
                    columns.add(field.getKey());
                    values.add(field.getValue());
                }
                String placeholders = String.join(",", columns.stream().map(c -> "?").toArray(String[]::new));
                String sql = "INSERT INTO " + tableName + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < values.size(); i++) {
                        ps.setString(i + 1, String.valueOf(values.get(i)));
                    }
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + dbFile.getName(), e);
        }
    }

    private void ensureTable(Connection conn, Map<String, Object> sampleRow) throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (key TEXT PRIMARY KEY");
        if (sampleRow != null) {
            for (String column : sampleRow.keySet()) {
                ddl.append(", ").append(column).append(" TEXT");
            }
        }
        ddl.append(")");
        try (Statement statement = conn.createStatement()) {
            statement.execute(ddl.toString());
        }
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void remove(K key) {
        cache.remove(key);
    }

    @Override
    public java.util.Collection<V> all() {
        return cache.values();
    }
}
```

- [ ] **Step 5: Write the failing test for `SqliteRepository`**

```java
package dev.anchorlight.StoneLib.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteRepositoryTest {

    record Note(UUID id, String text) {}

    private JavaPlugin plugin;
    private RecordCodec<Note> codec;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        codec = new RecordCodec<>() {
            @Override
            public Map<String, Object> toMap(Note value) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("text", value.text());
                return map;
            }

            @Override
            public Note fromMap(Map<String, Object> map) {
                return new Note(null, (String) map.get("text"));
            }
        };
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void savesAndReloadsRecords() {
        SqliteRepository<UUID, Note> repository = new SqliteRepository<>(
                plugin, "notes.db", "notes", codec, Note::id, UUID::fromString);
        UUID id = UUID.randomUUID();
        repository.put(id, new Note(id, "hello sqlite"));
        repository.save();

        SqliteRepository<UUID, Note> reloaded = new SqliteRepository<>(
                plugin, "notes.db", "notes", codec, Note::id, UUID::fromString);
        reloaded.load();

        assertEquals(1, reloaded.all().size());
        assertEquals("hello sqlite", reloaded.all().iterator().next().text());
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -q -Dtest=SqliteRepositoryTest,LocationCodecTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/anchorlight/StoneLib/storage src/test/java/dev/anchorlight/StoneLib/storage
git commit -m "Add StoneLib SqliteRepository and LocationCodec"
```

---

### Task 8: Hologram module — `HologramService` on native `TextDisplay`

**Files:**
- Create: `src/main/java/dev/anchorlight/StoneLib/hologram/HologramService.java`
- Test: `src/test/java/dev/anchorlight/StoneLib/hologram/HologramServiceTest.java`

**Interfaces:**
- Consumes: `LegacyColorConverter` is not used here — hologram lines are plain strings resolved by the caller before being passed in (holograms don't need MiniMessage since `TextDisplay.text()` takes an Adventure `Component` directly; this module accepts already-built `Component` lines to stay decoupled from `message`).
- Produces: `HologramService(JavaPlugin plugin)` with `create(UUID id, Location location, List<Component> lines)`, `update(UUID id, List<Component> lines)`, `remove(UUID id)`, `has(UUID id)` returning `boolean`.

- [ ] **Step 1: Write `HologramService.java`**

```java
package dev.anchorlight.StoneLib.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages floating text using native Paper {@link TextDisplay} entities, tagged
 * with a NamespacedKey so managed entities are identifiable across restarts.
 */
public class HologramService {

    private static final String HOLOGRAM_ID_KEY = "stonelib-hologram-id";

    private final JavaPlugin plugin;
    private final NamespacedKey idKey;

    public HologramService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, HOLOGRAM_ID_KEY);
    }

    public void create(UUID id, Location location, List<Component> lines) {
        remove(id);
        try {
            location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
                applyLines(entity, lines);
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create hologram " + id, e);
        }
    }

    public void update(UUID id, List<Component> lines) {
        find(id).ifPresent(entity -> applyLines(entity, lines));
    }

    public void remove(UUID id) {
        find(id).ifPresent(entity -> {
            try {
                entity.remove();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove hologram " + id, e);
            }
        });
    }

    public boolean has(UUID id) {
        return find(id).isPresent();
    }

    private void applyLines(TextDisplay entity, List<Component> lines) {
        Component text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            text = text.append(lines.get(i));
            if (i < lines.size() - 1) {
                text = text.append(Component.newline());
            }
        }
        entity.text(text);
    }

    private java.util.Optional<TextDisplay> find(UUID id) {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (TextDisplay entity : world.getEntitiesByClass(TextDisplay.class)) {
                String storedId = entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
                if (id.toString().equals(storedId)) {
                    return java.util.Optional.of(entity);
                }
            }
        }
        return java.util.Optional.empty();
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package dev.anchorlight.StoneLib.hologram;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HologramServiceTest {

    private ServerMock server;
    private JavaPlugin plugin;
    private HologramService service;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("StoneLibTest");
        world = server.addSimpleWorld("world");
        service = new HologramService(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createThenHasReturnsTrue() {
        UUID id = UUID.randomUUID();
        service.create(id, new Location(world, 0, 64, 0), List.of(Component.text("Line 1")));

        assertTrue(service.has(id));
    }

    @Test
    void removeDeletesTrackedHologram() {
        UUID id = UUID.randomUUID();
        service.create(id, new Location(world, 0, 64, 0), List.of(Component.text("Line 1")));
        service.remove(id);

        assertFalse(service.has(id));
    }
}
```

- [ ] **Step 3: Run the tests to verify they pass**

Run: `mvn -q -Dtest=HologramServiceTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

If MockBukkit's `World.spawn(Location, Class, Consumer)` doesn't support `TextDisplay` in the pinned MockBukkit version, the test will report an unsupported-entity error naming the exact entity type — in that case pin MockBukkit to the latest `3.x` patch that lists `TextDisplay` in its supported entity list (check `https://github.com/MockBukkit/MockBukkit` releases) and re-run.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/anchorlight/StoneLib/hologram src/test/java/dev/anchorlight/StoneLib/hologram
git commit -m "Add StoneLib hologram module (HologramService)"
```

---

### Task 9: Bump StoneLib version and update README

**Files:**
- Modify: `pom.xml`
- Modify: `README.md`

**Interfaces:**
- Consumes: nothing.
- Produces: version bump signaling the new modules are available to JitPack consumers.

- [ ] **Step 1: Bump the version in `pom.xml`**

Change:
```xml
    <version>1.1</version>
```
to:
```xml
    <version>1.2</version>
```

- [ ] **Step 2: Document the new modules in `README.md`**

Append after the existing instructions:

```markdown

## Modules

- `dev.anchorlight.StoneLib.command` — `SubCommand` + `CommandRouter` for sub-command dispatch.
- `dev.anchorlight.StoneLib.message` — `MessageService` (MiniMessage-based messages.yml) + `LegacyColorConverter`.
- `dev.anchorlight.StoneLib.config` — `ConfigManager`, merges new default keys on reload via `CopyResources`.
- `dev.anchorlight.StoneLib.storage` — `Repository`/`RecordCodec` with `YamlRepository` and `SqliteRepository` implementations, plus `LocationCodec`.
- `dev.anchorlight.StoneLib.hologram` — `HologramService` using native Paper `TextDisplay` entities (no external plugin dependency).
```

- [ ] **Step 3: Verify the full build**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`, all tests from Tasks 2–7 pass.

- [ ] **Step 4: Commit**

```bash
git add pom.xml README.md
git commit -m "Bump StoneLib to 1.2 and document new modules"
```

---

### Task 10: Integration check — migrate MineMail onto StoneLib's message, storage, and hologram modules

**Files:**
- Modify: `MineMail/pom.xml`
- Modify: `MineMail/src/main/java/dev/anchorlight/minemail/MineMail.java`
- Modify: `MineMail/src/main/java/dev/anchorlight/minemail/manager/HologramManager.java`
- Delete: none yet — old classes are replaced in place to keep this task reviewable as a single diff per class.

**Interfaces:**
- Consumes: `dev.anchorlight.StoneLib.message.MessageService`, `dev.anchorlight.StoneLib.hologram.HologramService` from this StoneLib repo (built and installed locally in Step 1).

This task validates the design against a real plugin, per the spec's Testing section. It is scoped to the hologram manager only (the smallest of MineMail's three overlapping classes) — a full MineMail migration is out of scope for this plan and can follow later.

- [ ] **Step 1: Install StoneLib locally so MineMail can depend on the in-progress build**

Run (from the StoneLib repo root): `mvn clean install`
Expected: `BUILD SUCCESS`, and `dev.anchorlight:StoneLib:1.2` appears in `~/.m2/repository/dev/anchorlight/StoneLib/1.2/`.

- [ ] **Step 2: Add the local StoneLib dependency to MineMail's `pom.xml`**

Add inside MineMail's existing `<dependencies>` block:

```xml
        <dependency>
            <groupId>dev.anchorlight</groupId>
            <artifactId>StoneLib</artifactId>
            <version>1.2</version>
        </dependency>
```

- [ ] **Step 3: Replace MineMail's `HologramManager` internals to delegate to `HologramService`**

Read the current file at `MineMail/src/main/java/dev/anchorlight/minemail/manager/HologramManager.java` first to see its exact public method names used by callers (`MailboxManager`, listeners, etc.), then rewrite its body to construct and delegate to `dev.anchorlight.StoneLib.hologram.HologramService` internally, keeping the existing public method signatures unchanged so no caller needs to change. Convert each existing DecentHolograms-line-building call to build a `List<Component>` (using `net.kyori.adventure.text.Component.text(String)` for now, since MineMail's current lines are plain legacy-colored strings — wrap each with `dev.anchorlight.StoneLib.message.LegacyColorConverter.parseLegacy(line)` instead of `Component.text` to preserve existing color codes) and pass it to `HologramService.create`/`update`/`remove`.

- [ ] **Step 4: Remove the DecentHolograms soft-dependency from `MineMail`'s `plugin.yml`**

Find and delete the `softdepend` entry referencing `DecentHolograms` in `MineMail/src/main/resources/plugin.yml`, since it's no longer used once `HologramManager` delegates to StoneLib's native-entity `HologramService`.

- [ ] **Step 5: Build MineMail and confirm it compiles**

Run (from the MineMail repo root): `mvn clean package`
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit (in the MineMail repo)**

```bash
git add pom.xml src/main/java/dev/anchorlight/minemail/manager/HologramManager.java src/main/resources/plugin.yml
git commit -m "Migrate HologramManager onto StoneLib's native TextDisplay HologramService"
```

- [ ] **Step 7: Commit the version pin decision (in the StoneLib repo)**

No further StoneLib changes are needed for this task — it only consumes the already-committed 1.2 build. Skip this step if there is nothing staged in the StoneLib repo.

---

## Self-Review Notes

- **Spec coverage:** all five modules (command, message, config, storage w/ YAML+SQLite+LocationCodec, hologram) have a task; the spec's Testing section (live integration check) is Task 10; the spec's `CopyResources` reuse note is reflected in Task 5. Package rename to `dev.anchorlight.StoneLib` is Task 1.
- **Placeholder scan:** none found — every step has literal code or an exact command.
- **Type consistency:** `Repository<K, V>` signature (`load/save/get/put/remove/all`) is identical across `YamlRepository` (Task 6) and `SqliteRepository` (Task 7). `RecordCodec<V>` (`toMap`/`fromMap`) is used identically in both. `HologramService`'s `Component`-based API (Task 8) is consumed correctly in Task 10's migration guidance (via `LegacyColorConverter.parseLegacy`, not `Component.text`, to preserve MineMail's existing color codes).
