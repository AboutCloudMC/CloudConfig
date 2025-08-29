package de.aboutcloud.cloudConfig.command;

import de.aboutcloud.cloudConfig.api.CloudConfigService;
import de.aboutcloud.cloudConfig.util.YamlUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CloudConfigCommand implements CommandExecutor, TabCompleter {
    private final CloudConfigService service;

    public CloudConfigCommand(CloudConfigService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("cloudconfig.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <reload|listlocales|set> ...");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (args.length < 2) return usage(sender, label, "reload <PluginName>");
                JavaPlugin plugin = requirePlugin(sender, args[1]);
                if (plugin == null) return true;
                service.reload(plugin);
                sender.sendMessage(ChatColor.GREEN + "Reloaded configs and locales for " + plugin.getName());
            }
            case "listlocales" -> {
                if (args.length < 2) return usage(sender, label, "listlocales <PluginName>");
                JavaPlugin plugin = requirePlugin(sender, args[1]);
                if (plugin == null) return true;
                Set<Locale> locales = service.getAvailableLocales(plugin);
                sender.sendMessage(ChatColor.AQUA + "Locales for " + plugin.getName() + ": " + locales);
            }
            case "set" -> {
                // /cloudconfig set <PluginName> <configFile.yml|locale/<tag>.yml> <path> <value...>
                if (args.length < 5) return usage(sender, label, "set <PluginName> <file> <path> <value...>");
                JavaPlugin plugin = requirePlugin(sender, args[1]);
                if (plugin == null) return true;

                String file = args[2];
                String path = args[3];
                String value = joinFrom(args, 4);

                if (!file.contains("/")) {
                    FileConfiguration cfg = service.getConfig(plugin, file);
                    if (!YamlUtil.pathExists(cfg, path)) {
                        sender.sendMessage(ChatColor.RED + "Path not found (parent may be missing): " + path);
                        return true;
                    }
                    YamlUtil.setSmart(cfg, path, value);
                    service.saveConfig(plugin, file);
                    sender.sendMessage(ChatColor.GREEN + "Set " + path + " = " + value + " in " + file);
                    return true;
                }

                if (file.toLowerCase(Locale.ROOT).startsWith("locale/")) {
                    var p = plugin.getDataFolder().toPath().resolve(file);
                    try {
                        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(p.toFile());
                        if (!YamlUtil.pathExists(yaml, path)) {
                            sender.sendMessage(ChatColor.RED + "Path not found (parent may be missing): " + path);
                            return true;
                        }
                        YamlUtil.setSmart(yaml, path, value);
                        yaml.save(p.toFile());
                        service.reload(plugin); // rescan locales
                        sender.sendMessage(ChatColor.GREEN + "Updated " + path + " in " + file);
                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "Failed to update: " + e.getMessage());
                    }
                    return true;
                }

                sender.sendMessage(ChatColor.RED + "Unknown file type: " + file);
            }
            default -> usage(sender, label, "<reload|listlocales|set>");
        }
        return true;
    }

    private boolean usage(CommandSender s, String l, String u) {
        s.sendMessage(ChatColor.YELLOW + "Usage: /" + l + " " + u);
        return true;
    }

    private static JavaPlugin requirePlugin(CommandSender s, String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        if (!(p instanceof JavaPlugin jp)) {
            s.sendMessage(ChatColor.RED + "Plugin not found or not a JavaPlugin: " + name);
            return null;
        }
        return jp;
    }

    private static String joinFrom(String[] arr, int idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < arr.length; i++) {
            if (i > idx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        switch (a.length) {
            case 1 -> out = List.of("reload","listlocales","set");
            case 2 -> {
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) out.add(p.getName());
            }
            default -> {}
        }
        return out;
    }
}
