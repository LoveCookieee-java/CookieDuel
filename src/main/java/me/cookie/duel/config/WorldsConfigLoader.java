package me.cookie.duel.config;

import me.cookie.duel.config.model.ArenaTemplateDefinition;
import me.cookie.duel.config.model.InstanceWorldSettings;
import me.cookie.duel.config.model.SerializableLocation;
import me.cookie.duel.config.model.WildLocationSettings;
import me.cookie.duel.config.model.WorldsConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldsConfigLoader {

    public WorldsConfig load(FileConfiguration config) {
        ConfigurationSection wildSection = MainConfigLoader.requireSection(config, "wild");
        ConfigurationSection randomLocationSection = MainConfigLoader.requireSection(wildSection, "random-location");
        ConfigurationSection terrainValidationSection = wildSection.getConfigurationSection("terrain-validation");
        ConfigurationSection arenaSection = MainConfigLoader.requireSectionAny(config, "arena", "arena-instance");
        ConfigurationSection templatesSection = MainConfigLoader.requireSection(arenaSection, "templates");

        WildLocationSettings wild = new WildLocationSettings(
                MainConfigLoader.requireString(wildSection, "world"),
                wildSection.getBoolean("use-random-safe-location"),
                wildSection.getDouble("spawn-distance"),
                wildSection.getInt("max-y-difference"),
                randomLocationSection.getInt("min-radius"),
                randomLocationSection.getInt("max-radius"),
                randomLocationSection.getInt("max-attempts"),
                randomLocationSection.getBoolean("avoid-water"),
                randomLocationSection.getBoolean("avoid-lava"),
                randomLocationSection.getBoolean("avoid-dangerous-blocks"),
                randomLocationSection.getInt("avoid-nearby-players-radius"),
                getInt(terrainValidationSection, "local-sample-radius", 2),
                getInt(terrainValidationSection, "max-local-height-spread", 2),
                getInt(terrainValidationSection, "max-local-height-spread-difference", 1),
                getInt(terrainValidationSection, "edge-check-radius", 2),
                getInt(terrainValidationSection, "max-edge-drop", 3),
                getInt(terrainValidationSection, "nearby-hazard-check-radius", 2),
                getInt(terrainValidationSection, "nearby-obstruction-check-radius", 1)
        );

        Map<String, ArenaTemplateDefinition> templates = new LinkedHashMap<>();
        for (String templateId : templatesSection.getKeys(false)) {
            ConfigurationSection templateSection = MainConfigLoader.requireSection(templatesSection, templateId);
            ConfigurationSection spawnASection = MainConfigLoader.requireSection(templateSection, "spawn-a");
            ConfigurationSection spawnBSection = MainConfigLoader.requireSection(templateSection, "spawn-b");
            ConfigurationSection settingsSection = MainConfigLoader.requireSection(templateSection, "settings");

            ArenaTemplateDefinition template = new ArenaTemplateDefinition(
                    templateId,
                    templateSection.getBoolean("enabled"),
                    MainConfigLoader.requireString(templateSection, "template-world-name"),
                    MainConfigLoader.requireString(templateSection, "instance-world-prefix"),
                    loadLocation(spawnASection),
                    loadLocation(spawnBSection),
                    new InstanceWorldSettings(
                            settingsSection.getBoolean("auto-set-gamerules"),
                            settingsSection.getBoolean("allow-block-break"),
                            settingsSection.getBoolean("allow-block-place"),
                            settingsSection.getDouble("border-size")
                    )
            );

            templates.put(templateId, template);
        }

        return new WorldsConfig(
                wild,
                arenaSection.getString("default-template", "default"),
                Map.copyOf(templates)
        );
    }

    private SerializableLocation loadLocation(ConfigurationSection section) {
        return new SerializableLocation(
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private int getInt(ConfigurationSection section, String path, int fallback) {
        return section == null ? fallback : section.getInt(path, fallback);
    }
}
