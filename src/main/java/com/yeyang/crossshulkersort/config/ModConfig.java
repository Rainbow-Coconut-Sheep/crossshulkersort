package com.yeyang.crossshulkersort.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yeyang.crossshulkersort.CrossShulkerSortClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON config plus the live effective instance. Every strategy switch maps 1:1
 * to a planning/execution stage; turning a stage off restores pre-feature
 * behavior. Singleplayer reads this file live; dedicated servers read their own
 * copy at startup (see effective()).
 */
public class ModConfig {

    /** Button offset from the top-left of the inventory GUI (GUI is 176x166). */
    public int buttonX = 150;
    public int buttonY = 4;

    /** Verbose engine logging into latest.log for troubleshooting. */
    public boolean debugLog = true;

    // ---- scope (which boxes take part)
    /** Dyed shulker boxes also take part (default only plain undyed ones). */
    public boolean includeDyed = false;
    /** Named boxes or ones with special components also take part. */
    public boolean includeNamed = false;
    /** Full single-type boxes (27/27) are re-sorted instead of skipped. */
    public boolean includeLockedFull = false;

    // ---- strategy (SortPlan pipeline stages)
    /** Types with at least this many stacks count as bulk and pack first. */
    public int bulkMinStacks = 6;
    /** Bulk types span box boundaries; off = everything splits smallest-first. */
    public boolean bulkFirst = true;
    /** Boxes keep the content they already hold; off = full repack every sort. */
    public boolean homeHealing = true;
    /** Home-full leftovers spill into the next box with room instead of staying loose. */
    public boolean overflowEnabled = true;
    /** Pull split types back into one box when room can be made without new splits. */
    public boolean defragEnabled = true;
    /** Same as above for bulk types spanning several boxes. */
    public boolean defragBulkEnabled = true;
    /** Fill partial stacks to max from same-type leftovers. */
    public boolean topUpEnabled = true;
    /** Unstackables live in their own trailing boxes, never mixed with stackables. */
    public boolean reservationEnabled = true;
    /** Max quiet rounds per Q press until the layout stops changing. */
    public int maxRounds = 3;

    // ---- display
    /** Post the finished/emptied summary to chat (errors always show). */
    public boolean chatReport = true;
    /** Mirror Item Scroller's sort order when the mod is present. */
    public boolean useItemScrollerOrder = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ModConfig FALLBACK = new ModConfig();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("crossshulkersort.json");
    }

    public static ModConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                ModConfig loaded = GSON.fromJson(Files.readString(path), ModConfig.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (Exception e) {
                CrossShulkerSortClient.LOGGER.warn("Failed to read config, using defaults", e);
            }
        }
        return new ModConfig();
    }

    /** Live config on the executing side, defaults when unknown (dedicated boot). */
    public static ModConfig effective() {
        try {
            ModConfig c = CrossShulkerSortClient.config();
            return c != null ? c : FALLBACK;
        } catch (Throwable t) {
            return FALLBACK;
        }
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            CrossShulkerSortClient.LOGGER.warn("Failed to save config", e);
        }
    }

    public void clamp() {
        buttonX = Math.max(0, Math.min(156, buttonX));
        buttonY = Math.max(0, Math.min(146, buttonY));
        bulkMinStacks = Math.max(2, Math.min(12, bulkMinStacks));
        maxRounds = Math.max(1, Math.min(5, maxRounds));
    }
}
