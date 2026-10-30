package de.citybuild;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager {

    public static class Clan {
        private final String tag;
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        public Clan(String tag, UUID leader) {
            this.tag = tag;
            this.leader = leader;
            this.members.add(leader);
        }

        public String tag() {
            return tag;
        }

        public UUID leader() {
            return leader;
        }

        public Set<UUID> members() {
            return members;
        }
    }

    private record Invite(String clanKey, long expires) {
    }

    private final CityBuildPlugin plugin;
    private final File file;
    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberClan = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public ClanManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        load();
    }

    private static String key(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("clans");
        if (section == null) {
            return;
        }
        for (String k : section.getKeys(false)) {
            try {
                String tag = section.getString(k + ".tag", k);
                UUID leader = UUID.fromString(section.getString(k + ".leader", ""));
                Clan clan = new Clan(tag, leader);
                for (String member : section.getStringList(k + ".members")) {
                    clan.members().add(UUID.fromString(member));
                }
                clans.put(key(tag), clan);
                for (UUID uuid : clan.members()) {
                    memberClan.put(uuid, key(tag));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ungueltiger Clan-Eintrag '" + k + "' uebersprungen.");
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Clan> entry : clans.entrySet()) {
            Clan clan = entry.getValue();
            String base = "clans." + entry.getKey();
            yml.set(base + ".tag", clan.tag());
            yml.set(base + ".leader", clan.leader().toString());
            List<String> members = new ArrayList<>();
            for (UUID uuid : clan.members()) {
                members.add(uuid.toString());
            }
            yml.set(base + ".members", members);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte clans.yml nicht speichern: " + e.getMessage());
        }
    }

    public Clan getClan(UUID player) {
        String k = memberClan.get(player);
        return k == null ? null : clans.get(k);
    }

    public Clan byTag(String tag) {
        return clans.get(key(tag));
    }

    public Collection<Clan> all() {
        return clans.values();
    }

    public Clan create(String tag, UUID leader) {
        Clan clan = new Clan(tag, leader);
        clans.put(key(tag), clan);
        memberClan.put(leader, key(tag));
        save();
        return clan;
    }

    public void disband(Clan clan) {
        for (UUID uuid : clan.members()) {
            memberClan.remove(uuid);
        }
        clans.remove(key(clan.tag()));
        save();
    }

    public void addMember(Clan clan, UUID uuid) {
        clan.members().add(uuid);
        memberClan.put(uuid, key(clan.tag()));
        save();
    }

    /** Entfernt ein Mitglied. Wenn der Anfuehrer geht, uebernimmt das naechste Mitglied; ist keiner mehr da, wird der Clan geloescht. */
    public void removeMember(Clan clan, UUID uuid) {
        clan.members().remove(uuid);
        memberClan.remove(uuid);
        if (clan.members().isEmpty()) {
            clans.remove(key(clan.tag()));
        } else if (clan.leader().equals(uuid)) {
            clan.leader = clan.members().iterator().next();
        }
        save();
    }

    public void invite(UUID target, Clan clan) {
        invites.put(target, new Invite(key(clan.tag()), System.currentTimeMillis() + 60_000L));
    }

    /** Gibt den Clan der offenen Einladung zurueck (und loescht sie), sonst null. */
    public Clan takeInvite(UUID target) {
        Invite invite = invites.remove(target);
        if (invite == null || invite.expires() < System.currentTimeMillis()) {
            return null;
        }
        return clans.get(invite.clanKey());
    }
}
