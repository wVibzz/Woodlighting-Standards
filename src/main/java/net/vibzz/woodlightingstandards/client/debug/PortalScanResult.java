package net.vibzz.woodlightingstandards.client.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.vibzz.woodlightingstandards.Woodlightingstandards;
import net.vibzz.woodlightingstandards.portal.PortalGroup;
import net.vibzz.woodlightingstandards.portal.PortalLightEntry;
import net.vibzz.woodlightingstandards.portal.WoodlightTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PortalScanResult {
    public boolean hasResults = false;
    public boolean gameruleEnabled = true;
    public boolean serverAvailable = false;
    public final List<PortalData> portals = new ArrayList<>();
    public long worldSeed = 0;

    public final List<BlockPos> portalFrame = new ArrayList<>();
    public final Set<BlockPos> portalInterior = new HashSet<>();
    public final Set<BlockPos> filledBurnSlots = new HashSet<>();
    public final Set<BlockPos> lavaSources = new HashSet<>();
    public final Set<BlockPos> fireBlocks = new HashSet<>();
    public final Map<BlockPos, int[]> flammables = new LinkedHashMap<>();

    public static class PortalData {
        public Direction.Axis axis;
        public final List<BlockPos> frame = new ArrayList<>();
        public final Set<BlockPos> interior = new HashSet<>();
        public final Set<BlockPos> filledBurnSlots = new HashSet<>();
        public final Set<BlockPos> effectiveLava = new HashSet<>();
        public int maxBurnSlots = 0;

        public boolean lit = false;
        public double perTickProbability = 0.0;
        public double cumulativeProbability = 0.0;
        public double targetCumulative = 0.0;
        public int chunkCount = 0;
        public int fireCount = 0;
        public final Map<BlockPos, Long> scheduledFires = new LinkedHashMap<>();
        public final Map<BlockPos, Long> scheduledBurnAway = new LinkedHashMap<>();

        public boolean timerActive = false;
        public int attempt = 0;
        public int groupSize = 1;
        public long groupKey = 0;
    }

    public void update() {
        clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getServer() == null) {
            hasResults = true;
            serverAvailable = false;
            return;
        }

        ServerWorld serverWorld = mc.getServer().getOverworld();
        if (serverWorld == null) {
            hasResults = true;
            serverAvailable = false;
            return;
        }

        serverAvailable = true;
        gameruleEnabled = serverWorld.getGameRules().getBoolean(Woodlightingstandards.STANDARDIZE_WOODLIGHT);
        worldSeed = serverWorld.getSeed();

        List<PortalLightEntry> entries = WoodlightTracker.getInstance().getAllEntries(serverWorld);
        if (entries == null || entries.isEmpty()) {
            hasResults = true;
            return;
        }

        Map<PortalLightEntry, PortalGroup> entryToGroup = new HashMap<>();
        Map<Long, PortalGroup> groupMap = WoodlightTracker.getInstance().getActiveGroups(serverWorld);
        if (groupMap != null) {
            for (PortalGroup g : groupMap.values()) {
                for (PortalLightEntry m : g.members) entryToGroup.put(m, g);
            }
        }

        for (PortalLightEntry entry : entries) {
            PortalData pd = new PortalData();
            pd.axis = entry.axis;
            pd.lit = entry.lit;

            pd.frame.addAll(entry.cachedFrame);
            pd.interior.addAll(entry.cachedInterior);
            pd.effectiveLava.addAll(entry.cachedLava);
            pd.filledBurnSlots.addAll(entry.cachedFlammable);
            pd.maxBurnSlots = entry.cachedInterior.size() * 2;
            pd.fireCount = entry.cachedFireCount;

            pd.timerActive = entry.perTickProbability > 0;
            pd.attempt = entry.attempt;
            pd.perTickProbability = entry.perTickProbability;
            pd.chunkCount = entry.portalSubChunks.size();

            PortalGroup group = entryToGroup.get(entry);
            if (group != null) {
                pd.cumulativeProbability = group.getCumulativeProbability();
                pd.targetCumulative = group.targetCumulative;
                pd.groupSize = group.members.size();
                pd.groupKey = group.memberKey;
                pd.scheduledFires.putAll(group.scheduler.getScheduledLavaFires());
                pd.scheduledFires.putAll(group.scheduler.getScheduledSpreadFires());
                pd.scheduledBurnAway.putAll(group.scheduler.getScheduledBurnAway());
            } else {
                pd.cumulativeProbability = entry.cumulativeContribution;
                pd.targetCumulative = 0;
                pd.groupSize = 1;
                pd.groupKey = 0;
            }

            portals.add(pd);

            if (!pd.lit) {
                portalFrame.addAll(pd.frame);
                portalInterior.addAll(pd.interior);
                filledBurnSlots.addAll(pd.filledBurnSlots);
                lavaSources.addAll(pd.effectiveLava);
                fireBlocks.addAll(entry.cachedFirePositions);
            }
        }

        hasResults = true;
    }

    public void clear() {
        hasResults = false;
        gameruleEnabled = true;
        serverAvailable = false;
        portals.clear();
        portalFrame.clear();
        portalInterior.clear();
        filledBurnSlots.clear();
        lavaSources.clear();
        fireBlocks.clear();
        flammables.clear();
        worldSeed = 0;
    }
}
