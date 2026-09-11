package dev.anchorlight.stonelib.messaging.proxy;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The proxy (Velocity) half of the message bus: it receives a message from one backend and fans it
 * out to every other backend.
 *
 * <p>This class is only ever loaded on a Velocity proxy. The rest of StoneLib never touches it, and
 * {@code velocity-api} is an optional dependency, so it is simply absent on a Paper server.</p>
 *
 * <p>Register it from a Velocity plugin:</p>
 *
 * <pre>
 * &#64;Inject
 * public EdenEffectsProxy(ProxyServer proxy, Logger logger) {
 *     ProxyMessageRelay relay = new ProxyMessageRelay(proxy, logger, "edeneffects:sync");
 *     relay.register(this);
 * }
 * </pre>
 *
 * <p>The relay does not read the message body beyond what it needs to route: the type, origin and
 * payload stay opaque, and the bytes forwarded are the bytes received. It skips the origin server
 * so a message never returns to the backend that sent it.</p>
 */
public final class ProxyMessageRelay {

    private final ProxyServer proxy;
    private final Logger logger;
    private final ChannelIdentifier channel;

    /**
     * @param channel the {@code namespace:name} channel, matching the one the backends use
     */
    public ProxyMessageRelay(ProxyServer proxy, Logger logger, String channel) {
        this.proxy = proxy;
        this.logger = logger;
        String[] parts = channel.toLowerCase(Locale.ROOT).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Channel must be namespace:name, got: " + channel);
        }
        this.channel = MinecraftChannelIdentifier.create(parts[0], parts[1]);
    }

    /**
     * Registers the channel and this listener with the proxy.
     *
     * @param plugin the Velocity plugin instance the listener belongs to
     */
    public void register(Object plugin) {
        proxy.getChannelRegistrar().register(channel);
        proxy.getEventManager().register(plugin, this);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.equals(event.getIdentifier())) {
            return;
        }
        // Consume it either way: this is server-to-server traffic and must never reach a client.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection source)) {
            // A client sent something on our channel. Dropping it is the whole point of the check.
            return;
        }

        String originName = source.getServerInfo().getName();
        byte[] data = event.getData();

        for (RegisteredServer target : proxy.getAllServers()) {
            if (target.getServerInfo().getName().equals(originName)) {
                continue;
            }
            if (target.getPlayersConnected().isEmpty()) {
                // Nothing to carry the message. The backend re-reads from the database on join.
                continue;
            }
            try {
                target.sendPluginMessage(channel, data);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Failed to relay a message to " + target.getServerInfo().getName(), e);
            }
        }
    }
}
