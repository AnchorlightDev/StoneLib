package dev.anchorlight.stonelib.message;

import dev.anchorlight.stonelib.config.ConfigUpdater;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
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
    private final TagResolver[] extraResolvers;
    private File file;
    private FileConfiguration messages;

    public MessageService(JavaPlugin plugin, String fileName) {
        this(plugin, fileName, new TagResolver[0]);
    }

    /**
     * As {@link #MessageService(JavaPlugin, String)}, plus tag resolvers of this plugin's own that
     * every message from this instance can use.
     *
     * <p>The usual reason is an item-sprite tag:
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
     * <p>Extras are held on this instance rather than registered on the shared {@code MiniMessage}
     * instance, deliberately. Registering there would be global state - StoneLib has none by design
     * - and would give the tag to every plugin in the JVM whether it opted in or not. A plugin that
     * passes no extras is unaffected in every respect.
     *
     * <p>Named placeholders passed at the call site are resolved ahead of these, so a per-call
     * value takes precedence over an extra resolver sharing its name.
     *
     * @param extra resolvers to make available to every template this instance parses
     */
    public MessageService(JavaPlugin plugin, String fileName, TagResolver... extra) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.extraResolvers = extra == null ? new TagResolver[0] : extra.clone();
        reload();
    }

    /**
     * Reloads the messages file, bringing it up to date with the bundled resource first. Without
     * that, a plugin update that adds a message would read "[Missing message: ...]" on every server
     * that already had the old file, and a renamed key could never be migrated at all.
     */
    public void reload() {
        file = new File(plugin.getDataFolder(), fileName);
        messages = ConfigUpdater.update(plugin, fileName);
    }

    /**
     * Resolves {@code key} to a Component, substituting {@code {0}}, {@code {1}}, ... with
     * {@code placeholders} in call order.
     *
     * <p>Placeholder values are substituted as inert text via MiniMessage's own
     * {@link Placeholder#unparsed(String, String)} mechanism, never by concatenating them into the
     * template string before parsing. The message TEMPLATE (from {@code messages.yml}) is
     * plugin-authored and trusted, so its own MiniMessage tags (e.g. {@code <red>}) are parsed
     * normally — but a placeholder VALUE is data, and if that data can come from outside the
     * plugin's own config (a player's name, chat input, an external API response, anything not
     * authored by you), naively concatenating it into the template before deserializing would let
     * it inject live MiniMessage tags of its own (e.g. a player named
     * {@code <click:run_command:/op x>} could execute arbitrary commands via anyone who receives a
     * message containing their name). Sanitize genuinely untrusted values with
     * {@link UntrustedText#forDisplay(String, int)} before passing them here as an extra
     * defense-in-depth layer, even though {@code unparsed} placeholders are already safe from
     * tag injection on their own.
     */
    public Component get(String key, Object... placeholders) {
        String raw = messages.getString(key, "[Missing message: " + key + "]");
        String prefix = messages.getString("prefix", "");
        String template = prefix + raw;

        TagResolver.Builder resolvers = TagResolver.builder();
        for (int i = 0; i < placeholders.length; i++) {
            String tag = "stonelib_arg" + i;
            template = template.replace("{" + i + "}", "<" + tag + ">");
            resolvers.resolver(Placeholder.unparsed(tag, String.valueOf(placeholders[i])));
        }
        for (TagResolver extra : extraResolvers) {
            resolvers.resolver(extra);
        }

        return miniMessage.deserialize(template, resolvers.build());
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void sendActionBar(Player player, String key, Object... placeholders) {
        player.sendActionBar(get(key, placeholders));
    }

    // ------------------------------------------------------- named placeholders

    /**
     * The raw template text for {@code key}, with no prefix and nothing parsed.
     *
     * <p>Titles, subtitles, scoreboard lines and item names all need the template without the chat
     * prefix glued on, and often need to be parsed later than they are looked up.
     */
    public String raw(String key) {
        return messages.getString(key, "[Missing message: " + key + "]");
    }

    /** True when {@code key} exists and is not blank - for messages that are optional by design. */
    public boolean has(String key) {
        String value = messages.getString(key);
        return value != null && !value.isBlank();
    }

    /**
     * Resolves {@code key} with <em>named</em> placeholders, so the template reads
     * {@code "<player> earned <points>"} rather than {@code "{0} earned {1}"}. The chat prefix is
     * applied, as in {@link #get(String, Object...)}.
     *
     * <p>Positional placeholders are fine for short messages, but they get hard to author and to
     * re-order once a template carries three or four values.
     *
     * @param keyValuePairs placeholder name, then value, repeated
     * @see MiniMessages
     */
    public Component getNamed(String key, Object... keyValuePairs) {
        return parseNamed(messages.getString("prefix", "") + raw(key), keyValuePairs);
    }

    /** As {@link #getNamed}, without the chat prefix - for titles, action bars and sidebars. */
    public Component getNamedUnprefixed(String key, Object... keyValuePairs) {
        return parseNamed(raw(key), keyValuePairs);
    }

    /**
     * Parses a named-placeholder template with this instance's extra resolvers appended.
     *
     * <p>With no extras this is exactly {@link MiniMessages#parse}: the same stock
     * {@code MiniMessage} instance and the same resolvers, so output is unchanged for every
     * existing caller.
     */
    private Component parseNamed(String template, Object... keyValuePairs) {
        if (extraResolvers.length == 0) {
            return MiniMessages.parse(template, keyValuePairs);
        }
        TagResolver[] named = MiniMessages.resolvers(keyValuePairs);
        TagResolver[] all = new TagResolver[named.length + extraResolvers.length];
        System.arraycopy(named, 0, all, 0, named.length);
        System.arraycopy(extraResolvers, 0, all, named.length, extraResolvers.length);
        return miniMessage.deserialize(template == null ? "" : template, all);
    }

    /** Sends a named-placeholder message to any audience, including the whole server. */
    public void sendNamed(Audience audience, String key, Object... keyValuePairs) {
        if (audience == null || !has(key)) {
            return;
        }
        audience.sendMessage(getNamed(key, keyValuePairs));
    }

    /** Sends a named-placeholder action bar. Action bars are never prefixed. */
    public void sendNamedActionBar(Audience audience, String key, Object... keyValuePairs) {
        if (audience == null || !has(key)) {
            return;
        }
        audience.sendActionBar(getNamedUnprefixed(key, keyValuePairs));
    }

    /** Shows a title built from two unprefixed message keys. Either key may be absent. */
    public void showTitle(Audience audience, String titleKey, String subtitleKey,
                          Title.Times times, Object... keyValuePairs) {
        if (audience == null) {
            return;
        }
        audience.showTitle(Title.title(
                getNamedUnprefixed(titleKey, keyValuePairs),
                getNamedUnprefixed(subtitleKey, keyValuePairs),
                times));
    }
}
