package net.vibzz.woodlightingstandards.client.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WoodlightDebugHud {

    private static final int BG_COLOR = 0x80000000;
    private static final int PANEL_W = 280;

    private static class DisplayRow {
        Direction.Axis axis;
        int interiorTotal = 0;
        int chunkCount = 0;
        int filledBurn = 0;
        int maxBurn = 0;
        int lavaCount = 0;
        int fireCount = 0;
        double perTickTotal = 0;
        double cumulative = 0;
        double target = 0;
        int attempt = 0;
        int scheduledFires = 0;
        int scheduledBurn = 0;
        boolean lit = false;
        boolean timerActive = false;
        int groupSize = 1;
        long groupKey = 0;
        boolean firstAbsorb = true;

        void absorb(PortalScanResult.PortalData pd) {
            if (firstAbsorb) {
                axis = pd.axis;
                cumulative = pd.cumulativeProbability;
                target = pd.targetCumulative;
                attempt = pd.attempt;
                lit = pd.lit;
                groupSize = pd.groupSize;
                groupKey = pd.groupKey;
                chunkCount = pd.chunkCount;
                firstAbsorb = false;
            }
            interiorTotal += pd.interior.size();
            filledBurn += pd.filledBurnSlots.size();
            maxBurn += pd.maxBurnSlots;
            lavaCount += pd.effectiveLava.size();
            fireCount += pd.fireCount;
            perTickTotal += pd.perTickProbability;
            scheduledFires += pd.scheduledFires.size();
            scheduledBurn += pd.scheduledBurnAway.size();
            if (pd.timerActive) timerActive = true;
            if (pd.lit) lit = true;
        }
    }

    private static List<DisplayRow> buildRows(PortalScanResult scan) {
        List<DisplayRow> rows = new ArrayList<>();
        Map<Long, DisplayRow> byGroup = new HashMap<>();
        for (PortalScanResult.PortalData pd : scan.portals) {
            if (pd.groupSize > 1 && pd.groupKey != 0) {
                DisplayRow existing = byGroup.get(pd.groupKey);
                if (existing == null) {
                    DisplayRow row = new DisplayRow();
                    row.absorb(pd);
                    byGroup.put(pd.groupKey, row);
                    rows.add(row);
                } else {
                    existing.absorb(pd);
                }
            } else {
                DisplayRow row = new DisplayRow();
                row.absorb(pd);
                rows.add(row);
            }
        }
        return rows;
    }

    public static void render(MatrixStack matrices, PortalScanResult scan) {
        if (!scan.hasResults) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer text = mc.textRenderer;
        int x = 6;
        int y = 6;
        int lineH = 11;

        if (!scan.serverAvailable) {
            int panelW = 240;
            int panelH = 44;
            fill(matrices, x - 4, y - 4, x + panelW, y + panelH, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§cIntegrated server unavailable", x, y, 0xFF5555);
            y += lineH;
            text.drawWithShadow(matrices, "§7Debug only works in singleplayer", x, y, 0xAAAAAA);
            return;
        }

        if (!scan.gameruleEnabled) {
            int panelW = 260;
            int panelH = 44;
            fill(matrices, x - 4, y - 4, x + panelW, y + panelH, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§c§lGAMERULE OFF", x, y, 0xFF5555);
            y += lineH;
            text.drawWithShadow(matrices, "§7/gamerule standardizeWoodlight true", x, y, 0xAAAAAA);
            return;
        }

        if (scan.portals.isEmpty()) {
            int panelH = 30;
            fill(matrices, x - 4, y - 4, x + PANEL_W, y + panelH, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§cNo portal found", x, y, 0xFF5555);
            return;
        }

        List<DisplayRow> rows = buildRows(scan);

        int totalLines = 2;
        for (DisplayRow row : rows) {
            totalLines += 1; // header
            if (row.lit) {
                totalLines += 1; // spacing only
                continue;
            }
            totalLines += 2; // burn slots + prob/avg
            if (row.scheduledFires > 0 || row.scheduledBurn > 0) {
                totalLines += 1;
            }
            totalLines += 1; // attempt/target
            if (row.timerActive) {
                totalLines += 4; // timer active + progress + eta + bar
            } else {
                totalLines += 2; // no timer + hint
            }
            totalLines += 1; // spacing
        }
        int panelH = totalLines * lineH + 8;
        fill(matrices, x - 4, y - 4, x + PANEL_W, y + panelH, BG_COLOR);

        text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
        y += lineH + 2;


        String diffName = "Unknown";
        String diffColor = "§7";
        if (mc.getServer() != null && mc.getServer().getOverworld() != null) {
            int diff = mc.getServer().getOverworld().getDifficulty().getId();
            switch (diff) {
                case 0: diffName = "Peaceful"; diffColor = "§2"; break;
                case 1: diffName = "Easy"; diffColor = "§a"; break;
                case 2: diffName = "Normal"; diffColor = "§e"; break;
                case 3: diffName = "Hard"; diffColor = "§c"; break;
            }
        }
        text.drawWithShadow(matrices,
                "Seed: §7" + scan.worldSeed + "§r | " + diffColor + diffName, x, y, 0xFFFFFF);
        y += lineH + 4;


        for (int i = 0; i < rows.size(); i++) {
            DisplayRow row = rows.get(i);

            String label = rows.size() > 1 ? "Portal " + (i + 1) : "Portal";
            String litTag = row.lit ? " §a§l[LIT]" : "";
            String groupTag = row.groupSize > 1 ? " §dgrouped" : "";
            text.drawWithShadow(matrices,
                    "§a" + label + groupTag + "§r: " + row.interiorTotal + " interior"
                            + " §7(" + row.axis.getName() + " axis)"
                            + (row.chunkCount > 0 ? " §8[" + row.chunkCount + " subchunks]" : "")
                            + litTag,
                    x, y, 0xFFFFFF);
            y += lineH;

            if (row.lit) {
                y += 4;
                continue;
            }

            String burnColor = row.filledBurn == 0 ? "§c" : row.filledBurn == row.maxBurn ? "§a" : "§e";
            String fireColor = row.fireCount > 0 ? "§c" : "§7";
            text.drawWithShadow(matrices,
                    "Burn: " + burnColor + row.filledBurn + "/" + row.maxBurn
                            + "§r | Lava: §6" + row.lavaCount
                            + "§r | Fire: " + fireColor + row.fireCount,
                    x, y, 0xFFFFFF);
            y += lineH;

            if (row.scheduledFires > 0 || row.scheduledBurn > 0) {
                text.drawWithShadow(matrices,
                        "Scheduled: §e" + row.scheduledFires + " fire§r | §6" + row.scheduledBurn + " burn",
                        x, y, 0xFFFFFF);
                y += lineH;
            }

            String probColor = row.perTickTotal > 0.005 ? "§a" : row.perTickTotal > 0.001 ? "§e" : "§c";
            double expectedSec = row.perTickTotal > 0 ? (1.0 / row.perTickTotal) / 20.0 : 0;
            text.drawWithShadow(matrices,
                    "P/tick: " + probColor + String.format("%.4f%%", row.perTickTotal * 100)
                            + "§r | Avg: §b" + String.format("%.1fs", expectedSec),
                    x, y, 0xFFFFFF);
            y += lineH;

            text.drawWithShadow(matrices,
                    String.format("Attempt #§b%d§r | Target: §b%.4f", row.attempt, row.target),
                    x, y, 0xFFFFFF);
            y += lineH;

            if (row.timerActive) {
                double remainingProb = Math.max(0, row.target - row.cumulative);
                double etaSec = row.perTickTotal > 0 ? (remainingProb / row.perTickTotal) / 20.0 : 0;

                text.drawWithShadow(matrices, "§a§lTIMER ACTIVE", x, y, 0x55FF55);
                y += lineH;

                text.drawWithShadow(matrices,
                        String.format("Progress: §e%.4f§r / §b%.4f", row.cumulative, row.target),
                        x, y, 0xFFFFFF);
                y += lineH;

                text.drawWithShadow(matrices,
                        String.format("ETA: §e%.1fs", etaSec), x, y, 0xFFFFFF);
                y += lineH;

                float progress = row.target > 0 ? (float) (row.cumulative / row.target) : 0;
                progress = Math.max(0, Math.min(1, progress));
                int barW = 180;
                int barH = 6;
                int barY = y + 2;
                fill(matrices, x, barY, x + barW, barY + barH, 0xFF333333);
                int filledW = (int) (barW * progress);
                int barColor = progress < 0.5f ? 0xFF55FF55 : progress < 0.8f ? 0xFFFFFF55 : 0xFFFF5555;
                fill(matrices, x, barY, x + filledW, barY + barH, barColor);
                y += barH + 4;
            } else {
                text.drawWithShadow(matrices, "§7No active timer", x, y, 0xAAAAAA);
                y += lineH;
                text.drawWithShadow(matrices,
                        "§7Need: flammable block + lava/fire nearby", x, y, 0xAAAAAA);
                y += lineH;
            }

            y += 4;
        }
    }

    private static void fill(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }
}
