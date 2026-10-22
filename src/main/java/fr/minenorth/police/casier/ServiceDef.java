package fr.minenorth.police.casier;

import net.minecraft.server.level.ServerPlayer;

/** Un service qui a des casiers (police, pompiers, ou une entreprise de la config). Thread serveur uniquement. */
public interface ServiceDef {
    String id();

    String name();

    /** Le joueur fait partie du service (grade, contrat…). */
    boolean member(ServerPlayer p);

    boolean onDuty(ServerPlayer p);

    /** Prend ou quitte le service. Renvoie le nouvel état. */
    boolean setDuty(ServerPlayer p, boolean on);

    /** Chef du service (peut poser des casiers, éditer le stuff, ouvrir la consigne). */
    boolean chief(ServerPlayer p);
}
