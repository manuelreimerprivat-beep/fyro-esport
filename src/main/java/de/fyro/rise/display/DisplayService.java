package de.fyro.rise.display;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.progression.LevelCurve;
import de.fyro.rise.storage.DataStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DisplayService {
    private static final String TEAM_PREFIX = "fyr_";

    private final DataStore data;
    private final Scoreboard scoreboard;
    private final Map<UUID, String> guildTags = new ConcurrentHashMap<>();
    private final Set<String> managedTeams = new HashSet<>();

    public DisplayService(FyroRisePlugin plugin, DataStore data) {
        this.data = data;
        this.scoreboard = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith(TEAM_PREFIX)) team.unregister();
        }
    }

    public void refresh(Player player) {
        Profile profile = data.profile(player.getUniqueId());
        String guild = profile.guild().trim();
        guildTags.put(player.getUniqueId(), guild);

        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            current.removeEntry(player.getName());
        }

        if (!guild.isBlank()) {
            String teamId = teamId(guild);
            Team team = scoreboard.getTeam(teamId);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamId);
                team.displayName(Component.text(guild, NamedTextColor.GOLD));
                team.prefix(Component.text("[" + guild + "] ", NamedTextColor.GOLD));
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                team.setAllowFriendlyFire(false);
            }
            managedTeams.add(teamId);
            team.addEntry(player.getName());
        }

        if (player.getScoreboard() != scoreboard) player.setScoreboard(scoreboard);
        syncExperience(player);
    }

    public void syncExperience(Player player) {
        Profile profile = data.profile(player.getUniqueId());
        player.setLevel(profile.level());
        player.setExp(LevelCurve.progress(profile.level(), profile.experience()));
    }

    public Component renderChat(UUID playerId, Component displayName, Component message) {
        String guild = guildTags.getOrDefault(playerId, "");
        Component decoratedName = guild.isBlank()
                ? displayName
                : Component.text("[" + guild + "] ", NamedTextColor.GOLD).append(displayName);
        return Component.text("<", NamedTextColor.DARK_GRAY)
                .append(decoratedName)
                .append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(message);
    }

    public void forget(Player player) {
        guildTags.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (String name : new HashSet<>(managedTeams)) {
            Team team = scoreboard.getTeam(name);
            if (team != null) team.unregister();
        }
        managedTeams.clear();
        guildTags.clear();
    }

    private String teamId(String guild) {
        String hash = UUID.nameUUIDFromBytes(guild.toLowerCase(Locale.ROOT)
                .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return TEAM_PREFIX + hash.substring(0, 10);
    }
}
