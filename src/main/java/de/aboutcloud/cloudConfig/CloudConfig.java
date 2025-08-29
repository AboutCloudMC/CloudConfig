package de.aboutcloud.cloudConfig;

import de.aboutcloud.cloudConfig.api.CloudConfigService;
import de.aboutcloud.cloudConfig.command.CloudConfigCommand;
import de.aboutcloud.cloudConfig.core.CloudConfigServiceImpl;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudConfig extends JavaPlugin {

    private CloudConfigServiceImpl service;

    @Override
    public void onEnable() {
        this.service = new CloudConfigServiceImpl(this);
        getServer().getServicesManager().register(
                CloudConfigService.class, service, this, ServicePriority.Normal);

        var cmd = getCommand("cloudconfig");
        if (cmd != null) {
            var executor = new CloudConfigCommand(service);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getSLF4JLogger().info("CloudConfig enabled");
    }

    @Override
    public void onDisable() {
        if (service != null) service.shutdown();
        getSLF4JLogger().info("CloudConfig disabled");
    }
}
