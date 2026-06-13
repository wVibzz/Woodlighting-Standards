package net.vibzz.woodlightingstandards.client.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

public class WoodlightDebugHud {

    private static final int BG_COLOR = 0x80000000;
    private static final int PANEL_W = 280;

    public static void render(MatrixStack matrices, PortalScanResult scan) {
        if (!scan.hasResults) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer text = mc.textRenderer;
        int x = 6;
        int y = 6;
        int lineH = 11;

        if (!scan.serverAvailable) {
            fill(matrices, x - 4, y - 4, x + 240, y + 44, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§cIntegrated server unavailable", x, y, 0xFF5555);
            y += lineH;
            text.drawWithShadow(matrices, "§7Debug only works in singleplayer", x, y, 0xAAAAAA);
            return;
        }

        if (!scan.gameruleEnabled) {
            fill(matrices, x - 4, y - 4, x + 260, y + 44, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§c§lGAMERULE OFF", x, y, 0xFF5555);
            y += lineH;
            text.drawWithShadow(matrices, "§7/gamerule standardizeWoodlight true", x, y, 0xAAAAAA);
            return;
        }

        if (scan.portals.isEmpty()) {
            fill(matrices, x - 4, y - 4, x + PANEL_W, y + 30, BG_COLOR);
            text.drawWithShadow(matrices, "§6§l█ Woodlight Debug", x, y, 0xFF9900);
            y += lineH + 2;
            text.drawWithShadow(matrices, "§cNo portal found", x, y, 0xFF5555);
            return;
        }

        int totalLines = 4; // header + seed + global progress + bar
        for (PortalScanResult.PortalData pd : scan.portals) {
            totalLines += 1; // portal header
            if (pd.lit) continue;
            totalLines += 2; // burn/lava/fire + per-tick
            if (!pd.scheduledFires.isEmpty() || !pd.scheduledBurnAway.isEmpty()) totalLines += 1;
        }
        int panelH = totalLines * lineH + 12;
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
        y += lineH + 2;

        double totalRate = 0;
        for (PortalScanResult.PortalData pd : scan.portals) {
            if (!pd.lit) totalRate += pd.perTickProbability;
        }
        double remaining = Math.max(0, scan.globalTarget - scan.globalProgress);
        double etaSec = totalRate > 0 ? (remaining / totalRate) / 20.0 : 0;
        text.drawWithShadow(matrices,
                String.format("Attempt #§b%d§r | §e%.4f§r/§b%.4f§r | ETA §e%.1fs",
                        scan.attempt, scan.globalProgress, scan.globalTarget, etaSec),
                x, y, 0xFFFFFF);
        y += lineH;

        float progress = scan.globalTarget > 0 ? (float) (scan.globalProgress / scan.globalTarget) : 0;
        progress = Math.max(0, Math.min(1, progress));
        int barW = 180;
        int barH = 6;
        int barY = y + 2;
        fill(matrices, x, barY, x + barW, barY + barH, 0xFF333333);
        int filledW = (int) (barW * progress);
        int barColor = progress < 0.5f ? 0xFF55FF55 : progress < 0.8f ? 0xFFFFFF55 : 0xFFFF5555;
        fill(matrices, x, barY, x + filledW, barY + barH, barColor);
        y += barH + 6;

        for (int i = 0; i < scan.portals.size(); i++) {
            PortalScanResult.PortalData pd = scan.portals.get(i);

            String label = scan.portals.size() > 1 ? "Portal " + (i + 1) : "Portal";
            String litTag = pd.lit ? " §a§l[LIT]" : "";
            text.drawWithShadow(matrices,
                    "§a" + label + "§r: " + pd.interior.size() + " interior"
                            + " §7(" + pd.axis.getName() + " axis)" + litTag,
                    x, y, 0xFFFFFF);
            y += lineH;

            if (pd.lit) continue;

            int filled = pd.filledBurnSlots.size();
            int max = pd.maxBurnSlots;
            String burnColor = filled == 0 ? "§c" : filled == max ? "§a" : "§e";
            String fireColor = pd.fireCount > 0 ? "§c" : "§7";
            text.drawWithShadow(matrices,
                    "Burn: " + burnColor + filled + "/" + max
                            + "§r | Lava: §6" + pd.effectiveLava.size()
                            + "§r | Fire: " + fireColor + pd.fireCount,
                    x, y, 0xFFFFFF);
            y += lineH;

            String probColor = pd.perTickProbability > 0.005 ? "§a" : pd.perTickProbability > 0.001 ? "§e" : "§c";
            text.drawWithShadow(matrices,
                    "P/tick: " + probColor + String.format("%.4f%%", pd.perTickProbability * 100),
                    x, y, 0xFFFFFF);
            y += lineH;

            if (!pd.scheduledFires.isEmpty() || !pd.scheduledBurnAway.isEmpty()) {
                text.drawWithShadow(matrices,
                        "Scheduled: §e" + pd.scheduledFires.size() + " fire§r | §6" + pd.scheduledBurnAway.size() + " burn",
                        x, y, 0xFFFFFF);
                y += lineH;
            }
        }
    }

    private static void fill(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }
}
