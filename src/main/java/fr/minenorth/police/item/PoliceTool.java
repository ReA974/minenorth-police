package fr.minenorth.police.item;

import fr.minenorth.police.PoliceService;
import fr.minenorth.police.compat.Mts;
import fr.minenorth.police.config.PoliceConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Outils communs aux objets de police (côté serveur). */
public final class PoliceTool {
    private PoliceTool() {}

    /** Vrai si le joueur peut utiliser l'équipement ; sinon affiche un message. */
    public static boolean allowed(ServerPlayer p) {
        if (!PoliceConfig.get().objets_reserves_police || PoliceService.rank(p) >= 0) return true;
        p.displayClientMessage(Component.literal("§cÉquipement réservé à la police."), true);
        return false;
    }

    /**
     * Entité visée : la plus proche de l'axe du regard (dans un cône de maxDeg degrés), en vue directe, à moins de range blocs.
     * Plus tolérant qu'un simple rayon : les véhicules MTS ont une boîte minuscule.
     */
    @Nullable
    public static Entity target(ServerPlayer p, double range, double maxDeg, Predicate<Entity> filter) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        AABB box = new AABB(eye, eye.add(look.scale(range))).inflate(4 + range * Math.tan(Math.toRadians(maxDeg)));
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, box, x -> x.isAlive() && !x.isSpectator() && filter.test(x))) {
            if (e == p.getVehicle() || e.hasPassenger(p)) continue;
            Vec3 c = aim(e);
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist < 0.5 || dist > range) continue;
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(to) / dist))));
            // Tolérance : taille apparente de l'entité (véhicule MTS : ~2 blocs).
            double radius = Mts.isBuilder(e) ? 2.0 : Math.max(0.5, e.getBbWidth() / 2 + 0.3);
            double allowed = Math.max(maxDeg, Math.toDegrees(Math.atan(radius / dist)));
            if (angle > allowed) continue;
            if (!visible(p, eye, c, dist)) continue;
            double score = angle / allowed + dist / range * 0.25;
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    /** Point visé sur une entité. */
    public static Vec3 aim(Entity e) {
        if (Mts.isBuilder(e)) return e.position().add(0, 1.0, 0);
        return e.getBoundingBox().getCenter();
    }

    private static boolean visible(ServerPlayer p, Vec3 from, Vec3 to, double dist) {
        HitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(from) >= dist - 1.2;
    }

    /** Vrai si l'objet correspond à un motif : "mod:id", "mod:*_sword", "mod:*" ou "#mod:tag". */
    public static boolean matches(ItemStack st, String pattern) {
        if (st.isEmpty() || pattern == null || pattern.isBlank()) return false;
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        if (p.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(p.substring(1));
            return rl != null && st.is(TagKey.create(Registries.ITEM, rl));
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (key == null) return false;
        String id = key.toString();
        if (!p.contains("*")) return id.equals(p.contains(":") ? p : "minecraft:" + p);
        String regex = ("\\Q" + (p.contains(":") ? p : "minecraft:" + p) + "\\E").replace("*", "\\E.*\\Q");
        return Pattern.matches(regex, id);
    }

    public static boolean matchesAny(ItemStack st, Iterable<String> patterns) {
        for (String p : patterns) if (matches(st, p)) return true;
        return false;
    }
}
