package com.yeyang.crossshulkersort.gui;

import com.yeyang.crossshulkersort.CrossShulkerSortClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The "Q" button in the inventory screen (1.21.11 input system: the base sprite is
 * drawn by the framework, this only paints the centered "Q" icon).
 * Click it to start the cross-box sort; hold Shift and drag to reposition it (persisted).
 */
public class QSortButton extends ButtonWidget {

    /** GLFW modifier bit for Shift (Click.modifiers() bitmask). */
    private static final int MOD_SHIFT = 1;

    /** Top-left corner of the inventory GUI; the button offset is stored relative to it. */
    private final int baseX;
    private final int baseY;

    private boolean dragging = false;
    private double grabOffsetX;
    private double grabOffsetY;

    public QSortButton(int x, int y, int baseX, int baseY) {
        super(x, y, 10, 10, net.minecraft.text.Text.translatable("crossshulkersort.btn.label"),
                b -> com.yeyang.crossshulkersort.CrossShulkerSortClient.requestSort(),
                DEFAULT_NARRATION_SUPPLIER);
        this.baseX = baseX;
        this.baseY = baseY;
        setTooltip(Tooltip.of(net.minecraft.text.Text.translatable("crossshulkersort.btn.tooltip")));
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                getX() + this.width / 2, getY() + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.visible && this.active && click.button() == 0 && (click.modifiers() & MOD_SHIFT) != 0) {
            this.dragging = true;
            this.grabOffsetX = click.x() - this.getX();
            this.grabOffsetY = click.y() - this.getY();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (this.dragging) {
            int newX = MathHelper.clamp((int) Math.round(click.x() - this.grabOffsetX), this.baseX, this.baseX + 156);
            int newY = MathHelper.clamp((int) Math.round(click.y() - this.grabOffsetY), this.baseY, this.baseY + 146);
            this.setX(newX);
            this.setY(newY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.dragging) {
            this.dragging = false;
            CrossShulkerSortClient.config().buttonX = this.getX() - this.baseX;
            CrossShulkerSortClient.config().buttonY = this.getY() - this.baseY;
            CrossShulkerSortClient.config().clamp();
            CrossShulkerSortClient.config().save();
            return true;
        }
        return super.mouseReleased(click);
    }
}
