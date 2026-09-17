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
 * The "Q" button in the inventory screen (1.21.9+ input system: press/click records).
 * Click it to start the cross-box sort; hold Shift and drag to reposition it (persisted).
 * The base button sprite is drawn by the framework; this only paints the centered "Q".
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
        super(x, y, 10, 10, Text.translatable("crossshulkersort.btn.label"),
                b -> com.yeyang.crossshulkersort.CrossShulkerSortClient.requestSort(),
                DEFAULT_NARRATION_SUPPLIER);
        this.baseX = baseX;
        this.baseY = baseY;
        setTooltip(Tooltip.of(Text.translatable("crossshulkersort.btn.tooltip")));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int bg = this.active ? (this.isHovered() ? 0xFFA0A0A0 : 0xFF888888) : 0xFF555555;
        context.fill(x, y, x + this.width, y + this.height, 0xFF000000);
        context.fill(x, y, x + this.width, y + 1, bg);
        context.fill(x, y + this.height - 1, x + this.width, y + this.height, bg);
        context.fill(x, y, x + 1, y + this.height, bg);
        context.fill(x + this.width - 1, y, x + this.width, y + this.height, bg);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                x + this.width / 2, y + 1, 0xFFFFFFFF);
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
