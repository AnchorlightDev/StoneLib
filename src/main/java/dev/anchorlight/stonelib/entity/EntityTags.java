package dev.anchorlight.stonelib.entity;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Marks entities a plugin spawns as belonging to a named group (a screen, a board, a statue) with
 * an optional role and index, in the entity's persistent data, so they can be found again after a
 * restart or a chunk reload, and cleaned up when the thing they belonged to no longer exists.
 *
 * <p>Persistent display and frame entities outlive the plugin's memory: a crash, a reload or a
 * deleted config entry leaves them in the world with nothing tracking them. Tagging them at spawn
 * time and calling {@link #removeOrphans} on enable (and from a {@code ChunkLoadEvent}) is the cure.
 *
 * <pre>{@code
 * EntityTags tags = new EntityTags(plugin, "screen");
 * world.spawn(location, BlockDisplay.class, d -> tags.tag(d, screenId, "frame", i));
 *
 * // onEnable, after loading screens
 * tags.removeOrphans(screens.keySet());
 *
 * // ChunkLoadEvent
 * tags.removeOrphans(event.getChunk(), screens.keySet());
 * }</pre>
 *
 * <p>All methods touch entities and must run on the main thread (or the owning region on Folia).
 */
public final class EntityTags {

    private final NamespacedKey groupKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey indexKey;

    /** Keys {@code <plugin>:<prefix>-group}, {@code <plugin>:<prefix>-role} and {@code <plugin>:<prefix>-index}. */
    public EntityTags(Plugin plugin, String prefix) {
        this(new NamespacedKey(plugin, prefix + "-group"),
                new NamespacedKey(plugin, prefix + "-role"),
                new NamespacedKey(plugin, prefix + "-index"));
    }

    /** Explicit keys, for adopting entities an older version of a plugin already tagged. */
    public EntityTags(NamespacedKey groupKey, NamespacedKey roleKey, NamespacedKey indexKey) {
        this.groupKey = groupKey;
        this.roleKey = roleKey;
        this.indexKey = indexKey;
    }

    public NamespacedKey groupKey() {
        return groupKey;
    }

    /** Tags {@code entity} as part of {@code group}. */
    public <E extends Entity> E tag(E entity, String group) {
        entity.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, group);
        return entity;
    }

    /** Tags {@code entity} as part of {@code group} with a role and an index ({@code index < 0} stores none). */
    public <E extends Entity> E tag(E entity, String group, String role, int index) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(groupKey, PersistentDataType.STRING, group);
        if (role != null) {
            data.set(roleKey, PersistentDataType.STRING, role);
        }
        if (index >= 0) {
            data.set(indexKey, PersistentDataType.INTEGER, index);
        }
        return entity;
    }

    public boolean isTagged(Entity entity) {
        return entity.getPersistentDataContainer().has(groupKey, PersistentDataType.STRING);
    }

    public Optional<String> group(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING));
    }

    public Optional<String> role(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING));
    }

    public OptionalInt index(Entity entity) {
        Integer value = entity.getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /** Every loaded, tagged entity of {@code group} across {@code worlds}. */
    public List<Entity> find(Collection<? extends World> worlds, String group) {
        List<Entity> found = new ArrayList<>();
        for (World world : worlds) {
            for (Entity entity : world.getEntities()) {
                if (group.equals(group(entity).orElse(null))) {
                    found.add(entity);
                }
            }
        }
        return found;
    }

    /** Removes every loaded, tagged entity of {@code group}. Returns how many were removed. */
    public int removeGroup(Collection<? extends World> worlds, String group) {
        return removeMatching(worlds, entity -> group.equals(group(entity).orElse(null)));
    }

    /** Removes every loaded, tagged entity whose group is not in {@code liveGroups}. */
    public int removeOrphans(Collection<? extends World> worlds, Set<String> liveGroups) {
        return removeMatching(worlds, entity -> group(entity).map(g -> !liveGroups.contains(g)).orElse(false));
    }

    /** Removes the tagged entities in one chunk whose group is not in {@code liveGroups}. */
    public int removeOrphans(Chunk chunk, Set<String> liveGroups) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            Optional<String> group = group(entity);
            if (group.isPresent() && !liveGroups.contains(group.get())) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Removes every loaded, tagged entity, whatever its group. */
    public int removeAll(Collection<? extends World> worlds) {
        return removeMatching(worlds, this::isTagged);
    }

    private int removeMatching(Collection<? extends World> worlds, Predicate<Entity> match) {
        int removed = 0;
        for (World world : worlds) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (match.test(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
