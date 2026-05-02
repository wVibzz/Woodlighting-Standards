package net.vibzz.woodlightingstandards.portal;

import net.minecraft.util.math.BlockPos;
import net.vibzz.woodlightingstandards.fire.FireEventScheduler;
import net.vibzz.woodlightingstandards.util.SeedTimingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PortalGroup {

    public final long memberKey;
    public final List<PortalLightEntry> members;
    public final FireEventScheduler scheduler;
    public final long worldSeed;
    public final int groupAttempt;
    public final double targetCumulative;

    public PortalGroup(List<PortalLightEntry> members, long worldSeed, int groupAttempt) {
        this.members = canonicalSort(members);
        this.memberKey = computeMemberKey(this.members);
        this.worldSeed = worldSeed;
        this.groupAttempt = groupAttempt;

        PortalLightEntry primary = this.members.get(0);

        if (this.members.size() == 1) {
            this.scheduler = new FireEventScheduler(
                    worldSeed, primary.attempt, primary.lowerCorner, primary.axis, primary.portalWidth);
            this.targetCumulative = SeedTimingUtil.calculateTargetCumulative(worldSeed, primary.attempt);
        } else {
            this.scheduler = new FireEventScheduler(
                    worldSeed, groupAttempt, primary.lowerCorner, primary.axis, primary.portalWidth);
            long mixed = mixSeed(worldSeed ^ mixSeed(memberKey) ^ mixSeed(groupAttempt));
            double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / ((double) Long.MAX_VALUE + 1.0);
            this.targetCumulative = -Math.log(1.0 - uniform);
        }
    }

    public double getCumulativeProbability() {
        double sum = 0;
        for (PortalLightEntry e : members) sum += e.cumulativeContribution;
        return sum;
    }

    public double getPerTickProbability() {
        double sum = 0;
        for (PortalLightEntry e : members) sum += e.perTickProbability;
        return sum;
    }

    public List<BlockPos> getCombinedInteriorList() {
        List<BlockPos> all = new ArrayList<>();
        for (PortalLightEntry e : members) all.addAll(e.cachedInterior);
        return all;
    }

    /** Weighted pick by per-tick probability; deterministic from seed + memberKey + groupAttempt. */
    public PortalLightEntry pickWinner() {
        double total = getPerTickProbability();
        if (total <= 0) {
            return members.get(0);
        }

        long mixed = mixSeed(worldSeed ^ mixSeed(memberKey) ^ mixSeed(groupAttempt) ^ 0x57494E4EL);
        double uniform = (double) (mixed & 0x7FFFFFFFFFFFFFFFL) / ((double) Long.MAX_VALUE + 1.0);
        double threshold = uniform * total;

        double accum = 0;
        for (PortalLightEntry e : members) {
            accum += e.perTickProbability;
            if (accum >= threshold) return e;
        }
        return members.get(members.size() - 1);
    }

    public void resetMemberContributions() {
        for (PortalLightEntry e : members) e.cumulativeContribution = 0;
    }

    private static List<PortalLightEntry> canonicalSort(List<PortalLightEntry> in) {
        List<PortalLightEntry> sorted = new ArrayList<>(in);
        sorted.sort(Comparator
                .comparingLong((PortalLightEntry e) -> e.lowerCorner.asLong())
                .thenComparingInt(e -> e.axis.ordinal()));
        return Collections.unmodifiableList(sorted);
    }

    public static long computeMemberKey(List<PortalLightEntry> sortedMembers) {
        long h = 0xcbf29ce484222325L;
        for (PortalLightEntry e : sortedMembers) {
            h ^= e.lowerCorner.asLong();
            h = mixSeed(h);
            h ^= e.axis.ordinal();
            h = mixSeed(h);
        }
        return h;
    }

    private static long mixSeed(long seed) {
        seed ^= (seed >>> 30);
        seed *= 0xbf58476d1ce4e5b9L;
        seed ^= (seed >>> 27);
        seed *= 0x94d049bb133111ebL;
        seed ^= (seed >>> 31);
        return seed;
    }
}
