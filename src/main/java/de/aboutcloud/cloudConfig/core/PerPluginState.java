package de.aboutcloud.cloudConfig.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

final class PerPluginState {
    final JavaPlugin plugin;
    final Map<String, FileConfiguration> configs = new HashMap<>();
    final Map<Locale, LocaleBundle> bundles = new HashMap<>();
    final Set<Locale> availableLocales = new TreeSet<>(Comparator.comparing(Locale::toLanguageTag));
    Locale defaultLocale = Locale.ENGLISH;

    PerPluginState(JavaPlugin plugin) { this.plugin = plugin; }
}
