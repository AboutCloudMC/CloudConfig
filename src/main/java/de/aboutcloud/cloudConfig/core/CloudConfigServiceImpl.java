package de.aboutcloud.cloudConfig.core;

import de.aboutcloud.cloudConfig.api.config.CloudConfigRegistration;
import de.aboutcloud.cloudConfig.api.config.CloudConfigService;
import de.aboutcloud.cloudConfig.util.LocaleUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CloudConfigServiceImpl implements CloudConfigService {
    private final JavaPlugin cloudConfigPlugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Per-plugin state (configs + locales)
    private final Map<String, PerPluginState> states = new ConcurrentHashMap<>();

    public CloudConfigServiceImpl(JavaPlugin plugin) {
        this.cloudConfigPlugin = plugin;
    }

    @Override
    public void register(JavaPlugin plugin, CloudConfigRegistration reg) {
        String key = plugin.getName();
        PerPluginState state = states.computeIfAbsent(key, k -> new PerPluginState(plugin));
        state.defaultLocale = LocaleUtil.parseLocale(reg.defaultLocaleTag());

        Path data = plugin.getDataFolder().toPath();
        ensureDir(data.resolve("config"));
        ensureDir(data.resolve("locale"));

        if (reg.copyDefaultsIfMissing()) {
            for (String cfg : reg.configFiles()) {
                Path target = data.resolve("config").resolve(cfg);
                if (Files.notExists(target)) {
                    safeSaveResource(plugin, "config/" + cfg, false);
                }
            }
            Path defLoc = data.resolve(reg.defaultLocaleFile());
            if (Files.notExists(defLoc)) {
                safeSaveResource(plugin, reg.defaultLocaleFile(), false);
            }
        }

        // Preload configs
        for (String cfg : reg.configFiles()) {
            loadConfig(state, cfg);
        }

        scanLocales(state);
    }

    @Override
    public FileConfiguration getConfig(JavaPlugin plugin, String fileName) {
        PerPluginState st = requireState(plugin);
        return st.configs.computeIfAbsent(fileName, fn -> loadConfig(st, fn));
    }

    @Override
    public void saveConfig(JavaPlugin plugin, String fileName) {
        PerPluginState st = requireState(plugin);
        FileConfiguration fc = st.configs.get(fileName);
        if (fc == null) return;
        try {
            fc.save(st.plugin.getDataFolder().toPath().resolve("config").resolve(fileName).toFile());
        } catch (Exception e) {
            cloudConfigPlugin.getSLF4JLogger().error("[CloudConfig] Failed to save {}", fileName, e);
        }
    }

    @Override
    public void reload(JavaPlugin plugin) {
        PerPluginState st = requireState(plugin);
        new ArrayList<>(st.configs.keySet()).forEach(fn -> loadConfig(st, fn));
        scanLocales(st);
    }

    @Override
    public Set<Locale> getAvailableLocales(JavaPlugin plugin) {
        return Collections.unmodifiableSet(requireState(plugin).availableLocales);
    }

    @Override
    public Component message(JavaPlugin plugin, String key, Locale requested, Map<String, Object> placeholders) {
        PerPluginState st = requireState(plugin);
        String raw = resolveMessage(st, key, requested);
        if (raw == null) raw = "<gray>Missing message: <red>" + key + "</red></gray>";
        if (placeholders != null) {
            for (var e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
            }
        }
        return mm.deserialize(raw);
    }

    @Override
    public void send(Audience audience, JavaPlugin plugin, String key, Map<String, Object> placeholders) {
        Locale loc = requireState(plugin).defaultLocale;
        if (audience instanceof Player p && p.locale() != null) {
            loc = p.locale();
        }
        audience.sendMessage(message(plugin, key, loc, placeholders));
    }

    private PerPluginState requireState(JavaPlugin plugin) {
        PerPluginState st = states.get(plugin.getName());
        if (st == null) throw new IllegalStateException("Plugin not registered in CloudConfig: " + plugin.getName());
        return st;
    }

    private FileConfiguration loadConfig(PerPluginState st, String fileName) {
        try {
            Path path = st.plugin.getDataFolder().toPath().resolve("config").resolve(fileName);
            ensureDir(path.getParent());
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(path.toFile());

            try (var in = st.plugin.getResource("config/" + fileName)) {
                if (in != null) {
                    YamlConfiguration def = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    cfg.setDefaults(def);
                    cfg.options().copyDefaults(true);
                }
            }
            st.configs.put(fileName, cfg);
            return cfg;
        } catch (Exception e) {
            cloudConfigPlugin.getSLF4JLogger().error("[CloudConfig] Failed loading config {}", fileName, e);
            return new YamlConfiguration();
        }
    }

    private void scanLocales(PerPluginState st) {
        st.bundles.clear();
        st.availableLocales.clear();
        Path dir = st.plugin.getDataFolder().toPath().resolve("locale");
        ensureDir(dir);
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".yml")).forEach(p -> {
                String base = p.getFileName().toString().replace(".yml","");
                Locale loc = LocaleUtil.parseLocale(base);
                try {
                    LocaleBundle b = LocaleBundle.load(p);
                    st.bundles.put(loc, b);
                    st.availableLocales.add(loc);
                } catch (IOException ex) {
                    cloudConfigPlugin.getSLF4JLogger().warn("[CloudConfig] Bad locale file {}: {}", p, ex.toString());
                }
            });
        } catch (IOException e) {
            cloudConfigPlugin.getSLF4JLogger().error("[CloudConfig] Failed scanning locales for {}", st.plugin.getName(), e);
        }

        if (!st.availableLocales.contains(st.defaultLocale)) {
            st.availableLocales.add(st.defaultLocale);
        }
    }

    private String resolveMessage(PerPluginState st, String key, Locale requested) {
        for (Locale c : LocaleUtil.fallbackChain(requested, st.defaultLocale)) {
            LocaleBundle b = st.bundles.get(c);
            if (b != null) {
                String v = b.get(key);
                if (v != null) return v;
            }
        }
        return null;
    }

    private static void ensureDir(Path p) {
        try { Files.createDirectories(p); } catch (IOException ignored) {}
    }

    private static void safeSaveResource(JavaPlugin plugin, String path, boolean replace) {
        try {
            plugin.saveResource(path, replace);
        } catch (IllegalArgumentException ignored) {
            // ignore exception bc its fine
        }
    }

    public void shutdown() {
        // TODO: Implement CCSImpl shutdown mechanic
    }
}
