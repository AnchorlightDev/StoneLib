package dev.anchorlight.stonelib.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a {@link MapPool} keeps the map ids it has minted, so a screen keeps the same maps across
 * restarts. Back it with whatever the plugin already persists to: a database table, a YAML file.
 *
 * <p>Ids are keyed by an owner (a screen or board id) and a slot (a slide index). An
 * implementation may throw; {@link MapPool} lets the exception through to the caller.
 */
public interface MapIdStore {

    /** One tile of a slot: its grid position and the map id drawn there. */
    record Tile(int tileX, int tileY, int mapId) {
    }

    /**
     * The tiles stored for {@code owner}'s {@code slot}, in any order; empty when none are. Return
     * either nothing or the whole slot: a partial or wrongly sized slot should be reported as
     * empty (and replaced on the next {@link #save}).
     */
    List<Tile> load(String owner, int slot) throws Exception;

    /** Stores {@code tiles} for {@code owner}'s {@code slot}. Called once per slot, when it is first allocated. */
    void save(String owner, int slot, List<Tile> tiles) throws Exception;

    /** Forgets everything stored for {@code owner}, when the screen is deleted. Optional. */
    default void delete(String owner) throws Exception {
    }

    /** A store that only lives as long as the server - for tests and throwaway screens. */
    static MapIdStore memory() {
        return new MapIdStore() {
            private final Map<String, List<Tile>> tiles = new ConcurrentHashMap<>();

            @Override
            public List<Tile> load(String owner, int slot) {
                return new ArrayList<>(tiles.getOrDefault(owner + "#" + slot, List.of()));
            }

            @Override
            public void save(String owner, int slot, List<Tile> saved) {
                tiles.put(owner + "#" + slot, List.copyOf(saved));
            }

            @Override
            public void delete(String owner) {
                tiles.keySet().removeIf(key -> key.startsWith(owner + "#"));
            }
        };
    }
}
