package fr.minenorth.police.casier.client;

import fr.minenorth.police.casier.CasierNet;
import net.minecraft.client.Minecraft;

/** Réception côté client des états envoyés par le serveur. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void view(CasierNet.View v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CasierScreen s && s.pos().equals(v.pos())) s.update(v);
        else mc.setScreen(new CasierScreen(v));
    }
}
