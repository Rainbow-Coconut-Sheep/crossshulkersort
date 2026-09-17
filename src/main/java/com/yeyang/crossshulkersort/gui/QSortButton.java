package com.yeyang.crossshulkersort.gui;

import com.yeyang.crossshulkersort.CrossShulkerSortClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The "Q" button in the inventory screen (1.19.x: MatrixStack rendering).
 * Click it to start the cross-box sort; hold Shift and drag to reposition it (persisted).
 */
public class QSortButton extends ButtonWidget {

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

    /** Small vanilla-style frame with the "Q" label drawn centered. */
    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int bg = this.active ? (this.isHovered() ? 0xFFA0A0A0 : 0xFF888888) : 0xFF555555;
        DrawableHelper.fill(matrices, x, y, x + this.width, y + this.height, 0xFF000000);
        DrawableHelper.fill(matrices, x, y, x + this.width, y + 1, bg);
        DrawableHelper.fill(matrices, x, y + this.height - 1, x + this.width, y + this.height, bg);
        DrawableHelper.fill(matrices, x, y, x + 1, y + this.height, bg);
        DrawableHelper.fill(matrices, x + this.width - 1, y, x + this.width, y + this.height, bg);
        DrawableHelper.drawCenteredTextWithShadow(matrices, MinecraftClient.getInstance().textRenderer,
                getMessage().asOrderedText(), x + this.width / 2, y + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.visible && this.active && button == 0 && Screen.hasShiftDown()) {
            this.dragging = true;
            this.grabOffsetX = mouseX - this.getX();
            this.grabOffsetY = mouseY - this.getY();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging) {
            int newX = MathHelper.clamp((int) Math.round(mouseX - this.grabOffsetX), this.baseX, this.baseX + 156);
            int newY = MathHelper.clamp((int) Math.round(mouseY - this.grabOffsetY), this.baseY, this.baseY + 146);
            this.setX(newX);
            this.setY(newY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging) {
            this.dragging = false;
            CrossShulkerSortClient.config().buttonX = this.getX() - this.baseX;
            CrossShulkerSortClient.config().buttonY = this.getY() - this.baseY;
            CrossShulkerSortClient.config().clamp();
            CrossShulkerSortClient.config().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
