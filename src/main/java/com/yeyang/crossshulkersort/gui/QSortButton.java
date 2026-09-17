package com.yeyang.crossshulkersort.gui;

import com.yeyang.crossshulkersort.CrossShulkerSortClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.MathHelper;

/**
 * The "Q" button in the inventory screen (1.15: pre-MatrixStack GL rendering,
 * String messages, no Tooltip class).
 * Click it to start the cross-box sort; hold Shift and drag to reposition it (persisted).
 */
public class QSortButton extends ButtonWidget {

    /** Top-left corner of the inventory GUI; the button offset is stored relative to it. */
    private final int baseX;
    private final int baseY;

    private final String label;

    private boolean dragging = false;
    private double grabOffsetX;
    private double grabOffsetY;

    public QSortButton(int x, int y, int baseX, int baseY) {
        super(x, y, 10, 10, I18n.translate("crossshulkersort.btn.label"),
                b -> com.yeyang.crossshulkersort.CrossShulkerSortClient.requestSort());
        this.baseX = baseX;
        this.baseY = baseY;
        this.label = I18n.translate("crossshulkersort.btn.label");
    }

    /** Small vanilla-style frame with the "Q" label drawn centered. */
    @Override
    public void renderButton(int mouseX, int mouseY, float delta) {
        int x = this.x;
        int y = this.y;
        int bg = this.active ? (this.isHovered() ? 0xFFA0A0A0 : 0xFF888888) : 0xFF555555;
        DrawableHelper.fill(x, y, x + this.width, y + this.height, 0xFF000000);
        DrawableHelper.fill(x, y, x + this.width, y + 1, bg);
        DrawableHelper.fill(x, y + this.height - 1, x + this.width, y + this.height, bg);
        DrawableHelper.fill(x, y, x + 1, y + this.height, bg);
        DrawableHelper.fill(x + this.width - 1, y, x + this.width, y + this.height, bg);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        tr.draw(label, x + this.width / 2 - tr.getStringWidth(label) / 2, y + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.visible && this.active && button == 0 && Screen.hasShiftDown()) {
            this.dragging = true;
            this.grabOffsetX = mouseX - this.x;
            this.grabOffsetY = mouseY - this.y;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging) {
            int newX = MathHelper.clamp((int) Math.round(mouseX - this.grabOffsetX), this.baseX, this.baseX + 156);
            int newY = MathHelper.clamp((int) Math.round(mouseY - this.grabOffsetY), this.baseY, this.baseY + 146);
            this.x = newX;
            this.y = newY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging) {
            this.dragging = false;
            CrossShulkerSortClient.config().buttonX = this.x - this.baseX;
            CrossShulkerSortClient.config().buttonY = this.y - this.baseY;
            CrossShulkerSortClient.config().clamp();
            CrossShulkerSortClient.config().save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
