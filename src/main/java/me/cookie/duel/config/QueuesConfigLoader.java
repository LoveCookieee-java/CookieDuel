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
        ConfigurationSection queuesSection = config.getConfigurationSection("queues");
        Map<String, QueueDefinition> queues = new LinkedHashMap<>();
        if (queuesSection == null) {
            return new QueuesConfig(Map.of());
        }

        for (String queueId : queuesSection.getKeys(false)) {
            ConfigurationSection section = MainConfigLoader.requireSection(queuesSection, queueId);
            DuelModeType mode = DuelModeType.fromInput(MainConfigLoader.requireString(section, "mode"));
            if (mode == null) {
                throw new ConfigurationException("Invalid queue mode for queue '" + queueId + "'");
            }

            QueueDefinition definition = new QueueDefinition(
                    queueId,
                    section.getBoolean("enabled"),
                    MainConfigLoader.requireString(section, "display-name"),
                    mode,
                    section.getString("template")
            );
            queues.put(queueId, definition);
        }

        return new QueuesConfig(Map.copyOf(queues));
    }
}
