package fr.minenorth.police.casier.block;

import fr.minenorth.police.casier.CasierData;
import fr.minenorth.police.casier.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Porte le service relié au casier (modifiable par un OP ou le chef dans l'écran admin). */
public class CasierBlockEntity extends BlockEntity {
    private String service = "";

    public CasierBlockEntity(BlockPos pos, BlockState st) { super(ModBlocks.CASIER_BE.get(), pos, st); }

    public String service() { return service; }

    public void setService(String s) {
        service = s == null ? "" : s;
        setChanged();
        register();
    }

    /** Inscrit ce casier dans la liste des casiers du service (pour savoir s'il en reste un). */
    public void register() {
        if (level == null || level.isClientSide || level.getServer() == null) return;
        CasierData d = CasierData.get(level.getServer());
        d.casiers.put(CasierData.key(level.dimension().location().toString(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()), service);
        d.setDirty();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putString("Service", service);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        service = t.getString("Service");
    }
}
