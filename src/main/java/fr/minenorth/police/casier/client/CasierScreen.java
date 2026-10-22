package fr.minenorth.police.casier.client;

import fr.minenorth.police.casier.CasierNet;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Écran du casier : prise d'équipement, dépôt, fin de service ; en bas, les outils du chef de service et des OP. */
public class CasierScreen extends Screen {
    private CasierNet.View v;
    private static final int W = 330;
    private int left, top, h;

    public CasierScreen(CasierNet.View v) {
        super(Component.translatable("screen.minenorth_casier.titre"));
        this.v = v;
    }

    public BlockPos pos() { return v.pos(); }

    public void update(CasierNet.View nv) {
        this.v = nv;
        rebuild();
    }

    @Override
    protected void init() { rebuild(); }

    private void act(int a) {
        CasierNet.CHANNEL.sendToServer(new CasierNet.Act(a, v.pos()));
    }

    private void rebuild() {
        clearWidgets();
        h = v.admin() ? 292 : 250;
        left = (width - W) / 2;
        top = Math.max(6, (height - h) / 2);
        int x = left + 14, bw = W - 28, y = top + 170;
        switch (v.state()) {
            case 1 -> btn(x, y, bw, 20, "casier.btn.prendre", MineNorthStyle.GREEN, true, 1);
            case 2 -> {
                btn(x, y, bw / 2 - 3, 20, "casier.btn.deposer", MineNorthStyle.CYAN, !v.foreign().isEmpty(), 3);
                btn(x + bw / 2 + 3, y, bw / 2 - 3, 20, "casier.btn.terminer", MineNorthStyle.PINK, true, 4);
            }
            case 3 -> {
                btn(x, y, bw / 2 - 3, 20, "casier.btn.reprendre", MineNorthStyle.GREEN, true, 2);
                btn(x + bw / 2 + 3, y, bw / 2 - 3, 20, "casier.btn.terminer", MineNorthStyle.PINK, true, 4);
            }
            case 4 -> btn(x, y, bw, 20, "casier.btn.terminer", MineNorthStyle.PINK, true, 4);
            default -> { }
        }
        if (v.state() >= 1 && v.state() <= 4) {
            addRenderableWidget(new MineNorthButton(x, y + 24, bw, 16, Component.translatable("casier.btn.perso"), MineNorthStyle.CYAN, () -> act(6)));
        }
        if (v.pending() > 0) {
            addRenderableWidget(new MineNorthButton(x, y + 44, bw, 16, Component.translatable("casier.btn.attente", v.pending()), MineNorthStyle.DARK, () -> act(5)));
        }
        if (v.admin()) {
            int ay = top + h - 44;
            addRenderableWidget(new MineNorthButton(x, ay, bw / 2 - 3, 16, Component.translatable("casier.btn.service", font.plainSubstrByWidth(v.svcName(), 70)), MineNorthStyle.DARK, () -> act(10)));
            addRenderableWidget(new MineNorthButton(x + bw / 2 + 3, ay, bw / 2 - 3, 16, Component.translatable("casier.btn.stuff"), MineNorthStyle.DARK, () -> act(11)));
            addRenderableWidget(new MineNorthButton(x, ay + 20, bw / 3 - 3, 16, Component.translatable("casier.btn.consigne", v.consigne()), MineNorthStyle.DARK, () -> act(12)));
            addRenderableWidget(new MineNorthButton(x + bw / 3 + 1, ay + 20, bw * 2 / 3 - 1, 16, Component.translatable("casier.btn.fichier"), MineNorthStyle.DARK, () -> act(13)).enabled(v.override()));
        }
    }

    private void btn(int x, int y, int w, int hh, String key, int color, boolean on, int action) {
        addRenderableWidget(new MineNorthButton(x, y, w, hh, Component.translatable(key), color, () -> act(action)).enabled(on));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Font f = font;
        MineNorthStyle.panel(g, left, top, W, h, I18n("screen.minenorth_casier.titre"), v.svcName());
        int x = left + 14, y = top + 50;
        MineNorthStyle.card(g, x, y, W - 28, 24, false, v.color());
        String etat = switch (v.state()) {
            case 1 -> I18n("casier.etat.pret");
            case 2 -> I18n("casier.etat.equipe");
            case 3 -> I18n("casier.etat.reprendre");
            case 4 -> I18n("casier.etat.perdu");
            case 5 -> I18n("casier.etat.autre");
            default -> I18n("casier.etat.refuse");
        };
        g.drawString(f, etat, x + 10, y + 8, v.state() == 2 ? MineNorthStyle.OK : MineNorthStyle.TEXT, false);
        int ly = y + 32;
        if (v.state() >= 2 && v.state() <= 4) {
            g.drawString(f, I18n(v.foreign().isEmpty() ? "casier.rien" : "casier.adeposer"), x, ly, v.foreign().isEmpty() ? MineNorthStyle.OK : MineNorthStyle.WARN, false);
            List<String> l = v.foreign();
            for (int i = 0; i < Math.min(l.size(), 5); i++) g.drawString(f, "• " + f.plainSubstrByWidth(l.get(i), W - 60), x + 6, ly + 12 + i * 10, MineNorthStyle.TEXT, false);
            if (l.size() > 5) g.drawString(f, "… +" + (l.size() - 5), x + 6, ly + 12 + 5 * 10, MineNorthStyle.MUTED, false);
            if (v.state() == 2 && v.freeLeave() && v.foreign().isEmpty()) g.drawString(f, I18n("casier.tablette"), x, ly + 66, MineNorthStyle.MUTED, false);
        }
        if (!v.message().isEmpty()) {
            g.drawString(f, f.plainSubstrByWidth(v.message(), W - 28), x, top + 154, MineNorthStyle.WARN, false);
        }
        super.render(g, mx, my, pt);
    }

    private static String I18n(String key) { return Component.translatable(key).getString(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
