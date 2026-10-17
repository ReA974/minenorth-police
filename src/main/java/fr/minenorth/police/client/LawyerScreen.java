package fr.minenorth.police.client;

import fr.minenorth.police.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Mallette d'avocat : choisir l'amende ou la condamnation à contester, écrire la défense, déposer. Le serveur revérifie tout. */
public class LawyerScreen extends Screen {
    private static final int W = 420, H = 270, ROWS = 5;

    private final UUID client;
    private final String clientName;
    private final List<ModNetwork.LawRec> recs;
    private int left, top, page, selected = -1;
    private EditBox defense;
    private String defenseText = "";

    private LawyerScreen(UUID client, String name, List<ModNetwork.LawRec> recs) {
        super(Component.literal("Mallette d'avocat"));
        this.client = client;
        this.clientName = name;
        this.recs = recs;
    }

    public static void open(UUID client, String name, List<ModNetwork.LawRec> recs) {
        Minecraft.getInstance().setScreen(new LawyerScreen(client, name, recs));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        defense = new EditBox(font, left + 20, top + 188, W - 40, 18, Component.literal("Défense"));
        defense.setMaxLength(120);
        defense.setValue(defenseText);
        defense.setResponder(v -> defenseText = v);
        addRenderableWidget(defense);
        int pages = Math.max(1, (recs.size() + ROWS - 1) / ROWS);
        addRenderableWidget(new MineNorthButton(left + W - 90, top + 46, 30, 16, Component.literal("<"), MineNorthStyle.DARK, () -> { if (page > 0) page--; }));
        addRenderableWidget(new MineNorthButton(left + W - 56, top + 46, 30, 16, Component.literal(">"), MineNorthStyle.DARK, () -> { if (page < pages - 1) page++; }));
        addRenderableWidget(new MineNorthButton(left + 20, top + 222, 180, 24, Component.literal("Déposer la contestation"), MineNorthStyle.GREEN, this::submit));
        addRenderableWidget(new MineNorthButton(left + W - 200, top + 222, 180, 24, Component.literal("Annuler"), MineNorthStyle.DARK, this::onClose));
    }

    private void submit() {
        if (selected < 0 || selected >= recs.size()) return;
        ModNetwork.CHANNEL.sendToServer(new ModNetwork.LawyerSubmitPacket(client, recs.get(selected).id(), defenseText));
        onClose();
    }

    private int rowY(int i) { return top + 70 + i * 22; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= recs.size()) break;
            int y = rowY(i);
            if (mx >= left + 20 && mx <= left + W - 20 && my >= y && my <= y + 20) { selected = idx; return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        MineNorthStyle.panel(g, left, top, W, H, "Mallette d'avocat", "Client : " + clientName);
        g.drawString(font, "Choisissez la décision à contester", left + 20, top + 50, MineNorthStyle.MUTED, false);
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm");
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= recs.size()) break;
            ModNetwork.LawRec r = recs.get(idx);
            int y = rowY(i);
            boolean sel = idx == selected;
            boolean hover = mx >= left + 20 && mx <= left + W - 20 && my >= y && my <= y + 20;
            MineNorthStyle.card(g, left + 20, y, W - 40, 20, sel || hover, sel ? MineNorthStyle.GREEN : MineNorthStyle.CYAN);
            String kind = r.type() == 0 ? "AMENDE" : "CONDAMN.";
            g.drawString(font, kind, left + 28, y + 6, MineNorthStyle.CYAN, false);
            g.drawString(font, f.format(new Date(r.time())), left + 80, y + 6, MineNorthStyle.MUTED, false);
            String txt = font.plainSubstrByWidth(r.text(), 190);
            g.drawString(font, txt, left + 130, y + 6, MineNorthStyle.WHITE, false);
            if (r.type() == 0) {
                String amt = MineNorthStyle.euros(r.amount()) + (r.paid() ? " ✔" : "");
                g.drawString(font, amt, left + W - 24 - font.width(amt), y + 6, r.paid() ? MineNorthStyle.OK : MineNorthStyle.WARN, false);
            }
        }
        g.drawString(font, "Arguments de la défense (10 à 120 caractères)", left + 20, top + 176, MineNorthStyle.MUTED, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
