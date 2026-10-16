package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Réglage d'un radar posé (mobile ou fixe) : limite de vitesse et bouton « Ranger ». Le serveur revérifie le droit et la distance. */
public class RadarBlockScreen extends Screen {
    private static final int W = 260, H = 222;
    private static final int RED = 0xFFB3263E;
    private static final int[] PRESETS = {30, 50, 70, 80, 90, 110, 130};
    private static final int MIN = 5, MAX = 200;

    private final BlockPos pos;
    private final int flashes;
    private final boolean mobile;
    private int value;
    private int left, top;

    private RadarBlockScreen(BlockPos pos, int limit, int flashes, boolean mobile) {
        super(Component.literal("Radar"));
        this.pos = pos;
        this.value = Math.max(MIN, Math.min(MAX, limit));
        this.flashes = flashes;
        this.mobile = mobile;
    }

    public static void open(BlockPos pos, int limit, int flashes, boolean mobile) {
        Minecraft.getInstance().setScreen(new RadarBlockScreen(pos, limit, flashes, mobile));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int y = top + 90;
        int[] steps = {-10, -5, 5, 10};
        for (int i = 0; i < steps.length; i++) {
            int s = steps[i];
            addRenderableWidget(new MineNorthButton(left + 20 + i * 58, y, 52, 20, Component.literal((s > 0 ? "+" : "") + s), MineNorthStyle.DARK, () -> add(s)));
        }
        int bw = 28, gap = 4, total = PRESETS.length * bw + (PRESETS.length - 1) * gap;
        int px = left + (W - total) / 2;
        for (int i = 0; i < PRESETS.length; i++) {
            int p = PRESETS[i];
            addRenderableWidget(new MineNorthButton(px + i * (bw + gap), top + 120, bw, 20, Component.literal(String.valueOf(p)), MineNorthStyle.CYAN, () -> value = p));
        }
        addRenderableWidget(new MineNorthButton(left + 20, top + 158, 112, 24, Component.literal("Valider"), MineNorthStyle.GREEN, () -> send(false)));
        addRenderableWidget(new MineNorthButton(left + W - 132, top + 158, 112, 24, Component.literal("Annuler"), MineNorthStyle.DARK, this::onClose));
        addRenderableWidget(new MineNorthButton(left + 20, top + 188, W - 40, 22, Component.literal("Ranger le radar"), RED, () -> send(true)));
    }

    private void add(int d) { value = Math.max(MIN, Math.min(MAX, value + d)); }

    private void send(boolean pickup) {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RadarBlockPacket(pos, value, pickup));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, mobile ? "Radar mobile" : "Radar fixe", flashes + " flash(s) enregistré(s)");
        String txt = value + " km/h";
        int w = font.width(txt);
        MineNorthStyle.scaled(g, MineNorthStyle.bold(txt), left + (W - Math.round(w * 2.4f)) / 2, top + 52, 2.4f, MineNorthStyle.WHITE);
        g.drawString(font, "Limite de vitesse", left + 20, top + 78, MineNorthStyle.MUTED, false);
        g.drawString(font, "Préréglages", left + 20, top + 110, MineNorthStyle.MUTED, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
