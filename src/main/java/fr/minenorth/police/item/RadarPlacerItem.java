package fr.minenorth.police.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.function.Supplier;

/**
 * Radar (mobile ou fixe) à poser. Volontairement un objet simple et NON un BlockItem, comme la herse : la pose est faite par
 * {@link #place} (setBlock direct, sans évènement de pose) à la demande du client (clic vanilla annulé côté client, paquet dédié),
 * donc WorldGuard n'a rien à refuser. Réservé à la police (config objets_reserves_police).
 */
public class RadarPlacerItem extends Item {
    private final Supplier<? extends Block> block;

    public RadarPlacerItem(Supplier<? extends Block> block, Properties props) {
        super(props);
        this.block = block;
    }

    /** Pose le radar contre la face cliquée (ou à la place du bloc cliqué s'il est remplaçable). */
    public static void place(ServerPlayer p, BlockPos clicked, Direction face) {
        ItemStack st = p.getMainHandItem();
        if (!(st.getItem() instanceof RadarPlacerItem item)) return;
        // Borne fixe : posée par les admins ; radar mobile : par les policiers.
        if (item.block.get() instanceof fr.minenorth.police.block.RadarBorneBlock) {
            if (!(p.hasPermissions(2) || p.getAbilities().instabuild)) {
                p.displayClientMessage(Component.literal("§cLa borne fixe est posée par les administrateurs."), true);
                return;
            }
        } else if (!PoliceTool.allowed(p)) return;
        ServerLevel level = p.serverLevel();
        BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        Block b = item.block.get();
        BlockState state = b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, p.getDirection().getOpposite());
        boolean tall = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF);
        BlockPos top = target.above();
        if (!level.isLoaded(target) || target.getY() < level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight() - (tall ? 1 : 0)
                || !level.getBlockState(target).canBeReplaced() || (tall && !level.getBlockState(top).canBeReplaced())
                || !level.isUnobstructed(state, target, CollisionContext.of(p))) {
            p.displayClientMessage(Component.literal("§cImpossible de poser le radar ici."), true);
            return;
        }
        level.setBlock(target, state, 3);
        b.setPlacedBy(level, target, state, p, st);
        var snd = state.getSoundType();
        level.playSound(null, target, snd.getPlaceSound(), SoundSource.BLOCKS, (snd.getVolume() + 1f) / 2f, snd.getPitch() * 0.8f);
        if (!p.getAbilities().instabuild) st.shrink(1);
    }
}
