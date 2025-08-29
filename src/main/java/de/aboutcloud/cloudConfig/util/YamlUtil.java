package de.aboutcloud.cloudConfig.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class YamlUtil {
    private YamlUtil() {}

    public static void setSmart(FileConfiguration cfg, String path, String value) {
        Object parsed = parse(value);
        cfg.set(path, parsed);
    }

    private static Object parse(String raw) {
        String t = raw.trim();
        if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) return Boolean.parseBoolean(t);
        try { return Integer.parseInt(t); } catch (NumberFormatException ignored) {}
        try { return Long.parseLong(t); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(t); } catch (NumberFormatException ignored) {}
        return raw;
    }

    public static boolean pathExists(FileConfiguration cfg, String path) {
        if (cfg.isSet(path)) return true;
        String parent = path.contains(".") ? path.substring(0, path.lastIndexOf('.')) : "";
        ConfigurationSection sec = parent.isEmpty() ? cfg : cfg.getConfigurationSection(parent);
        return sec != null;
    }
}
