package dev.anchorlight.StoneLib;

import dev.anchorlight.StoneLib.config.ConfigUpdater;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility class for doing respectful copies of resources to server environment.
 *
 * @deprecated superseded by {@link ConfigUpdater}, which does the same merge but also supports
 *         versioned migrations (renames and removals) when the bundled resource declares a
 *         {@code config-version}. This class now delegates to it, so existing callers keep working
 *         and gain versioning as soon as their resource declares a version.
 */
@Deprecated
public final class CopyResources {

    private CopyResources() {
        throw new IllegalStateException("Utility class shouldn't be instantiated");
    }

    /// Mirror hierarchy and fields from embedded file at 'resources/<filepath>'.
    /// Server environment will have a corresponding file in plugins data folder.
    public static void mirror(JavaPlugin plugin, String filepath) {
        ConfigUpdater.update(plugin, filepath);
    }
}
