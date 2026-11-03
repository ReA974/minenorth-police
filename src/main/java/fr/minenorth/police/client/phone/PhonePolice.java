package fr.minenorth.police.client.phone;

import com.mikasrevs.newphone.client.zphone.PhoneNavigationState;
import fr.minenorth.police.network.ModNetwork;
import fr.minenorth.police.network.PhoneNet;
import net.minecraft.client.Minecraft;

/**
 * Seul point d'entrée vers {@link PhonePoliceScreen} (qui hérite d'une classe du téléphone MikasRevs Phone 2). Ne JAMAIS toucher cette classe sans
 * avoir vérifié {@code PhoneAppClient.phone()} : sa vérification par la JVM charge PhonePoliceScreen, donc la classe du téléphone. Côté client uniquement.
 */
public final class PhonePolice {
    /** Ouverture demandée depuis le téléphone : la prochaine vue de la tablette ouvre l'application dans le téléphone (pendant 5 s). */
    private static long pendingUntil;

    private PhonePolice() {}

    /** Clic sur l'icône : on garde le téléphone ouvert et on demande l'ouverture au serveur (qui revérifie le métier). */
    public static void request() {
        pendingUntil = System.currentTimeMillis() + 5000;
        ModNetwork.CHANNEL.sendToServer(new PhoneNet.Open());
    }

    /** true si la vue a été prise par l'application du téléphone (ouverte ou mise à jour). */
    public static boolean handle(ModNetwork.ViewPacket view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PhonePoliceScreen ps) {
            ps.update(view);
            return true;
        }
        if (System.currentTimeMillis() < pendingUntil && mc.player != null) {
            pendingUntil = 0;
            PhoneNavigationState.open(mc, mc.screen, new PhonePoliceScreen(view));
            return true;
        }
        return false;
    }
}
