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
