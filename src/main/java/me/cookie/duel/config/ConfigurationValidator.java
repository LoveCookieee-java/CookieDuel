package me.cookie.duel.config;

import me.cookie.duel.config.model.MainConfig;
import me.cookie.duel.config.model.QueuesConfig;
import me.cookie.duel.config.model.WorldsConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigurationValidator {

    public void validate(MainConfig mainConfig, QueuesConfig queuesConfig, WorldsConfig worldsConfig) {
        if (mainConfig.modes().wildEnabled()) {
            World wildWorld = Bukkit.getWorld(worldsConfig.wild().world());
            if (wildWorld == null) {
                throw new ConfigurationException(
                        "WILD world '" + worldsConfig.wild().world() + "' is not loaded. "
                                + "Load it before startup; CookieDuel will not auto-load it."
                );
            }
        }

        Path worldContainer = Bukkit.getWorldContainer().toPath();
        if (mainConfig.modes().arenaInstance().enabled()) {
            if (worldsConfig.arenaTemplates().isEmpty()) {
                throw new ConfigurationException("ARENA_INSTANCE is enabled, but no arena templates are configured.");
            }

            String defaultTemplateId = worldsConfig.defaultArenaTemplateId();
            if (defaultTemplateId == null || defaultTemplateId.isBlank()) {
                throw new ConfigurationException("ARENA_INSTANCE needs a default template id in worlds.yml.");
            }

            var defaultTemplate = worldsConfig.arenaTemplates().get(defaultTemplateId);
            if (defaultTemplate == null || !defaultTemplate.enabled()) {
                throw new ConfigurationException("Default arena template '" + defaultTemplateId + "' is missing or disabled.");
            }

            Path templatePath = worldContainer.resolve(defaultTemplate.templateWorldName());
            if (!Files.isDirectory(templatePath)) {
                throw new ConfigurationException(
                        "Default arena template folder was not found: " + templatePath
                                + ". Create the template world before startup."
                );
            }
        }
    }
}
