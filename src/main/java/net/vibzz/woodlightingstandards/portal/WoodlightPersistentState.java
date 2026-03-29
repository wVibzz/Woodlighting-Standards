package net.vibzz.woodlightingstandards.portal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

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

    public int getAttemptCount() {
        return attemptCount;
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
            CompoundTag entryTag = list.getCompound(i);
            BlockPos lowerCorner = new BlockPos(
                    entryTag.getInt("LcX"), entryTag.getInt("LcY"), entryTag.getInt("LcZ"));
            Direction.Axis axis = Direction.Axis.valueOf(entryTag.getString("Axis"));
            BlockPos probePos = new BlockPos(
                    entryTag.getInt("PpX"), entryTag.getInt("PpY"), entryTag.getInt("PpZ"));
            int attempt = entryTag.getInt("Attempt");
            long startTick = entryTag.getLong("StartTick");
            long worldSeed = entryTag.getLong("WorldSeed");
            int difficulty = entryTag.getInt("Difficulty");

            savedEntries.add(new PortalLightEntry(lowerCorner, axis, probePos, attempt, startTick, worldSeed, difficulty));
        }
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        tag.putInt("AttemptCount", this.attemptCount);

        ListTag list = new ListTag();
        for (PortalLightEntry entry : savedEntries) {
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
            entryTag.putInt("Difficulty", 2);
            list.add(entryTag);
        }
        tag.put("ActiveTimers", list);

        return tag;
    }
}
