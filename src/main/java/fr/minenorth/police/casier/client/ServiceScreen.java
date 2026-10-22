package fr.minenorth.police.casier.client;

import fr.minenorth.police.casier.ServiceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Écran des cases du stuff (modèle) et de la consigne, dessiné aux couleurs et aux bords arrondis MineNorth. */
public class ServiceScreen extends AbstractContainerScreen<ServiceMenu> {
    public ServiceScreen(ServiceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + menu.rows * 18;
        this.inventoryLabelY = 18 + menu.rows * 18 + 3;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        MineNorthStyle.rfill(g, x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 9, MineNorthStyle.FRAME);
        MineNorthStyle.rfill(g, x, y, x + imageWidth, y + imageHeight, 7, MineNorthStyle.PANEL);
        for (Slot s : menu.slots) {
            MineNorthStyle.rfill(g, x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 3, MineNorthStyle.LIST);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, MineNorthStyle.WHITE, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, MineNorthStyle.MUTED, false);
        if (menu.ghost) g.drawString(font, Component.translatable("menu.minenorth_casier.astuce"), 70, inventoryLabelY, MineNorthStyle.WARN, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
