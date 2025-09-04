package de.aboutcloud.cloudConfig.api.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface CloudConfigService {

    /** Register a plugin. */
    void register(JavaPlugin plugin, CloudConfigRegistration registration);

    /** Return a loaded config. */
    FileConfiguration getConfig(JavaPlugin plugin, String fileName);

    /** Save a loaded config to disk. */
    void saveConfig(JavaPlugin plugin, String fileName);

    /** Reload all configs and locales for a plugin. */
    void reload(JavaPlugin plugin);

    /** Available locales (plugins/<PluginName>/locale/*.yml) */
    Set<Locale> getAvailableLocales(JavaPlugin plugin);

    /** Loaded MiniMessage to Component incl. placeholders. */
    Component message(JavaPlugin plugin, String key, Locale locale, Map<String, Object> placeholders);

    /** Send a message to an audience (uses Player locale if possible). */
    void send(Audience audience, JavaPlugin plugin, String key, Map<String, Object> placeholders);
}
