package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menu du prisonnier (shift + clic droit sur un menotté) : garde à vue, prison, monter / faire sortir du véhicule, fouille.
 * Affichage seul : le serveur revérifie droits, distance et état de chaque action.
 */
public class CuffMenuScreen extends Screen {
    private static final int W = 300;
    private int H() { return p.mini() ? 150 : 262; }

    private String unlockLabel() { return CuffClient.hooded(p.target()) ? "RETIRER LE SAC" : "DÉMENOTTER"; }
    private ModNetwork.CuffMenuOpenPacket p;
    private int left, top;
    private EditBox reason, minutes;
    private String kReason = "", kMinutes = "10";

    private CuffMenuScreen(ModNetwork.CuffMenuOpenPacket p) {
        super(Component.literal("Prisonnier"));
        this.p = p;
    }

    public static void open(ModNetwork.CuffMenuOpenPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.name().isEmpty()) {            // le serveur demande de fermer (détention décidée)
            if (mc.screen instanceof CuffMenuScreen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof CuffMenuScreen s && s.p.target().equals(p.target())) s.update(p);
        else mc.setScreen(new CuffMenuScreen(p));
    }

    private void update(ModNetwork.CuffMenuOpenPacket n) {
        keep();
        this.p = n;
        rebuild();
    }

    private void keep() {
        if (reason != null) kReason = reason.getValue();
        if (minutes != null) kMinutes = minutes.getValue();
    }

    private void rebuild() { clearWidgets(); init(); }

    private void act(int action) {
        keep();
        int m;
        try { m = Integer.parseInt(kMinutes.trim()); } catch (RuntimeException e) { m = 0; }
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.CuffMenuActionPacket(action, p.target(), kReason, m));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H()) / 2);
        int x = left + 14, w = W - 28, half = (w - 8) / 2;
        if (p.mini()) {
            // Menottes illégales : démenotter et véhicule seulement.
            addRenderableWidget(new MineNorthButton(x, top + 54, w, 22, Component.literal(unlockLabel()), MineNorthStyle.GREEN, () -> act(5)));
            addRenderableWidget(new MineNorthButton(x, top + 82, w, 22, Component.literal("MONTER EN VOITURE"), MineNorthStyle.DARK, () -> act(1))).enabled(!p.seated());
            addRenderableWidget(new MineNorthButton(x, top + 110, w, 22, Component.literal("FERMER"), 0xFFB3263E, this::onClose));
            return;
        }
        reason = new EditBox(font, x, top + 66, w, 18, Component.literal("Motif"));
        reason.setMaxLength(90);
        reason.setHint(Component.literal("Motif de la détention"));
        reason.setValue(kReason);
        addRenderableWidget(reason);
        minutes = new EditBox(font, x, top + 104, 72, 18, Component.literal("Durée"));
        minutes.setMaxLength(4);
        minutes.setValue(kMinutes);
        addRenderableWidget(minutes);
        addRenderableWidget(new MineNorthButton(x, top + 132, half, 22, Component.literal("GARDE À VUE"), MineNorthStyle.CYAN, () -> act(3))).enabled(p.jail());
        addRenderableWidget(new MineNorthButton(x + half + 8, top + 132, half, 22, Component.literal("PRISON"), MineNorthStyle.PINK, () -> act(4))).enabled(p.prison());
        addRenderableWidget(new MineNorthButton(x, top + 160, half, 22, Component.literal("MONTER EN VOITURE"), MineNorthStyle.DARK, () -> act(1))).enabled(!p.seated());
        addRenderableWidget(new MineNorthButton(x + half + 8, top + 160, half, 22, Component.literal("FAIRE SORTIR"), MineNorthStyle.GREEN, () -> act(2))).enabled(p.seated());
        addRenderableWidget(new MineNorthButton(x, top + 188, half, 22, Component.literal("FOUILLE"), MineNorthStyle.PINK, () -> act(0))).enabled(p.search());
        addRenderableWidget(new MineNorthButton(x + half + 8, top + 188, half, 22, Component.literal(unlockLabel()), MineNorthStyle.GREEN, () -> act(5)));
        addRenderableWidget(new MineNorthButton(x, top + 216, w, 22, Component.literal("FERMER"), 0xFFB3263E, this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        String who = p.name().length() > 22 ? p.name().substring(0, 21) + "…" : p.name();
        MineNorthStyle.panel(g, left, top, W, H(), "PRISONNIER", who);
        int x = left + 14;
        if (!p.mini()) {
            g.drawString(font, "Motif (garde à vue / prison)", x, top + 54, MineNorthStyle.BLUE, false);
            g.drawString(font, "Durée en minutes", x, top + 92, MineNorthStyle.BLUE, false);
            String max = "Max : " + p.gavMax() + " min (garde à vue), " + p.prisonMax() + " min (prison)";
            g.drawString(font, font.plainSubstrByWidth(max, W - 28 - 80), x + 80, top + 109, MineNorthStyle.MUTED, false);
        }
        if (!p.msg().isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(p.msg(), W - 28), x, top + H() - 18, p.ok() ? MineNorthStyle.OK : MineNorthStyle.ALERT, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
