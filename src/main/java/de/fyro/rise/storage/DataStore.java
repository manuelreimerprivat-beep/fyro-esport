package de.fyro.rise.storage;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.model.Model.Faction;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.model.Model.RiseClass;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DataStore {
    private final FyroRisePlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Profile> cache = new HashMap<>();

    public DataStore(FyroRisePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Profile profile(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    private Profile load(UUID uuid) {
        Profile profile = new Profile(uuid);
        String root = "players." + uuid;
        try {
            profile.faction(Faction.valueOf(yaml.getString(root + ".faction", Faction.UNGEWAEHLT.name())));
        } catch (IllegalArgumentException ignored) {}
        try {
            profile.riseClass(RiseClass.valueOf(yaml.getString(root + ".class", RiseClass.NOVIZE.name())));
        } catch (IllegalArgumentException ignored) {}
        profile.level(yaml.getInt(root + ".level", 1));
        profile.experience(yaml.getInt(root + ".experience", 0));
        profile.coins(yaml.getDouble(root + ".coins", 100.0));
        profile.pvpEnabled(yaml.getBoolean(root + ".pvp", false));
        profile.activeQuest(yaml.getString(root + ".quest.active", ""));
        profile.questProgress(yaml.getInt(root + ".quest.progress", 0));
        profile.completedQuests().addAll(yaml.getStringList(root + ".quest.completed"));
        profile.guild(yaml.getString(root + ".guild", ""));
        return profile;
    }

    public void save(Profile profile) {
        String root = "players." + profile.uuid();
        yaml.set(root + ".faction", profile.faction().name());
        yaml.set(root + ".class", profile.riseClass().name());
        yaml.set(root + ".level", profile.level());
        yaml.set(root + ".experience", profile.experience());
        yaml.set(root + ".coins", profile.coins());
        yaml.set(root + ".pvp", profile.pvpEnabled());
        yaml.set(root + ".quest.active", profile.activeQuest());
        yaml.set(root + ".quest.progress", profile.questProgress());
        yaml.set(root + ".quest.completed", new ArrayList<>(profile.completedQuests()));
        yaml.set(root + ".guild", profile.guild());
    }

    public void saveAll() {
        cache.values().forEach(this::save);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("data.yml konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    public void unload(UUID uuid) {
        Profile profile = cache.remove(uuid);
        if (profile != null) {
            save(profile);
            saveAll();
        }
    }

    public Collection<Profile> cachedProfiles() {
        return Collections.unmodifiableCollection(cache.values());
    }
}
