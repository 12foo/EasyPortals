package de.teamalbin.spigotmc.easyportals;

import de.teamalbin.spigotmc.easyportals.nms.NMSInterface;
import de.teamalbin.spigotmc.easyportals.nms.NMSInterface_1_12;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class EasyPortals extends JavaPlugin {
    private NMSInterface nms;
    private PortalManager portals;
    private PlayerInteraction interaction;

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
        String[] version = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        switch (version[version.length-1]) {
            case "v1_12_R1":
                this.nms = new NMSInterface_1_12();
                break;
            default:
                getLogger().severe("You're running a Minecraft version " + version[version.length-1] + " server.");
                getLogger().severe("EasyPortals is not supported on this version. Deactivating plugin-- sorry!");
                Bukkit.getPluginManager().disablePlugin(this);
        }


        try {
            File portalconfig = new File(getDataFolder(), "portals.yml");
            this.portals = new PortalManager(nms, portalconfig);
            if (new File(getDataFolder(), "portals.yml").exists()) this.portals.loadConfig();
            else {
                portalconfig.getParentFile().mkdirs();
                saveResource("portals.yml", false);
            }
        } catch (IOException ioex) {
            getLogger().severe("Could not load portal configuration. (" + ioex.getMessage() + ")");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        } catch (InvalidConfigurationException icex) {
            getLogger().severe("Could not parse portal configuration. (" + icex.getMessage() + ")");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.interaction = new PlayerInteraction(this.portals);
        this.getCommand("portal").setExecutor(this.interaction);
        getServer().getPluginManager().registerEvents(this.interaction, this);
    }
}
