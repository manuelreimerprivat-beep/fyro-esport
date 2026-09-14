package de.fyro.rise.social;

import de.fyro.rise.FyroRisePlugin;
import de.fyro.rise.display.DisplayService;
import de.fyro.rise.game.GameService;
import de.fyro.rise.model.Model.Guild;
import de.fyro.rise.model.Model.Profile;
import de.fyro.rise.storage.DataStore;
import de.fyro.rise.util.GameRules;
import de.fyro.rise.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public final class SocialService {
    private record Invite(UUID inviter, long expiresAt) {}
    private static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();
        private Party(UUID leader) { this.leader = leader; members.add(leader); }
    }

    private final FyroRisePlugin plugin;
    private final DataStore data;
    private final GameService game;
    private final DisplayService display;
    private final Map<UUID, Party> partyByMember = new HashMap<>();
    private final Map<UUID, Invite> partyInvites = new HashMap<>();
    private final Map<String, Guild> guilds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID, String> guildInvites = new HashMap<>();
    private final File guildFile;

    public SocialService(FyroRisePlugin plugin, DataStore data, GameService game, DisplayService display) {
        this.plugin = plugin;
        this.data = data;
        this.game = game;
        this.display = display;
        this.guildFile = new File(plugin.getDataFolder(), "guilds.yml");
        loadGuilds();
    }

    public void createParty(Player player) {
        if (partyByMember.containsKey(player.getUniqueId())) {
            Text.error(player, "Du bist bereits in einer Gruppe.");
            return;
        }
        Party party = new Party(player.getUniqueId());
        partyByMember.put(player.getUniqueId(), party);
        Text.success(player, "Gruppe erstellt.");
    }

    public void inviteParty(Player leader, Player target) {
        Party party = partyByMember.get(leader.getUniqueId());
        if (party == null || !party.leader.equals(leader.getUniqueId())) {
            Text.error(leader, "Du bist nicht der Gruppenleiter.");
            return;
        }
        if (party.members.size() >= 5) {
            Text.error(leader, "Die Gruppe ist bereits voll (maximal 5).");
            return;
        }
        if (partyByMember.containsKey(target.getUniqueId())) {
            Text.error(leader, "Dieser Spieler ist bereits in einer Gruppe.");
            return;
        }
        partyInvites.put(target.getUniqueId(), new Invite(leader.getUniqueId(), System.currentTimeMillis() + 60_000));
        Text.success(leader, target.getName() + " wurde eingeladen.");
        Text.send(target, leader.getName() + " lädt dich ein. Annahme: /rise party accept");
    }

    public void acceptParty(Player player) {
        Invite invite = partyInvites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            Text.error(player, "Du hast keine gültige Gruppeneinladung.");
            return;
        }
        Party party = partyByMember.get(invite.inviter);
        if (party == null || party.members.size() >= 5) {
            Text.error(player, "Die Gruppe ist nicht mehr verfügbar.");
            return;
        }
        party.members.add(player.getUniqueId());
        partyByMember.put(player.getUniqueId(), party);
        broadcast(party, player.getName() + " ist der Gruppe beigetreten.");
    }

    public void leaveParty(Player player) {
        Party party = partyByMember.remove(player.getUniqueId());
        if (party == null) {
            Text.error(player, "Du bist in keiner Gruppe.");
            return;
        }
        party.members.remove(player.getUniqueId());
        if (party.members.isEmpty()) return;
        if (party.leader.equals(player.getUniqueId())) party.leader = party.members.iterator().next();
        broadcast(party, player.getName() + " hat die Gruppe verlassen.");
    }

    public void partyInfo(Player player) {
        Party party = partyByMember.get(player.getUniqueId());
        if (party == null) {
            Text.error(player, "Du bist in keiner Gruppe.");
            return;
        }
        String names = party.members.stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName)
                .map(name -> name == null ? "Unbekannt" : name).collect(Collectors.joining(", "));
        Text.send(player, "Gruppe: " + names + " | Leiter: " + Bukkit.getOfflinePlayer(party.leader).getName());
    }

    public Set<Player> onlineParty(Player player) {
        Party party = partyByMember.get(player.getUniqueId());
        if (party == null) return Set.of(player);
        return party.members.stream().map(Bukkit::getPlayer).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void broadcast(Party party, String message) {
        party.members.stream().map(Bukkit::getPlayer).filter(Objects::nonNull)
                .forEach(player -> Text.send(player, "[Gruppe] " + message));
    }

    public void createGuild(Player player, String name) {
        Profile profile = data.profile(player.getUniqueId());
        String clean = name.trim();
        if (!profile.guild().isBlank()) {
            Text.error(player, "Du bist bereits in einer Gilde.");
            return;
        }
        if (!GameRules.validGuildName(clean)) {
            Text.error(player, "Gildenname: 3–20 Zeichen; Buchstaben, Zahlen, Leerzeichen, _ und -.");
            return;
        }
        if (guilds.containsKey(clean)) {
            Text.error(player, "Dieser Gildenname ist bereits vergeben.");
            return;
        }
        Guild guild = new Guild(clean, player.getUniqueId());
        guilds.put(clean, guild);
        profile.guild(clean);
        saveGuilds();
        data.saveAll();
        display.refresh(player);
        Text.success(player, "Gilde gegründet: " + clean);
    }

    public void inviteGuild(Player player, Player target) {
        Profile profile = data.profile(player.getUniqueId());
        Guild guild = guilds.get(profile.guild());
        if (guild == null || !guild.owner().equals(player.getUniqueId())) {
            Text.error(player, "Nur der Gildenleiter kann einladen.");
            return;
        }
        if (!data.profile(target.getUniqueId()).guild().isBlank()) {
            Text.error(player, "Dieser Spieler ist bereits in einer Gilde.");
            return;
        }
        guildInvites.put(target.getUniqueId(), guild.name());
        Text.success(player, target.getName() + " wurde eingeladen.");
        Text.send(target, "Einladung zu " + guild.name() + ". Annahme: /rise guild accept " + guild.name());
    }

    public void acceptGuild(Player player, String guildName) {
        String invited = guildInvites.remove(player.getUniqueId());
        Guild guild = invited == null ? null : guilds.get(invited);
        Profile profile = data.profile(player.getUniqueId());
        if (guild == null || !guild.name().equalsIgnoreCase(guildName)) {
            Text.error(player, "Keine passende Gildeneinladung gefunden.");
            return;
        }
        if (!profile.guild().isBlank()) {
            Text.error(player, "Du bist bereits in einer Gilde.");
            return;
        }
        guild.members().add(player.getUniqueId());
        profile.guild(guild.name());
        saveGuilds();
        data.saveAll();
        display.refresh(player);
        Text.success(player, "Du bist " + guild.name() + " beigetreten.");
    }

    public void leaveGuild(Player player) {
        Profile profile = data.profile(player.getUniqueId());
        Guild guild = guilds.get(profile.guild());
        if (guild == null) {
            profile.guild("");
            display.refresh(player);
            Text.error(player, "Du bist in keiner Gilde.");
            return;
        }
        if (guild.owner().equals(player.getUniqueId()) && guild.members().size() > 1) {
            Text.error(player, "Der Leiter kann eine nicht leere Gilde nicht verlassen.");
            return;
        }
        guild.members().remove(player.getUniqueId());
        profile.guild("");
        if (guild.members().isEmpty()) guilds.remove(guild.name());
        saveGuilds();
        data.saveAll();
        display.refresh(player);
        Text.success(player, "Du hast die Gilde verlassen.");
    }

    public void guildInfo(Player player) {
        Profile profile = data.profile(player.getUniqueId());
        Guild guild = guilds.get(profile.guild());
        if (guild == null) {
            Text.error(player, "Du bist in keiner Gilde.");
            return;
        }
        Text.send(player, "Gilde " + guild.name() + " | Mitglieder: " + guild.members().size()
                + " | Bank: " + guild.bank() + " ✦ | Leiter: " + Bukkit.getOfflinePlayer(guild.owner()).getName());
    }

    public void deposit(Player player, double amount) {
        Profile profile = data.profile(player.getUniqueId());
        Guild guild = guilds.get(profile.guild());
        if (guild == null || !GameRules.validTransfer(amount, profile.coins())) {
            Text.error(player, "Einzahlung nicht möglich. Prüfe Betrag und Guthaben.");
            return;
        }
        profile.coins(profile.coins() - amount);
        guild.bank(guild.bank() + amount);
        saveGuilds();
        data.saveAll();
        Text.success(player, amount + " ✦ in die Gildenbank eingezahlt.");
    }

    private void loadGuilds() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(guildFile);
        ConfigurationSection section = yaml.getConfigurationSection("guilds");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String root = "guilds." + key;
            String name = yaml.getString(root + ".name", key);
            String ownerValue = yaml.getString(root + ".owner");
            if (ownerValue == null) continue;
            try {
                Guild guild = new Guild(name, UUID.fromString(ownerValue));
                guild.members().clear();
                for (String value : yaml.getStringList(root + ".members")) {
                    try { guild.members().add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) {}
                }
                guild.members().add(guild.owner());
                guild.bank(yaml.getDouble(root + ".bank", 0));
                guilds.put(name, guild);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungültige Gilde in guilds.yml: " + key);
            }
        }
    }

    public void saveGuilds() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Guild guild : guilds.values()) {
            String key = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(guild.name().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            String root = "guilds." + key;
            yaml.set(root + ".name", guild.name());
            yaml.set(root + ".owner", guild.owner().toString());
            yaml.set(root + ".members", guild.members().stream().map(UUID::toString).toList());
            yaml.set(root + ".bank", guild.bank());
        }
        try {
            yaml.save(guildFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("guilds.yml konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }
}
