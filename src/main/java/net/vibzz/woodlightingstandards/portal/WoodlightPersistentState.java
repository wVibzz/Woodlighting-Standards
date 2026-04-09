package net.vibzz.woodlightingstandards.portal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WoodlightPersistentState extends PersistentState {
    private static final String ID = "wls";

    private int attemptCount = 0;
    private final List<PortalLightEntry> savedEntries = new ArrayList<>();

    public WoodlightPersistentState() {
        super(ID);
    }

    public static WoodlightPersistentState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(WoodlightPersistentState::new, ID);
    }

    public int peekNextAttempt() {
        return attemptCount + 1;
    }

    public void commitAttempt() {
        attemptCount++;
        markDirty();
    }

    public List<PortalLightEntry> getSavedEntries() {
        return savedEntries;
    }

    public void saveEntries(List<PortalLightEntry> entries) {
        savedEntries.clear();
        savedEntries.addAll(entries);
        markDirty();
    }

    @Override
    public void fromTag(CompoundTag tag) {
        this.attemptCount = tag.getInt("AttemptCount");

        savedEntries.clear();
        ListTag list = tag.getList("ActiveTimers", 10);
        for (int i = 0; i < list.size(); i++) {
            savedEntries.add(entryFromTag(list.getCompound(i)));
        }
    }

    private PortalLightEntry entryFromTag(CompoundTag entryTag) {
        BlockPos lowerCorner = new BlockPos(
                entryTag.getInt("LcX"), entryTag.getInt("LcY"), entryTag.getInt("LcZ"));
        Direction.Axis axis = Direction.Axis.valueOf(entryTag.getString("Axis"));
        BlockPos probePos = new BlockPos(
                entryTag.getInt("PpX"), entryTag.getInt("PpY"), entryTag.getInt("PpZ"));
        int attempt = entryTag.getInt("Attempt");
        long startTick = entryTag.getLong("StartTick");
        long worldSeed = entryTag.getLong("WorldSeed");
        double prob = entryTag.getDouble("Probability");
        double cumulative = entryTag.getDouble("Cumulative");
        int portalWidth = entryTag.getInt("PortalWidth");
        int portalHeight = entryTag.getInt("PortalHeight");

        PortalLightEntry entry = new PortalLightEntry(lowerCorner, axis, probePos, attempt, startTick, worldSeed, prob, portalWidth, portalHeight);
        entry.cumulativeProbability = cumulative;

        Map<BlockPos, Long> fires = new HashMap<>();
        ListTag fireList = entryTag.getList("ScheduledFires", 10);
        for (int j = 0; j < fireList.size(); j++) {
            CompoundTag ft = fireList.getCompound(j);
            fires.put(new BlockPos(ft.getInt("X"), ft.getInt("Y"), ft.getInt("Z")), ft.getLong("T"));
        }

        Map<BlockPos, Long> burnAway = new HashMap<>();
        ListTag burnList = entryTag.getList("ScheduledBurnAway", 10);
        for (int j = 0; j < burnList.size(); j++) {
            CompoundTag bt = burnList.getCompound(j);
            burnAway.put(new BlockPos(bt.getInt("X"), bt.getInt("Y"), bt.getInt("Z")), bt.getLong("T"));
        }

        Map<Long, Integer> counters = new HashMap<>();
        ListTag counterList = entryTag.getList("PositionCounters", 10);
        for (int j = 0; j < counterList.size(); j++) {
            CompoundTag ct = counterList.getCompound(j);
            counters.put(ct.getLong("K"), ct.getInt("C"));
        }

        entry.fireScheduler.loadState(fires, burnAway, counters);
        return entry;
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        tag.putInt("AttemptCount", this.attemptCount);

        ListTag list = new ListTag();
        for (PortalLightEntry entry : savedEntries) {
            list.add(entryToTag(entry));
        }
        tag.put("ActiveTimers", list);

        return tag;
    }

    private CompoundTag entryToTag(PortalLightEntry entry) {
        CompoundTag entryTag = writeEntryHeader(entry);

        ListTag fireList = new ListTag();
        for (Map.Entry<BlockPos, Long> fe : entry.fireScheduler.getScheduledFires().entrySet()) {
            CompoundTag ft = new CompoundTag();
            ft.putInt("X", fe.getKey().getX());
            ft.putInt("Y", fe.getKey().getY());
            ft.putInt("Z", fe.getKey().getZ());
            ft.putLong("T", fe.getValue());
            fireList.add(ft);
        }
        entryTag.put("ScheduledFires", fireList);

        ListTag burnList = new ListTag();
        for (Map.Entry<BlockPos, Long> be : entry.fireScheduler.getScheduledBurnAway().entrySet()) {
            CompoundTag bt = new CompoundTag();
            bt.putInt("X", be.getKey().getX());
            bt.putInt("Y", be.getKey().getY());
            bt.putInt("Z", be.getKey().getZ());
            bt.putLong("T", be.getValue());
            burnList.add(bt);
        }
        entryTag.put("ScheduledBurnAway", burnList);

        ListTag counterList = new ListTag();
        for (Map.Entry<Long, Integer> ce : entry.fireScheduler.getPositionCounters().entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putLong("K", ce.getKey());
            ct.putInt("C", ce.getValue());
            counterList.add(ct);
        }
        entryTag.put("PositionCounters", counterList);

        return entryTag;
    }

    private static CompoundTag writeEntryHeader(PortalLightEntry entry) {
        CompoundTag entryTag = new CompoundTag();
        entryTag.putInt("LcX", entry.lowerCorner.getX());
        entryTag.putInt("LcY", entry.lowerCorner.getY());
        entryTag.putInt("LcZ", entry.lowerCorner.getZ());
        entryTag.putString("Axis", entry.axis.name());
        entryTag.putInt("PpX", entry.probePos.getX());
        entryTag.putInt("PpY", entry.probePos.getY());
        entryTag.putInt("PpZ", entry.probePos.getZ());
        entryTag.putInt("Attempt", entry.attempt);
        entryTag.putLong("StartTick", entry.startTick);
        entryTag.putLong("WorldSeed", entry.worldSeed);
        entryTag.putDouble("Probability", entry.perTickProbability);
        entryTag.putDouble("Cumulative", entry.cumulativeProbability);
        entryTag.putInt("PortalWidth", entry.portalWidth);
        entryTag.putInt("PortalHeight", entry.portalHeight);
        return entryTag;
    }
}
