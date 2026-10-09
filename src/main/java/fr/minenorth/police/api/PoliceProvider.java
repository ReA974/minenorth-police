package fr.minenorth.police.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fournit les effectifs de police aux autres mods via MineNorth API. */
public final class PoliceProvider implements fr.minenorth.api.PoliceService {
    @Override public int grade(MinecraftServer s, UUID player) { return player == null ? -1 : PoliceApi.grade(s, player); }
    @Override public List<String> grades() { return List.of(PoliceApi.grades()); }
    @Override public Map<UUID, Integer> officers(MinecraftServer s) { return PoliceApi.officers(s); }
    @Override public boolean onDuty(MinecraftServer s, UUID player) { return PoliceApi.onDuty(player); }
    @Override public boolean canSearch(ServerPlayer officer, UUID owner) { return PoliceApi.canSearch(officer, owner); }
}
