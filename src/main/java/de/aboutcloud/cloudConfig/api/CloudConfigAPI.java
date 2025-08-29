package de.aboutcloud.cloudConfig.api;

import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudConfigAPI {
    private CloudConfigAPI() {}

    public static CloudConfigService get(JavaPlugin plugin) {
        ServicesManager sm = plugin.getServer().getServicesManager();
        CloudConfigService svc = sm.load(CloudConfigService.class);
        if (svc == null) throw new IllegalStateException("CloudConfig service not available");
        return svc;
    }
}
