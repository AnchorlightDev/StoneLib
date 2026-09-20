package dev.anchorlight.stonelib.message;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Item and block sprites as chat components - a real inventory icon inline in chat, titles,
 * action bars, boss bars and menu text.
 *
 * <p>Pure and static. {@code Sprites} builds components and returns them; it never sends anything,
 * holds a plugin reference or reads a plugin's config. Everything it returns is an immutable
 * Adventure {@link Component}, safe to cache, reuse and hand to any audience.
 *
 * <h2>Two ways in</h2>
 *
 * <p>For a sprite known when the message is <em>authored</em>, no StoneLib API is needed at all:
 * MiniMessage's own {@code <sprite>} tag is in the default tag set and already works in every
 * StoneLib message method, because {@link MiniMessages} and {@link MessageService} both use a
 * stock {@code MiniMessage.miniMessage()}:
 *
 * <pre>{@code
 * # messages.yml - works today, no code required
 * reward: "<green>You found <sprite:"minecraft:items":item/diamond_sword>!"
 * }</pre>
 *
 * <p>For a sprite chosen at <em>runtime</em>, build one here and pass it as a placeholder value.
 * {@link MiniMessages#resolvers} accepts a {@link Component} value, so this needs no new method:
 *
 * <pre>{@code
 * messages.sendNamed(player, "reward", "icon", Sprites.of(drop.getType()));
 * }</pre>
 *
 * <h2>The server cannot verify a sprite exists</h2>
 *
 * <p>Sprite atlases live entirely client-side. Nothing here can ask whether a key is real, and a
 * wrong key does not throw, log or fail - it renders as the black-and-magenta missing-texture
 * checkerboard on the player's screen, with complete silence on the server. That is why the
 * override table and {@link #auditPage} exist: the only reliable verification is a human looking
 * at the result in-game.
 *
 * <p>For the same reason, the convention steps below are <em>guesses</em>, not lookups. They are
 * right for the common cases and wrong for some others, and the override table is how a wrong one
 * gets corrected.
 *
 * @see IconOptions
 */
public final class Sprites {

    /*
     * Atlas names, kept together so the next split has exactly one place to change.
     *
     * Verified against: Paper 26.2 server with 26.x clients, September 2026.
     *
     * Items moved out of the blocks atlas into their own `minecraft:items` atlas at 1.21.11.
     * Every client this fleet serves is 26.x, comfortably past that split, so ITEMS is correct
     * for items.
     *
     * Note that Adventure 5.2.0's own SpriteObjectContents.DEFAULT_ATLAS is still
     * `minecraft:blocks` -- that is the legacy default for the one-argument
     * ObjectContents.sprite(Key) overload, kept for backwards compatibility. We never rely on it;
     * both atlases are always passed explicitly.
     */
    private static final Key ITEMS_ATLAS = Key.key("minecraft:items");
    private static final Key BLOCKS_ATLAS = Key.key("minecraft:blocks");

    /** Value in {@code sprites.yml} that forces a material onto the text fallback. */
    private static final String FORCE_TEXT = "-";

    private static final String OVERRIDES_RESOURCE = "/sprites.yml";

    private static final Logger LOG = Logger.getLogger(Sprites.class.getName());

    /** Values already complained about, so a bad template logs once rather than once per tick. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Resolved sprite components, keyed by material. Components are immutable, so caching them is
     * free, and a boss bar or action bar rebuilding its text every tick reuses one instance instead
     * of allocating a fresh component tree per frame.
     *
     * <p>{@link EnumMap} rather than a concurrent map because the maps are tiny, array-backed and
     * read far more than written; the lock below is uncontended in practice, since Bukkit message
     * building happens on the main thread.
     */
    private static final Map<Material, Component> SPRITE_CACHE = new EnumMap<>(Material.class);
    private static final Map<Material, Component> LABELLED_CACHE = new EnumMap<>(Material.class);
    private static final Object CACHE_LOCK = new Object();

    /** Lazily loaded, then immutable. Null until the first resolution triggers the load. */
    private static volatile Map<Material, String> overrides;

    private Sprites() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    // ------------------------------------------------------------------ public

    /**
     * The inventory sprite for {@code material}, or its translated name when no sprite maps.
     *
     * <p>Repeated calls for one material return the same cached instance.
     */
    public static Component of(Material material) {
        if (material == null) {
            return Component.empty();
        }
        synchronized (CACHE_LOCK) {
            return SPRITE_CACHE.computeIfAbsent(material, Sprites::resolve);
        }
    }

    /** The sprite for a stack's type. Stack size, meta and enchantments are ignored. */
    public static Component of(ItemStack stack) {
        return stack == null ? Component.empty() : of(stack.getType());
    }

    /**
     * An arbitrary sprite, with no material mapping and no override lookup - the escape hatch for
     * atlases StoneLib knows nothing about, including resource-pack atlases of your own.
     *
     * <p>Nothing validates either key. A typo renders as missing-texture checkerboard client-side.
     */
    public static Component of(Key atlas, Key sprite) {
        if (atlas == null || sprite == null) {
            return Component.empty();
        }
        return Component.object(ObjectContents.sprite(atlas, sprite));
    }

    /**
     * The sprite for {@code material}, a space, then the item's name.
     *
     * <p>The name is a {@link Component#translatable} of the material's own translation key, so it
     * renders in each viewer's own language - the same string the client shows in its inventory -
     * rather than a server-side guess at English.
     */
    public static Component labelled(Material material) {
        if (material == null) {
            return Component.empty();
        }
        synchronized (CACHE_LOCK) {
            return LABELLED_CACHE.computeIfAbsent(material, m -> {
                Component sprite = SPRITE_CACHE.computeIfAbsent(m, Sprites::resolve);
                Component name = text(m);
                // When resolve() already fell back to the name, a "name name" label helps nobody.
                return sprite.equals(name) ? name : sprite.append(Component.space()).append(name);
            });
        }
    }

    /**
     * A {@code <icon:'MATERIAL'>} tag resolver, for icons authored in {@code messages.yml} rather
     * than passed from code.
     *
     * <pre>{@code
     * MessageService messages = new MessageService(this, "messages.yml",
     *         Sprites.iconResolver(IconOptions.defaults()));
     * }</pre>
     *
     * <pre>{@code
     * # messages.yml
     * tier-open: "<gold>Tier open - bring <amount>x <icon:'diamond_sword'>"
     * }</pre>
     *
     * <p>The argument is a material name, case-insensitive, with or without the {@code minecraft:}
     * namespace. An unknown material never throws: it emits the name as text and logs once at
     * {@code WARNING}, so a typo in a template is visible in the log without spamming it.
     *
     * <p>This is returned for a plugin to pass to its own {@link MessageService}, never registered
     * on the shared {@code MiniMessage} instance - that would be global state, and would hand the
     * tag to every plugin in the JVM whether it asked for it or not.
     */
    public static TagResolver iconResolver(IconOptions options) {
        IconOptions opts = options == null ? IconOptions.defaults() : options;
        return TagResolver.resolver("icon", (args, ctx) -> {
            String raw = args.popOr("<icon:> needs a material name, e.g. <icon:'diamond_sword'>")
                    .value();
            Material material = matchMaterial(raw);
            if (material == null) {
                warnOnce(raw);
                return Tag.selfClosingInserting(Component.text(prettify(raw)));
            }
            if (!opts.enabled()) {
                return Tag.selfClosingInserting(text(material));
            }
            return Tag.selfClosingInserting(opts.labelled() ? labelled(material) : of(material));
        });
    }

    /**
     * One page of every {@link Material} rendered as sprite plus material name, for eyeballing
     * which sprites actually resolve.
     *
     * <p>Sprite correctness is only ever confirmed visually, so this is the tool that populates
     * the override table: anything showing checkerboard or a blank needs an entry in
     * {@code sprites.yml}. StoneLib ships no commands of its own, so wire this behind a
     * {@code SubCommand} in whichever plugin is consuming sprites, using the existing
     * {@code CommandRouter}.
     *
     * @param page    1-based page number; out-of-range pages return a short notice, never an error
     * @param perPage rows per page, clamped to at least 1
     */
    public static Component auditPage(int page, int perPage) {
        Material[] all = Material.values();
        int size = Math.max(1, perPage);
        int pages = (all.length + size - 1) / size;
        int index = Math.max(1, page);
        if (index > pages) {
            return Component.text("No such page. " + pages + " page(s) of " + all.length + " materials.");
        }

        Component out = Component.text("Sprite audit - page " + index + "/" + pages)
                .append(Component.newline());
        int from = (index - 1) * size;
        int to = Math.min(from + size, all.length);
        for (int i = from; i < to; i++) {
            Material material = all[i];
            out = out.append(of(material))
                    .append(Component.space())
                    .append(Component.text(material.name()))
                    .append(Component.newline());
        }
        return out;
    }

    // ------------------------------------------------------------- resolution

    /**
     * Material to sprite, first hit wins:
     *
     * <ol>
     *   <li>the {@code sprites.yml} override table, including {@code "-"} to force text;</li>
     *   <li>blocks: {@code minecraft:blocks} + {@code block/<name>};</li>
     *   <li>items: {@code minecraft:items} + {@code item/<name>};</li>
     *   <li>text fallback - a translatable of the item's name.</li>
     * </ol>
     *
     * <p>Steps 2 and 3 branch on {@link Material#isBlock()} rather than trying one atlas and then
     * the other, because "try, and fall through on failure" is not available to us - the atlas is
     * client-side and a miss is invisible to the server. Branching on what the server <em>does</em>
     * know is the only version of that choice that can actually be made here.
     *
     * <p><strong>Blocks are the weak case, and that is the vanilla feature, not a bug here.</strong>
     * A placeable block's inventory icon is a rendered 3D model, not a flat sprite, so the blocks
     * atlas can only ever give a single face texture. {@code block/stone} looks right;
     * {@code block/crafting_table} gives one face of it; {@code block/oak_stairs} does not exist at
     * all and renders as checkerboard. Correcting those is exactly what the override table is for.
     */
    private static Component resolve(Material material) {
        String override = overrides().get(material);
        if (override != null) {
            return FORCE_TEXT.equals(override) ? text(material) : fromOverride(material, override);
        }
        if (material.isLegacy()) {
            // Pre-1.13 leftovers have no meaningful translation key, and asking for one routes
            // through UnsafeValues.fromLegacy. Use the enum name directly rather than risk it.
            return Component.text(prettify(material.name()));
        }
        if (material.isAir()) {
            return text(material);
        }
        String name = material.getKey().value();
        if (material.isBlock()) {
            return sprite(BLOCKS_ATLAS, "block/" + name, material);
        }
        if (material.isItem()) {
            return sprite(ITEMS_ATLAS, "item/" + name, material);
        }
        return text(material);
    }

    /** A sprite component carrying the item's name as its client-side fallback. */
    private static Component sprite(Key atlas, String path, Material material) {
        return Component.object(ObjectContents.sprite(atlas, Key.key(path)))
                .fallback(text(material));
    }

    /**
     * The text fallback: the item's own name, translated per viewer.
     *
     * <p>{@code translationKey()} can reach into {@code UnsafeValues} for odd materials. This is a
     * display path on the way to a chat message, so a material that cannot produce a key degrades
     * to its prettified enum name rather than propagating out of {@link #of} and taking the whole
     * message with it.
     */
    private static Component text(Material material) {
        try {
            return Component.translatable(material.translationKey());
        } catch (RuntimeException ex) {
            warnOnce("translationKey:" + material.name());
            return Component.text(prettify(material.name()));
        }
    }

    private static Component fromOverride(Material material, String value) {
        int split = value.indexOf('|');
        if (split <= 0 || split == value.length() - 1) {
            warnOnce("sprites.yml:" + material.name());
            return text(material);
        }
        try {
            return Component.object(ObjectContents.sprite(
                            Key.key(value.substring(0, split)),
                            Key.key(value.substring(split + 1))))
                    .fallback(text(material));
        } catch (RuntimeException ex) {
            warnOnce("sprites.yml:" + material.name());
            return text(material);
        }
    }

    // ----------------------------------------------------------- override file

    /**
     * The bundled override table, loaded once from the jar and never copied into a plugin's data
     * folder - this is library data, not user config, and a stale per-server copy of it would be a
     * bug rather than a customisation.
     *
     * <p>Ships with only entries that have been verified in-game. An unverified guess here is worse
     * than no entry at all, because the convention chain would have produced the same result
     * without pretending to be authoritative.
     */
    private static Map<Material, String> overrides() {
        Map<Material, String> local = overrides;
        if (local != null) {
            return local;
        }
        synchronized (CACHE_LOCK) {
            if (overrides == null) {
                overrides = loadOverrides();
            }
            return overrides;
        }
    }

    private static Map<Material, String> loadOverrides() {
        Map<Material, String> loaded = new EnumMap<>(Material.class);
        try (InputStream in = Sprites.class.getResourceAsStream(OVERRIDES_RESOURCE)) {
            if (in == null) {
                return Collections.unmodifiableMap(loaded);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                ConfigurationSection section = yaml.getConfigurationSection("overrides");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        Material material = matchMaterial(key);
                        String value = section.getString(key);
                        if (material == null || value == null || value.isBlank()) {
                            warnOnce("sprites.yml:" + key);
                            continue;
                        }
                        loaded.put(material, value.trim());
                    }
                }
            }
        } catch (Exception ex) {
            // A malformed bundled table must not take the library down; the convention chain and
            // the text fallback both still work without it.
            LOG.warning("StoneLib: could not read bundled sprites.yml (" + ex.getMessage()
                    + "); using convention sprite mapping only.");
        }
        return Collections.unmodifiableMap(loaded);
    }

    // ---------------------------------------------------------------- helpers

    /** Case-insensitive, {@code minecraft:} optional. Null when nothing matches. */
    private static Material matchMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Logs {@code value} at WARNING the first time it is seen and never again.
     *
     * <p>A bad material in a template is parsed on every send, so an unconditional log here would
     * fill the console from a boss bar ticking twenty times a second.
     */
    private static void warnOnce(String value) {
        if (WARNED.add(value)) {
            LOG.warning("StoneLib: could not resolve a sprite for '" + value
                    + "'; falling back to text.");
        }
    }

    /** "diamond_sword" / "minecraft:diamond_sword" -> "Diamond Sword", for the unknown-name case. */
    private static String prettify(String raw) {
        String name = raw.trim();
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(name.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return out.length() == 0 ? raw : out.toString();
    }
}
