package de.aboutcloud.cloudConfig;

import de.aboutcloud.cloudConfig.api.config.CloudConfigService;
import de.aboutcloud.cloudConfig.api.databasse.CloudDatabaseService;
import de.aboutcloud.cloudConfig.command.CloudConfigCommand;
import de.aboutcloud.cloudConfig.core.CloudConfigServiceImpl;
import de.aboutcloud.cloudConfig.core.CloudDatabaseServiceImpl;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudConfig extends JavaPlugin {

    private CloudConfigServiceImpl ccs;
    private CloudDatabaseServiceImpl cdbs;

    @Override
    public void onEnable() {
        this.ccs = new CloudConfigServiceImpl(this);
        getServer().getServicesManager().register(CloudConfigService.class, ccs, this, ServicePriority.Normal);

        this.cdbs = new CloudDatabaseServiceImpl(this, ccs);
        getServer().getServicesManager().register(CloudDatabaseService.class, cdbs, this, ServicePriority.Normal);


        var cmd = getCommand("cloudconfig");
        if (cmd != null) {
            var executor = new CloudConfigCommand(ccs);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getSLF4JLogger().info("CloudConfig enabled");
    }

    @Override
    public void onDisable() {
        if (ccs != null) ccs.shutdown();
        getSLF4JLogger().info("CloudConfig disabled");
    }
}
