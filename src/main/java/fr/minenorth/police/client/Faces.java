package fr.minenorth.police.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Visage (tête du skin) d'un citoyen.
 * Connecté : skin déjà connu du client. Déconnecté : le skin est téléchargé à partir du pseudo,
 * comme pour une tête de joueur ; en attendant (ou en cas d'échec) on affiche le skin par défaut.
 */
public final class Faces {
    private Faces() {}

    private static final Map<UUID, GameProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Set<UUID> REQUESTED = ConcurrentHashMap.newKeySet();

    public static ResourceLocation skin(UUID id, String pseudo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getSkinLocation();
        }
        try {
            GameProfile profile = PROFILES.get(id);
            if (profile != null) return mc.getSkinManager().getInsecureSkinLocation(profile);
            if (pseudo != null && !pseudo.isBlank() && REQUESTED.add(id)) {
                SkullBlockEntity.updateGameprofile(new GameProfile(id, pseudo), filled -> { if (filled != null) PROFILES.put(id, filled); });
            }
        } catch (Throwable ignored) {}
        return DefaultPlayerSkin.getDefaultSkin(id);
    }

    public static void draw(GuiGraphics g, UUID id, String pseudo, int x, int y, int size) {
        PlayerFaceRenderer.draw(g, skin(id, pseudo), x, y, size);
    }
}
