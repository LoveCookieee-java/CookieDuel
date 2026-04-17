package me.cookie.duel.config;

import me.cookie.duel.config.model.AntiAbuseSettings;
import me.cookie.duel.config.model.ArenaModeSettings;
import me.cookie.duel.config.model.DuelSettings;
import me.cookie.duel.config.model.LobbySettings;
import me.cookie.duel.config.model.MainConfig;
import me.cookie.duel.config.model.ModeSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class MainConfigLoader {

    public MainConfig load(FileConfiguration config) {
        ConfigurationSection generalSection = requireSection(config, "general");
        ConfigurationSection lobbySection = requireSection(config, "lobby");
        ConfigurationSection modesSection = requireSection(config, "modes");
        ConfigurationSection wildModeSection = requireSection(modesSection, "wild");
        ConfigurationSection arenaModeSection = requireSection(modesSection, "arena-instance");
        ConfigurationSection duelSection = requireSection(config, "duel");
        ConfigurationSection antiAbuseSection = requireSection(config, "anti-abuse");

        LobbySettings lobbySettings = new LobbySettings(
                lobbySection.getBoolean("enabled"),
                requireString(lobbySection, "world"),
                lobbySection.getDouble("x"),
                lobbySection.getDouble("y"),
                lobbySection.getDouble("z"),
                (float) lobbySection.getDouble("yaw"),
                (float) lobbySection.getDouble("pitch")
        );

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
                duelSection.getInt("confirm-timeout-seconds"),
                duelSection.getInt("prestart-countdown-seconds"),
                duelSection.getInt("max-fight-seconds"),
                duelSection.getBoolean("restore-inventory-after-duel"),
                duelSection.getBoolean("teleport-back-to-lobby-after-duel")
        );

        AntiAbuseSettings antiAbuseSettings = new AntiAbuseSettings(
                antiAbuseSection.getInt("queue-join-cooldown-seconds"),
                antiAbuseSection.getInt("queue-leave-penalty-seconds"),
                antiAbuseSection.getInt("rematch-block-seconds")
        );

        return new MainConfig(
                generalSection.getBoolean("debug"),
                lobbySettings,
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
}
