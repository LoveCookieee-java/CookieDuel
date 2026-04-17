package me.cookie.duel.config;

import me.cookie.duel.config.model.BlacklistConfig;
import me.cookie.duel.config.model.WildBlacklistSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class BlacklistConfigLoader {

    private static final Map<String, Predicate<Material>> GROUP_ALIASES = Map.of(
            "leaves", material -> material.isBlock() && material.name().endsWith("_LEAVES"),
            "fences", material -> material.isBlock() && (material.name().endsWith("_FENCE") || material == Material.NETHER_BRICK_FENCE),
            "walls", material -> material.isBlock() && material.name().endsWith("_WALL")
    );

    private final Logger logger;

    public BlacklistConfigLoader(Logger logger) {
        this.logger = logger;
    }

    public BlacklistConfig load(FileConfiguration config) {
        ConfigurationSection wildSection = MainConfigLoader.requireSection(config, "wild");
        int[] invalidEntries = new int[1];

        WildBlacklistSettings wildBlacklistSettings = new WildBlacklistSettings(
                resolveMaterials(wildSection.getStringList("floor-blacklist"), "wild.floor-blacklist", invalidEntries),
                resolveMaterials(wildSection.getStringList("body-blacklist"), "wild.body-blacklist", invalidEntries),
                resolveMaterials(wildSection.getStringList("nearby-blacklist"), "wild.nearby-blacklist", invalidEntries)
        );

        return new BlacklistConfig(wildBlacklistSettings, invalidEntries[0]);
    }

    private Set<Material> resolveMaterials(List<String> entries, String path, int[] invalidEntries) {
        EnumSet<Material> resolved = EnumSet.noneOf(Material.class);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String normalized = entry.trim();
            if (normalized.toLowerCase(Locale.ROOT).startsWith("tag:")) {
                String alias = normalized.substring("tag:".length()).trim().toLowerCase(Locale.ROOT);
                Predicate<Material> matcher = GROUP_ALIASES.get(alias);
                if (matcher == null) {
                    invalidEntries[0]++;
                    logger.warning("Invalid blacklist alias '" + normalized + "' at " + path + ".");
                    continue;
                }

                for (Material material : Material.values()) {
                    if (matcher.test(material)) {
                        resolved.add(material);
                    }
                }
                continue;
            }

            Material material = Material.matchMaterial(normalized);
            if (material == null) {
                material = Material.getMaterial(normalized.toUpperCase(Locale.ROOT));
            }

            if (material == null) {
                invalidEntries[0]++;
                logger.warning("Invalid blacklist material '" + normalized + "' at " + path + ".");
                continue;
            }

            resolved.add(material);
        }
        return Set.copyOf(resolved);
    }
}
