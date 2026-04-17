package me.cookie.duel.duel.instance;

import me.cookie.duel.config.model.ArenaTemplateDefinition;
import me.cookie.duel.config.model.WorldsConfig;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class WorldTemplateManager {

    private final Path worldContainer;

    public WorldTemplateManager() {
        this.worldContainer = Bukkit.getWorldContainer().toPath();
    }

    public Path templatePath(ArenaTemplateDefinition templateDefinition) {
        return worldContainer.resolve(templateDefinition.templateWorldName());
    }

    public String instanceWorldName(ArenaTemplateDefinition templateDefinition, UUID sessionId) {
        return templateDefinition.instanceWorldPrefix() + sessionId.toString().replace("-", "");
    }

    public Set<String> findLeftoverInstances(WorldsConfig worldsConfig) throws IOException {
        Set<String> prefixes = new HashSet<>();
        for (ArenaTemplateDefinition template : worldsConfig.arenaTemplates().values()) {
            if (template.enabled()) {
                prefixes.add(template.instanceWorldPrefix());
            }
        }

        Set<String> leftovers = new HashSet<>();
        if (!Files.isDirectory(worldContainer)) {
            return leftovers;
        }

        try (var stream = Files.list(worldContainer)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                String directoryName = path.getFileName().toString();
                for (String prefix : prefixes) {
                    if (directoryName.startsWith(prefix)) {
                        leftovers.add(directoryName);
                        break;
                    }
                }
            });
        }

        return leftovers;
    }
}
