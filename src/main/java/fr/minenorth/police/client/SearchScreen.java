package fr.minenorth.police.client;

import fr.minenorth.police.cuff.SearchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Écran de fouille (charte MineNorth) : inventaire du menotté, objets illégaux entourés de rouge, barre de retrait sur l'objet en cours.
 * Affichage seul : le serveur décide de tout (droits, distance, délai par objet).
 */
public class SearchScreen extends AbstractContainerScreen<SearchMenu> {
    private static final int RED = 0xFFB3263E;

    public SearchScreen(SearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 204;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        MineNorthStyle.rfill(g, x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 9, MineNorthStyle.FRAME);
        MineNorthStyle.rfill(g, x, y, x + imageWidth, y + imageHeight, 7, MineNorthStyle.PANEL);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x, sy = y + s.y;
            MineNorthStyle.rfill(g, sx - 1, sy - 1, sx + 17, sy + 17, 3, i < SearchMenu.N ? MineNorthStyle.LIST : MineNorthStyle.DISABLED);
            if (i >= SearchMenu.N) continue;
            if (menu.illegal(i) && s.hasItem()) {
                MineNorthStyle.rfill(g, sx - 1, sy - 1, sx + 17, sy + 17, 3, RED);
                MineNorthStyle.rfill(g, sx, sy, sx + 16, sy + 16, 2, 0xFF3A1230);
            }
            if (menu.pendingSlot() == i) {
                int h = 16 * menu.pendingProgress() / 100;
                g.fill(sx, sy + 16 - h, sx + 16, sy + 16, 0x99FFE066);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, font.plainSubstrByWidth("Fouille de " + menu.targetName, imageWidth - 16), 8, 6, MineNorthStyle.WHITE, false);
        g.drawString(font, "Votre inventaire", 8, 111, MineNorthStyle.MUTED, false);
        String hint = menu.pendingSlot() >= 0 ? "Retrait en cours... (clic = annuler)" : "Clic : retirer - rouge : illégal";
        g.drawString(font, hint, imageWidth - 8 - font.width(hint), 111, menu.pendingSlot() >= 0 ? MineNorthStyle.WARN : MineNorthStyle.MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
