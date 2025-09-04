package de.aboutcloud.cloudConfig.api.databasse;

import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudDatabaseAPI {
    private CloudDatabaseAPI() {}
    public static CloudDatabaseService get(JavaPlugin plugin) {
        ServicesManager sm = plugin.getServer().getServicesManager();
        CloudDatabaseService svc = sm.load(CloudDatabaseService.class);
        if (svc == null) throw new IllegalStateException("CloudDatabaseService not available");
        return svc;
    }
}
