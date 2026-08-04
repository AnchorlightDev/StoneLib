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

- `dev.anchorlight.StoneLib.command` — `SubCommand` + `CommandRouter` for sub-command dispatch.
- `dev.anchorlight.StoneLib.message` — `MessageService` (MiniMessage-based messages.yml) + `LegacyColorConverter`.
- `dev.anchorlight.StoneLib.config` — `ConfigManager`, merges new default keys on reload via `CopyResources`.
- `dev.anchorlight.StoneLib.storage` — `Repository`/`RecordCodec` with `YamlRepository` and `SqliteRepository` implementations, plus `LocationCodec`.
- `dev.anchorlight.StoneLib.hologram` — `HologramService` using native Paper `TextDisplay` entities (no external plugin dependency).

## Plugin onEnable / onDisable Template

Each module is constructed directly — no framework, no service locator. Wire only the
modules your plugin actually uses; delete the rest. Order matters: config before
messages/storage (they may read config values), storage before anything that loads
data from it, holograms last since they're usually populated from loaded data.

```java
package dev.anchorlight.example;

import dev.anchorlight.StoneLib.command.CommandRouter;
import dev.anchorlight.StoneLib.config.ConfigManager;
import dev.anchorlight.StoneLib.hologram.HologramService;
import dev.anchorlight.StoneLib.message.MessageService;
import dev.anchorlight.StoneLib.storage.YamlRepository;
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
