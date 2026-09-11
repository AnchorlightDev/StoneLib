package dev.anchorlight.StoneLib.messaging;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * The backend (Paper) half of a cross-server message bus, carried over a plugin-message channel
 * and fanned out by a proxy-side relay.
 *
 * <pre>
 * MessageBus bus = new MessageBus(plugin, "edeneffects:sync", config.getString("server-id"));
 * bus.subscribe("cosmetic-changed", message -&gt; cache.invalidate(UUID.fromString(message.payload())));
 * bus.register();
 * ...
 * bus.publish("cosmetic-changed", uuid.toString());
 * </pre>
 *
 * <h2>What this can and cannot promise</h2>
 *
 * <p>Plugin messages ride a player's connection, so {@link #publish} does nothing when no player is
 * online — and the proxy can only reach a backend that has one. Delivery is therefore best-effort.
 * That is fine for the intended use, cache invalidation, because the database still holds the
 * truth and a missed message costs a stale cache until the next read. Never use the bus for state
 * that is not also persisted.</p>
 *
 * <p>Handlers run on the thread the plugin message arrives on. Touch the world from one only via
 * the scheduler.</p>
 */
public final class MessageBus implements PluginMessageListener {

    private final Plugin plugin;
    private final String channel;
    private final String serverId;
    private final Map<String, List<MessageHandler>> handlers = new ConcurrentHashMap<>();

    private boolean registered;

    /**
     * @param channel  the plugin-message channel, {@code namespace:name} in lower case, matching the
     *                 channel the proxy relay listens on
     * @param serverId this backend's identifier, stamped on every outgoing message as its origin
     */
    public MessageBus(Plugin plugin, String channel, String serverId) {
        this.plugin = plugin;
        this.channel = channel;
        this.serverId = serverId;
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("serverId must be set — the proxy uses it to skip the origin server");
        }
    }

    /** Registers the incoming and outgoing channels. Call from {@code onEnable}. */
    public void register() {
        if (registered) {
            return;
        }
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, this);
        registered = true;
    }

    /** Unregisters both channels. Call from {@code onDisable}. */
    public void unregister() {
        if (!registered) {
            return;
        }
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, channel, this);
        registered = false;
    }

    /** Adds a handler for one message type. Several handlers may share a type. */
    public void subscribe(String type, MessageHandler handler) {
        handlers.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Sends a message to every other backend, via the proxy relay.
     *
     * @return true if the message was handed to a player's connection, false if it was dropped
     *         because nobody was online to carry it
     */
    public boolean publish(String type, String payload) {
        return publish(new Message(type, serverId, payload));
    }

    /** Sends a pre-built message. Its origin should be this server's id. */
    public boolean publish(Message message) {
        if (!registered) {
            plugin.getLogger().warning("MessageBus.publish called before register(); dropping " + message.type());
            return false;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (carrier == null) {
            // Expected on an empty backend, and harmless: the database still has the change and
            // other servers will read it on their next miss.
            return false;
        }
        try {
            carrier.sendPluginMessage(plugin, channel, message.encode());
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to publish " + message.type(), e);
            return false;
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String incomingChannel, @NotNull Player player, byte[] data) {
        if (!channel.equals(incomingChannel)) {
            return;
        }
        Message message = Message.decode(data);
        if (message == null) {
            plugin.getLogger().warning("Discarded a malformed message on " + channel);
            return;
        }
        if (serverId.equals(message.origin())) {
            // Our own message, echoed back. Acting on it would undo the change that caused it.
            return;
        }
        List<MessageHandler> forType = handlers.get(message.type());
        if (forType == null) {
            return;
        }
        for (MessageHandler handler : forType) {
            try {
                handler.handle(message);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Handler for " + message.type() + " failed", e);
            }
        }
    }

    /** This backend's identifier. */
    public String serverId() {
        return serverId;
    }

    /** Receives one message. */
    @FunctionalInterface
    public interface MessageHandler {
        void handle(Message message);
    }
}
