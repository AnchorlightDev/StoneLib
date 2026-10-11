package dev.anchorlight.stonelib.entity;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.persistence.PersistentDataContainerMock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class EntityTagsTest {

    private final List<Entity> entities = new ArrayList<>();
    private World world;
    private EntityTags tags;

    @BeforeEach
    void setUp() {
        tags = new EntityTags(new NamespacedKey("test", "screen-group"), new NamespacedKey("test", "screen-role"),
                new NamespacedKey("test", "screen-index"));
        world = Mockito.mock(World.class);
        when(world.getEntities()).thenAnswer(invocation -> new ArrayList<>(entities));
    }

    /** An entity with a real data container that drops out of the world when removed. */
    private Entity spawn() {
        Entity entity = Mockito.mock(Entity.class);
        PersistentDataContainerMock data = new PersistentDataContainerMock();
        when(entity.getPersistentDataContainer()).thenReturn(data);
        when(entity.isValid()).thenAnswer(invocation -> entities.contains(entity));
        Mockito.doAnswer(invocation -> entities.remove(entity)).when(entity).remove();
        entities.add(entity);
        return entity;
    }

    @Test
    void readsBackGroupRoleAndIndex() {
        Entity stand = tags.tag(spawn(), "lobby", "frame", 3);
        assertTrue(tags.isTagged(stand));
        assertEquals("lobby", tags.group(stand).orElseThrow());
        assertEquals("frame", tags.role(stand).orElseThrow());
        assertEquals(3, tags.index(stand).getAsInt());

        Entity bare = tags.tag(spawn(), "lobby");
        assertTrue(tags.role(bare).isEmpty());
        assertTrue(tags.index(bare).isEmpty());
        assertFalse(tags.isTagged(spawn()));
    }

    @Test
    void findsAndRemovesByGroup() {
        tags.tag(spawn(), "a");
        tags.tag(spawn(), "a");
        tags.tag(spawn(), "b");
        assertEquals(2, tags.find(List.of(world), "a").size());
        assertEquals(2, tags.removeGroup(List.of(world), "a"));
        assertEquals(0, tags.find(List.of(world), "a").size());
        assertEquals(1, tags.find(List.of(world), "b").size());
    }

    @Test
    void removeOrphansKeepsLiveGroupsAndUntaggedEntities() {
        tags.tag(spawn(), "live");
        tags.tag(spawn(), "dead");
        Entity untagged = spawn();
        assertEquals(1, tags.removeOrphans(List.of(world), Set.of("live")));
        assertEquals(1, tags.find(List.of(world), "live").size());
        assertTrue(untagged.isValid());
        assertEquals(1, tags.removeAll(List.of(world)));
        assertTrue(untagged.isValid());
    }
}
