package com.yeyang.crossshulkersort.gui;

import com.yeyang.crossshulkersort.CrossShulkerSortClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The "Q" button in the inventory screen. Vanilla MC style (standard button sprite + label).
 * Click it to start the cross-box sort; hold Shift and drag to reposition it (persisted).
 */
public class QSortButton extends Button {

    /** Top-left corner of the inventory GUI; the button offset is stored relative to it. */
    private final int baseX;
    private final int baseY;

    private boolean dragging = false;
    private double grabOffsetX;
    private double grabOffsetY;

    public QSortButton(int x, int y, int baseX, int baseY) {
        super(x, y, 10, 10, Component.translatable("crossshulkersort.btn.label"),
                b -> com.yeyang.crossshulkersort.CrossShulkerSortClient.requestSort(), DEFAULT_NARRATION);
        this.baseX = baseX;
        this.baseY = baseY;
        setTooltip(Tooltip.create(Component.translatable("crossshulkersort.btn.tooltip")));
    }

    /** Vanilla button visuals, scaled down, with the "Q" label drawn centered. */
    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.extractDefaultSprite(context);
        this.extractDefaultLabel(context.textRendererForWidget(
                this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (this.visible && this.active && click.button() == 0 && click.hasShiftDown()) {
            this.dragging = true;
            this.grabOffsetX = click.x() - this.getX();
            this.grabOffsetY = click.y() - this.getY();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            int newX = Mth.clamp((int) Math.round(event.x() - this.grabOffsetX), this.baseX, this.baseX + 156);
            int newY = Mth.clamp((int) Math.round(event.y() - this.grabOffsetY), this.baseY, this.baseY + 146);
            this.setX(newX);
            this.setY(newY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging) {
            this.dragging = false;
            CrossShulkerSortClient.config().buttonX = this.getX() - this.baseX;
            CrossShulkerSortClient.config().buttonY = this.getY() - this.baseY;
            CrossShulkerSortClient.config().clamp();
            CrossShulkerSortClient.config().save();
            return true;
        }
        return super.mouseReleased(event);
    }
}
