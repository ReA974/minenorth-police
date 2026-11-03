package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    /** Met à jour la tablette déjà ouverte (on garde l'onglet et la page), ou l'application du téléphone, sinon ouvre la tablette. */
    public static void view(ModNetwork.ViewPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PoliceScreen s) s.update(p);
        else if (PhoneAppClient.phone() && fr.minenorth.police.client.phone.PhonePolice.handle(p)) return;
        else mc.setScreen(new PoliceScreen(p));
    }
}
