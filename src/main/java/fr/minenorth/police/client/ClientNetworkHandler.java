package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    /** Met à jour la tablette déjà ouverte (on garde l'onglet et la page), sinon l'ouvre. */
    public static void view(ModNetwork.ViewPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PoliceScreen s) s.update(p);
        else mc.setScreen(new PoliceScreen(p));
    }
}
