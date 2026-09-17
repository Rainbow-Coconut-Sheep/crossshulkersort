package com.yeyang.crossshulkersort.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ModMenu integration. Soft dependency: this class is only ever loaded when
 * ModMenu itself is installed (entrypoint key {@code modmenu}); Cloth Config
 * is probed again at open time and missing Cloth falls back to a plain note
 * screen, so every install combination keeps working.
 */
public class ModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuEntry::open;
    }

    private static Screen open(Screen parent) {
        try {
            Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder");
            return buildCloth(parent);
        } catch (Throwable t) {
            return fallback(parent);
        }
    }

    private static Component t(String key) {
        return Component.translatable("crossshulkersort." + key);
    }

    private static Component tt(String key) {
        return Component.translatable("crossshulkersort." + key + ".tooltip");
    }

    private static Screen buildCloth(Screen parent) {
        ModConfig cfg = ModConfig.effective();
        ModConfig defaults = new ModConfig();
        me.shedaniel.clothconfig2.api.ConfigBuilder b =
                me.shedaniel.clothconfig2.api.ConfigBuilder.create()
                        .setParentScreen(parent)
                        .setTitle(t("config.title"))
                        .setSavingRunnable(() -> {
                            cfg.clamp();
                            cfg.save();
                            com.yeyang.crossshulkersort.CrossShulkerSortClient.refreshSortOrder();
                        });
        me.shedaniel.clothconfig2.api.ConfigEntryBuilder e = b.entryBuilder();

        me.shedaniel.clothconfig2.api.ConfigCategory scope =
                b.getOrCreateCategory(t("config.cat.scope"));
        scope.addEntry(e.startTextDescription(t("config.note")).build());
        scope.addEntry(e.startBooleanToggle(t("config.includeDyed"), cfg.includeDyed)
                .setDefaultValue(defaults.includeDyed).setTooltip(tt("config.includeDyed"))
                .setSaveConsumer(v -> cfg.includeDyed = v).build());
        scope.addEntry(e.startBooleanToggle(t("config.includeNamed"), cfg.includeNamed)
                .setDefaultValue(defaults.includeNamed).setTooltip(tt("config.includeNamed"))
                .setSaveConsumer(v -> cfg.includeNamed = v).build());
        scope.addEntry(e.startBooleanToggle(t("config.includeLockedFull"), cfg.includeLockedFull)
                .setDefaultValue(defaults.includeLockedFull).setTooltip(tt("config.includeLockedFull"))
                .setSaveConsumer(v -> cfg.includeLockedFull = v).build());

        me.shedaniel.clothconfig2.api.ConfigCategory strategy =
                b.getOrCreateCategory(t("config.cat.strategy"));
        strategy.addEntry(e.startIntSlider(t("config.bulkMinStacks"), cfg.bulkMinStacks, 2, 12)
                .setDefaultValue(defaults.bulkMinStacks).setTooltip(tt("config.bulkMinStacks"))
                .setSaveConsumer(v -> cfg.bulkMinStacks = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.bulkFirst"), cfg.bulkFirst)
                .setDefaultValue(defaults.bulkFirst).setTooltip(tt("config.bulkFirst"))
                .setSaveConsumer(v -> cfg.bulkFirst = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.homeHealing"), cfg.homeHealing)
                .setDefaultValue(defaults.homeHealing).setTooltip(tt("config.homeHealing"))
                .setSaveConsumer(v -> cfg.homeHealing = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.overflowEnabled"), cfg.overflowEnabled)
                .setDefaultValue(defaults.overflowEnabled).setTooltip(tt("config.overflowEnabled"))
                .setSaveConsumer(v -> cfg.overflowEnabled = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.defragEnabled"), cfg.defragEnabled)
                .setDefaultValue(defaults.defragEnabled).setTooltip(tt("config.defragEnabled"))
                .setSaveConsumer(v -> cfg.defragEnabled = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.defragBulkEnabled"), cfg.defragBulkEnabled)
                .setDefaultValue(defaults.defragBulkEnabled).setTooltip(tt("config.defragBulkEnabled"))
                .setSaveConsumer(v -> cfg.defragBulkEnabled = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.topUpEnabled"), cfg.topUpEnabled)
                .setDefaultValue(defaults.topUpEnabled).setTooltip(tt("config.topUpEnabled"))
                .setSaveConsumer(v -> cfg.topUpEnabled = v).build());
        strategy.addEntry(e.startBooleanToggle(t("config.reservationEnabled"), cfg.reservationEnabled)
                .setDefaultValue(defaults.reservationEnabled).setTooltip(tt("config.reservationEnabled"))
                .setSaveConsumer(v -> cfg.reservationEnabled = v).build());
        strategy.addEntry(e.startIntSlider(t("config.maxRounds"), cfg.maxRounds, 1, 5)
                .setDefaultValue(defaults.maxRounds).setTooltip(tt("config.maxRounds"))
                .setSaveConsumer(v -> cfg.maxRounds = v).build());

        me.shedaniel.clothconfig2.api.ConfigCategory display =
                b.getOrCreateCategory(t("config.cat.display"));
        display.addEntry(e.startIntSlider(t("config.buttonX"), cfg.buttonX, 0, 156)
                .setDefaultValue(defaults.buttonX).setTooltip(tt("config.buttonX"))
                .setSaveConsumer(v -> cfg.buttonX = v).build());
        display.addEntry(e.startIntSlider(t("config.buttonY"), cfg.buttonY, 0, 146)
                .setDefaultValue(defaults.buttonY).setTooltip(tt("config.buttonY"))
                .setSaveConsumer(v -> cfg.buttonY = v).build());
        display.addEntry(e.startBooleanToggle(t("config.chatReport"), cfg.chatReport)
                .setDefaultValue(defaults.chatReport).setTooltip(tt("config.chatReport"))
                .setSaveConsumer(v -> cfg.chatReport = v).build());
        display.addEntry(e.startBooleanToggle(t("config.useItemScrollerOrder"), cfg.useItemScrollerOrder)
                .setDefaultValue(defaults.useItemScrollerOrder).setTooltip(tt("config.useItemScrollerOrder"))
                .setSaveConsumer(v -> cfg.useItemScrollerOrder = v).build());
        display.addEntry(e.startBooleanToggle(t("config.debugLog"), cfg.debugLog)
                .setDefaultValue(defaults.debugLog).setTooltip(tt("config.debugLog"))
                .setSaveConsumer(v -> cfg.debugLog = v).build());
        return b.build();
    }

    private static Screen fallback(Screen parent) {
        return new Screen(t("config.title")) {
            @Override
            protected void init() {
                this.addRenderableWidget(Button.builder(t("config.back"),
                        btn -> Minecraft.getInstance().setScreenAndShow(parent))
                        .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
            }

            @Override
            public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context,
                                           int mouseX, int mouseY, float deltaTicks) {
                super.extractRenderState(context, mouseX, mouseY, deltaTicks);
                context.centeredText(Minecraft.getInstance().font, t("config.needCloth"),
                        this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            }
        };
    }
}
