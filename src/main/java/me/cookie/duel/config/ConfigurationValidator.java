package me.cookie.duel.config;

import me.cookie.duel.config.model.ArenaTemplateDefinition;
import me.cookie.duel.config.model.MainConfig;
import me.cookie.duel.config.model.QueueDefinition;
import me.cookie.duel.config.model.QueuesConfig;
import me.cookie.duel.config.model.WorldsConfig;
import me.cookie.duel.duel.DuelModeType;
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
                        "Configured WILD world '" + worldsConfig.wild().world() + "' is not loaded. "
                                + "CookieDuel requires the WILD world to already be loaded and does not auto-load it."
                );
            }
        }

        Path worldContainer = Bukkit.getWorldContainer().toPath();
        for (QueueDefinition queue : queuesConfig.queues().values()) {
            if (queue.mode() == DuelModeType.ARENA_INSTANCE) {
                if (queue.templateId() == null || queue.templateId().isBlank()) {
                    throw new ConfigurationException("ARENA_INSTANCE queue '" + queue.id() + "' is missing a template id.");
                }

                ArenaTemplateDefinition template = worldsConfig.arenaTemplates().get(queue.templateId());
                if (template == null || !template.enabled()) {
                    throw new ConfigurationException("Queue '" + queue.id() + "' references missing or disabled template '" + queue.templateId() + "'.");
                }

                Path templatePath = worldContainer.resolve(template.templateWorldName());
                if (!Files.isDirectory(templatePath)) {
                    throw new ConfigurationException(
                            "Template world folder does not exist for queue '" + queue.id() + "': " + templatePath
                                    + ". CookieDuel requires ARENA_INSTANCE template worlds to exist on disk before startup."
                    );
                }
            }
        }
    }
}
