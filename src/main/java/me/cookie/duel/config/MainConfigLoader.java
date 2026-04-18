package me.cookie.duel.config;

import me.cookie.duel.config.model.AntiAbuseSettings;
import me.cookie.duel.config.model.ArenaModeSettings;
import me.cookie.duel.config.model.DuelSettings;
import me.cookie.duel.config.model.MainConfig;
import me.cookie.duel.config.model.ModeSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class MainConfigLoader {

    public MainConfig load(FileConfiguration config) {
        ConfigurationSection generalSection = requireSection(config, "general");
        ConfigurationSection modesSection = requireSection(config, "modes");
        ConfigurationSection wildModeSection = requireSection(modesSection, "wild");
        ConfigurationSection arenaModeSection = requireSectionAny(modesSection, "arena", "arena-instance");
        ConfigurationSection duelSection = requireSection(config, "duel");
        ConfigurationSection antiAbuseSection = requireSection(config, "anti-abuse");

        ArenaModeSettings arenaModeSettings = new ArenaModeSettings(
                arenaModeSection.getBoolean("enabled"),
                arenaModeSection.getInt("max-active-instances"),
                arenaModeSection.getBoolean("cleanup-leftovers-on-startup"),
                arenaModeSection.getInt("instance-create-timeout-seconds"),
                arenaModeSection.getInt("instance-delete-timeout-seconds")
        );

        ModeSettings modeSettings = new ModeSettings(
                wildModeSection.getBoolean("enabled"),
                arenaModeSettings
        );

        DuelSettings duelSettings = new DuelSettings(
                duelSection.getInt("prestart-countdown-seconds"),
                duelSection.getInt("max-fight-seconds")
        );

        AntiAbuseSettings antiAbuseSettings = new AntiAbuseSettings(
                antiAbuseSection.getInt("queue-join-cooldown-seconds"),
                antiAbuseSection.getInt("queue-leave-penalty-seconds"),
                antiAbuseSection.getInt("rematch-block-seconds")
        );

        return new MainConfig(
                generalSection.getBoolean("debug"),
                modeSettings,
                duelSettings,
                antiAbuseSettings
        );
    }

    static ConfigurationSection requireSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) {
            throw new ConfigurationException("Missing config section: " + parent.getCurrentPath() + "." + path);
        }
        return section;
    }

    static String requireString(ConfigurationSection parent, String path) {
        String value = parent.getString(path);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing config value: " + parent.getCurrentPath() + "." + path);
        }
        return value;
    }

    static ConfigurationSection requireSectionAny(ConfigurationSection parent, String... paths) {
        for (String path : paths) {
            ConfigurationSection section = parent.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }

        throw new ConfigurationException(
                "Missing config section: " + parent.getCurrentPath() + "."
                        + String.join(" or " + parent.getCurrentPath() + ".", paths)
        );
    }
}
