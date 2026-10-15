package fr.minenorth.police;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * Herse de police : une entité plate posée sur la route (et non un bloc, que WorldGuard refuserait de poser). Sans collision, invulnérable,
 * immobile ; sa rotation (yRot, multiple de 90°) donne le sens de la bande : elle s'étend en travers du regard du policier qui l'a posée.
 * Ce sont {@link HerseService} qui crève les roues des véhicules qui la franchissent.
 */
public class HerseEntity extends Entity {
    /** Demi-longueur (travers de la route) et demi-épaisseur (sens de la marche) de la bande, en blocs. */
    public static final double HALF_LENGTH = 1.0, HALF_WIDTH = 0.45;

    public HerseEntity(EntityType<? extends HerseEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return true; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    /** Axe « en travers » de la bande (x, z), perpendiculaire au sens du regard de pose. */
    public double[] lateral() {
        double a = Math.toRadians(getYRot());
        return new double[] {Math.cos(a), Math.sin(a)};
    }

    /** Vrai si le point (x, z) est sur la bande. */
    public boolean covers(double x, double z) {
        double dx = x - getX(), dz = z - getZ(), a = Math.toRadians(getYRot());
        double lateral = dx * Math.cos(a) + dz * Math.sin(a), along = dx * -Math.sin(a) + dz * Math.cos(a);
        return Math.abs(lateral) <= HALF_LENGTH && Math.abs(along) <= HALF_WIDTH;
    }
}
