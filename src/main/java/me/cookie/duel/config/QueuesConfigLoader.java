package me.cookie.duel.config;

import me.cookie.duel.config.model.QueueDefinition;
import me.cookie.duel.config.model.QueuesConfig;
import me.cookie.duel.duel.DuelModeType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public final class QueuesConfigLoader {

    public QueuesConfig load(FileConfiguration config) {
        ConfigurationSection queuesSection = MainConfigLoader.requireSection(config, "queues");
        Map<String, QueueDefinition> queues = new LinkedHashMap<>();

        for (String queueId : queuesSection.getKeys(false)) {
            ConfigurationSection section = MainConfigLoader.requireSection(queuesSection, queueId);
            DuelModeType mode;
            try {
                mode = DuelModeType.valueOf(MainConfigLoader.requireString(section, "mode").toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new ConfigurationException("Invalid queue mode for queue '" + queueId + "'");
            }

            QueueDefinition definition = new QueueDefinition(
                    queueId,
                    section.getBoolean("enabled"),
                    MainConfigLoader.requireString(section, "display-name"),
                    mode,
                    section.getBoolean("confirm-required"),
                    section.getString("template")
            );
            queues.put(queueId, definition);
        }

        return new QueuesConfig(Map.copyOf(queues));
    }
}
