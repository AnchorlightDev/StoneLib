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
