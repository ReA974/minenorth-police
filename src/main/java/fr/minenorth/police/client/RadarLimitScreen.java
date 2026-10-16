package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Réglage de la limite de vitesse du radar à main (charte MineNorth). Le serveur revérifie l'objet en main et borne la valeur. */
public class RadarLimitScreen extends Screen {
    private static final int W = 260, H = 190;
    private static final int[] PRESETS = {30, 50, 70, 80, 90, 110, 130};
    public static final int MIN = 5, MAX = 200;

    private int value;
    private int left, top;

    private RadarLimitScreen(int current) {
        super(Component.literal("Radar à main"));
        this.value = Math.max(MIN, Math.min(MAX, current));
    }

    public static void open(int current) { Minecraft.getInstance().setScreen(new RadarLimitScreen(current)); }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int y = top + 78;
        int[] steps = {-10, -5, 5, 10};
        for (int i = 0; i < steps.length; i++) {
            int s = steps[i];
            addRenderableWidget(new MineNorthButton(left + 20 + i * 58, y, 52, 20, Component.literal((s > 0 ? "+" : "") + s), MineNorthStyle.DARK, () -> add(s)));
        }
        int bw = 28, gap = 4, total = PRESETS.length * bw + (PRESETS.length - 1) * gap;
        int px = left + (W - total) / 2;
        for (int i = 0; i < PRESETS.length; i++) {
            int p = PRESETS[i];
            addRenderableWidget(new MineNorthButton(px + i * (bw + gap), top + 108, bw, 20, Component.literal(String.valueOf(p)), MineNorthStyle.CYAN, () -> value = p));
        }
        addRenderableWidget(new MineNorthButton(left + 20, top + 150, 112, 24, Component.literal("Valider"), MineNorthStyle.GREEN, this::validate));
        addRenderableWidget(new MineNorthButton(left + W - 132, top + 150, 112, 24, Component.literal("Annuler"), MineNorthStyle.DARK, this::onClose));
    }

    private void add(int d) { value = Math.max(MIN, Math.min(MAX, value + d)); }

    private void validate() {
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.RadarLimitPacket(value));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "Radar à main", "Limite de vitesse");
        String txt = value + " km/h";
        int w = font.width(txt);
        MineNorthStyle.scaled(g, MineNorthStyle.bold(txt), left + (W - Math.round(w * 2.4f)) / 2, top + 50, 2.4f, MineNorthStyle.WHITE);
        g.drawString(font, "Préréglages", left + 20, top + 98, MineNorthStyle.MUTED, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
