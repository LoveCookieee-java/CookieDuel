package me.cookie.duel.config;

import me.cookie.duel.config.model.BlacklistConfig;
import me.cookie.duel.config.model.MainConfig;
import me.cookie.duel.config.model.QueuesConfig;
import me.cookie.duel.config.model.WorldsConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ConfigService {

    private final JavaPlugin plugin;
    private final BlacklistConfigLoader blacklistConfigLoader;
    private final MainConfigLoader mainConfigLoader;
    private final QueuesConfigLoader queuesConfigLoader;
    private final WorldsConfigLoader worldsConfigLoader;
    private final ConfigurationValidator validator;

    private BlacklistConfig blacklistConfig;
    private MainConfig mainConfig;
    private QueuesConfig queuesConfig;
    private WorldsConfig worldsConfig;
    private FileConfiguration messagesConfig;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.blacklistConfigLoader = new BlacklistConfigLoader(plugin.getLogger());
        this.mainConfigLoader = new MainConfigLoader();
        this.queuesConfigLoader = new QueuesConfigLoader();
        this.worldsConfigLoader = new WorldsConfigLoader();
        this.validator = new ConfigurationValidator();
    }

    public void load() {
        saveDefaultFiles();

        FileConfiguration mainYaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        FileConfiguration queuesYaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "queues.yml"));
        FileConfiguration worldsYaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "worlds.yml"));
        FileConfiguration blacklistYaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "blacklist.yml"));
        FileConfiguration messagesYaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));

        this.blacklistConfig = blacklistConfigLoader.load(blacklistYaml);
        this.mainConfig = mainConfigLoader.load(mainYaml);
        this.queuesConfig = queuesConfigLoader.load(queuesYaml);
        this.worldsConfig = worldsConfigLoader.load(worldsYaml);
        this.messagesConfig = messagesYaml;

        validator.validate(mainConfig, queuesConfig, worldsConfig);
    }

    public MainConfig mainConfig() {
        return mainConfig;
    }

    public BlacklistConfig blacklistConfig() {
        return blacklistConfig;
    }

    public QueuesConfig queuesConfig() {
        return queuesConfig;
    }

    public WorldsConfig worldsConfig() {
        return worldsConfig;
    }

    public FileConfiguration messagesConfig() {
        return messagesConfig;
    }

    private void saveDefaultFiles() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new ConfigurationException("Failed to create plugin data folder.");
        }

        saveDefaultFile("config.yml");
        saveDefaultFile("queues.yml");
        saveDefaultFile("worlds.yml");
        saveDefaultFile("blacklist.yml");
        saveDefaultFile("messages.yml");
    }

    private void saveDefaultFile(String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
    }
}
