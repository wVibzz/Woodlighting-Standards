package net.vibzz.woodlightingstandards.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors vanilla FireBlock.registerDefaultFlammables()
 */
public class FlammableBlockUtil {

    private static final Map<Block, int[]> FLAMMABLES = new HashMap<>();

    static {
        // Format: { burnChance, spreadChance }

        // Wood building blocks: burn=5, spread=20
        int[] p5_20 = {5, 20};
        for (Block b : new Block[]{
                Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
                Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
                Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB,
                Blocks.JUNGLE_SLAB, Blocks.ACACIA_SLAB, Blocks.DARK_OAK_SLAB,
                Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.BIRCH_FENCE_GATE,
                Blocks.JUNGLE_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE, Blocks.ACACIA_FENCE_GATE,
                Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE,
                Blocks.JUNGLE_FENCE, Blocks.DARK_OAK_FENCE, Blocks.ACACIA_FENCE,
                Blocks.OAK_STAIRS, Blocks.BIRCH_STAIRS, Blocks.SPRUCE_STAIRS,
                Blocks.JUNGLE_STAIRS, Blocks.ACACIA_STAIRS, Blocks.DARK_OAK_STAIRS,
                Blocks.COMPOSTER, Blocks.BEEHIVE, Blocks.BEE_NEST
        }) {
            FLAMMABLES.put(b, p5_20);
        }

        // Logs/wood: burn=5, spread=5
        int[] p5_5 = {5, 5};
        for (Block b : new Block[]{
                Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
                Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
                Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG,
                Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD,
                Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD,
                Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_BIRCH_WOOD,
                Blocks.STRIPPED_JUNGLE_WOOD, Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD
        }) {
            FLAMMABLES.put(b, p5_5);
        }

        // Leaves: burn=30, spread=60
        int[] p30_60 = {30, 60};
        for (Block b : new Block[]{
                Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
                Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES
        }) {
            FLAMMABLES.put(b, p30_60);
        }

        // Wool: burn=30, spread=60
        for (Block b : new Block[]{
                Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL,
                Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
                Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL,
                Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
                Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL,
                Blocks.BLACK_WOOL
        }) {
            FLAMMABLES.put(b, p30_60);
        }

        // Carpets: burn=60, spread=20
        int[] p60_20 = {60, 20};
        for (Block b : new Block[]{
                Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.MAGENTA_CARPET,
                Blocks.LIGHT_BLUE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIME_CARPET,
                Blocks.PINK_CARPET, Blocks.GRAY_CARPET, Blocks.LIGHT_GRAY_CARPET,
                Blocks.CYAN_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET,
                Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.RED_CARPET,
                Blocks.BLACK_CARPET
        }) {
            FLAMMABLES.put(b, p60_20);
        }

        // Plants: burn=60, spread=100
        int[] p60_100 = {60, 100};
        for (Block b : new Block[]{
                Blocks.GRASS, Blocks.FERN, Blocks.DEAD_BUSH,
                Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
                Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.DANDELION, Blocks.POPPY,
                Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
                Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY,
                Blocks.WITHER_ROSE, Blocks.SWEET_BERRY_BUSH
        }) {
            FLAMMABLES.put(b, p60_100);
        }

        // Misc individual blocks
        FLAMMABLES.put(Blocks.DRIED_KELP_BLOCK, p30_60);
        FLAMMABLES.put(Blocks.COAL_BLOCK, p5_5);
        FLAMMABLES.put(Blocks.BOOKSHELF, new int[]{30, 20});
        FLAMMABLES.put(Blocks.TNT, new int[]{15, 100});
        FLAMMABLES.put(Blocks.VINE, new int[]{15, 100});
        FLAMMABLES.put(Blocks.HAY_BLOCK, new int[]{60, 20});
        FLAMMABLES.put(Blocks.TARGET, new int[]{15, 20});
        FLAMMABLES.put(Blocks.BAMBOO, new int[]{60, 60});
        FLAMMABLES.put(Blocks.SCAFFOLDING, new int[]{60, 60});
        FLAMMABLES.put(Blocks.LECTERN, new int[]{30, 20});
    }

    public static int getBurnChance(BlockState state) {
        if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) return 0;
        int[] v = FLAMMABLES.get(state.getBlock());
        return v != null ? v[0] : 0;
    }

    public static int getSpreadChance(BlockState state) {
        if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) return 0;
        int[] v = FLAMMABLES.get(state.getBlock());
        return v != null ? v[1] : 0;
    }

    public static boolean isFlammable(BlockState state) {
        return getBurnChance(state) > 0;
    }
}
