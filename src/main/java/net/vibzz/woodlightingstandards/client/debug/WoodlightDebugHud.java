package net.vibzz.woodlightingstandards.client.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

public class WoodlightDebugHud {

    private static final int BG_COLOR = 0x80000000;

    public static void render(MatrixStack matrices, PortalScanResult scan) {
        if (!scan.hasResults) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer text = mc.textRenderer;
        int x = 6;
        int y = 6;
        int lineH = 11;

        if (scan.portals.isEmpty()) {
            int panelW = 220;
            int panelH = 30;
            fill(matrices, x - 4, y - 4, x + panelW, y + panelH, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§cNo portal found", x, y, 0xFF5555);
            return;
        }

        // Calculate panel height
        int totalLines = 2; // header + seed
        for (PortalScanResult.PortalData pd : scan.portals) {
            totalLines += 3; // portal header + burn slots + lava/fire
            if (pd.timerActive) {
                totalLines += 5; // attempt + timer active + remaining + elapsed + bar
            } else {
                totalLines += 2; // no timer + hint
            }
            totalLines += 1; // spacing
        }
        int panelW = 220;
        int panelH = totalLines * lineH + 8;
        fill(matrices, x - 4, y - 4, x + panelW, y + panelH, BG_COLOR);

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


        long currentTick = 0;
        if (mc.getServer() != null && mc.getServer().getOverworld() != null) {
            currentTick = mc.getServer().getOverworld().getTime();
        }

        for (int i = 0; i < scan.portals.size(); i++) {
            PortalScanResult.PortalData pd = scan.portals.get(i);


            String label = scan.portals.size() > 1 ? "Portal " + (i + 1) : "Portal";
            text.drawWithShadow(matrices,
                    "§a" + label + "§r: " + pd.interior.size() + " interior"
                            + " §7(" + pd.axis.getName() + " axis)",
                    x, y, 0xFFFFFF);
            y += lineH;


            int filled = pd.filledBurnSlots.size();
            int max = pd.maxBurnSlots;
            String burnColor = filled == 0 ? "§c" : filled == max ? "§a" : "§e";
            String scoreColor = pd.setupScore >= 0.8 ? "§a" : pd.setupScore >= 0.4 ? "§e" : "§c";
            text.drawWithShadow(matrices,
                    "Burn: " + burnColor + filled + "/" + max
                            + "§r | Score: " + scoreColor + String.format("%.0f%%", pd.setupScore * 100),
                    x, y, 0xFFFFFF);
            y += lineH;


            int effectiveLava = pd.effectiveLava.size();
            String lavaColor = effectiveLava == 0 ? "§c" : "§6";
            String lavaScoreColor = pd.lavaScore >= 0.8 ? "§a" : pd.lavaScore >= 0.4 ? "§e" : "§c";
            text.drawWithShadow(matrices,
                    "Lava: " + lavaColor + effectiveLava
                            + "§r | Score: " + lavaScoreColor + String.format("%.0f%%", pd.lavaScore * 100),
                    x, y, 0xFFFFFF);
            y += lineH;


            if (pd.timerActive) {
                double seedSeconds = pd.seedDerivedTicks / 20.0;
                text.drawWithShadow(matrices,
                        String.format("Attempt #§b%d§r: §b%d§r ticks (§b%.1f§rs)",
                                pd.attempt, pd.seedDerivedTicks, seedSeconds), x, y, 0xFFFFFF);
                y += lineH;

                long remaining = pd.timerTargetTick - currentTick;
                long elapsed = currentTick - pd.timerStartTick;

                if (remaining <= 0) {
                    text.drawWithShadow(matrices, "§a§lPORTAL LIT!", x, y, 0x55FF55);
                    y += lineH;
                } else {
                    double remainSec = remaining / 20.0;
                    double totalSec = pd.seedDerivedTicks / 20.0;
                    double elapsedSec = elapsed / 20.0;

                    text.drawWithShadow(matrices, "§a§lTIMER ACTIVE", x, y, 0x55FF55);
                    y += lineH;

                    text.drawWithShadow(matrices,
                            String.format("Remaining: §e%.1f§rs / §b%.1f§rs",
                                    remainSec, totalSec), x, y, 0xFFFFFF);
                    y += lineH;

                    text.drawWithShadow(matrices,
                            String.format("Elapsed: §a%.1f§rs", elapsedSec), x, y, 0xFFFFFF);
                    y += lineH;


                    float progress = (float) elapsed / pd.seedDerivedTicks;
                    progress = Math.max(0, Math.min(1, progress));
                    int barW = 180;
                    int barH = 6;
                    int barY = y + 2;
                    fill(matrices, x, barY, x + barW, barY + barH, 0xFF333333);
                    int filledW = (int) (barW * progress);
                    int barColor = progress < 0.5f ? 0xFF55FF55 : progress < 0.8f ? 0xFFFFFF55 : 0xFFFF5555;
                    fill(matrices, x, barY, x + filledW, barY + barH, barColor);
                    y += barH + 4;
                }
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
