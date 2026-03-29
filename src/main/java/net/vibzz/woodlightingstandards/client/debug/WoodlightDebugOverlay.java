package net.vibzz.woodlightingstandards.client.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import java.util.Set;

public class WoodlightDebugOverlay {

    public static void render(MatrixStack matrices, Camera camera, PortalScanResult scan) {
        if (!scan.hasResults) return;

        Vec3d cam = camera.getPos();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0f);

        for (PortalScanResult.PortalData pd : scan.portals) {
            if (pd.timerActive) {
                float pulse = (float) (0.15 + 0.1 * Math.sin(System.currentTimeMillis() / 200.0));
                for (BlockPos pos : pd.frame) {
                    drawInnerFaces(matrices, pos, pd.interior, 0.1f, 0.8f, 0.9f, 0.9f, pulse);
                }
            } else {
                for (BlockPos pos : pd.frame) {
                    drawInnerFaces(matrices, pos, pd.interior, 0.6f, 0.2f, 0.8f, 0.8f, 0.25f);
                }
            }
        }

        for (BlockPos pos : scan.filledBurnSlots) {
            int[] vals = scan.flammables.get(pos);
            if (vals == null) continue;
            float score = Math.min(1f, (vals[0] / 60f * 0.4f) + (vals[1] / 100f * 0.6f));
            float r = Math.min(1f, score * 2f);
            float g = Math.min(1f, (1f - score) * 2f);
            drawInnerFaces(matrices, pos, scan.portalInterior, r, g, 0.1f, 0.6f, 0.12f);
        }

        for (BlockPos pos : scan.fireBlocks) {
            drawOutline(matrices, pos, 0.6f, 0.9f);
        }

        for (BlockPos pos : scan.lavaSources) {
            drawOutline(matrices, pos, 0.4f, 0.7f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
        matrices.pop();
    }

    private static void drawInnerFaces(MatrixStack matrices, BlockPos pos, Set<BlockPos> interior,
                                       float r, float g, float b, float lineAlpha, float fillAlpha) {
        Matrix4f mat = matrices.peek().getModel();
        float x0 = pos.getX() + 0.002f, y0 = pos.getY() + 0.002f, z0 = pos.getZ() + 0.002f;
        float x1 = pos.getX() + 0.998f, y1 = pos.getY() + 0.998f, z1 = pos.getZ() + 0.998f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf;

        // -Y face (interior below)
        if (interior.contains(pos.down())) {
            buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, fillAlpha);
            tess.draw();
            buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            line(buf, mat, x0, y0, z0, x1, y0, z0, r, g, b, lineAlpha);
            line(buf, mat, x1, y0, z0, x1, y0, z1, r, g, b, lineAlpha);
            line(buf, mat, x1, y0, z1, x0, y0, z1, r, g, b, lineAlpha);
            line(buf, mat, x0, y0, z1, x0, y0, z0, r, g, b, lineAlpha);
            tess.draw();
        }
        // +Y face (interior above)
        if (interior.contains(pos.up())) {
            buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            quad(buf, mat, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, fillAlpha);
            tess.draw();
            buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            line(buf, mat, x0, y1, z0, x1, y1, z0, r, g, b, lineAlpha);
            line(buf, mat, x1, y1, z0, x1, y1, z1, r, g, b, lineAlpha);
            line(buf, mat, x1, y1, z1, x0, y1, z1, r, g, b, lineAlpha);
            line(buf, mat, x0, y1, z1, x0, y1, z0, r, g, b, lineAlpha);
            tess.draw();
        }
        // -Z face (interior to north)
        if (interior.contains(pos.north())) {
            buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            quad(buf, mat, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, fillAlpha);
            tess.draw();
            buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            line(buf, mat, x0, y0, z0, x1, y0, z0, r, g, b, lineAlpha);
            line(buf, mat, x1, y0, z0, x1, y1, z0, r, g, b, lineAlpha);
            line(buf, mat, x1, y1, z0, x0, y1, z0, r, g, b, lineAlpha);
            line(buf, mat, x0, y1, z0, x0, y0, z0, r, g, b, lineAlpha);
            tess.draw();
        }
        // +Z face (interior to south)
        if (interior.contains(pos.south())) {
            buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            quad(buf, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, fillAlpha);
            tess.draw();
            buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            line(buf, mat, x0, y0, z1, x1, y0, z1, r, g, b, lineAlpha);
            line(buf, mat, x1, y0, z1, x1, y1, z1, r, g, b, lineAlpha);
            line(buf, mat, x1, y1, z1, x0, y1, z1, r, g, b, lineAlpha);
            line(buf, mat, x0, y1, z1, x0, y0, z1, r, g, b, lineAlpha);
            tess.draw();
        }
        // -X face (interior to west)
        if (interior.contains(pos.west())) {
            buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            quad(buf, mat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, fillAlpha);
            tess.draw();
            buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            line(buf, mat, x0, y0, z0, x0, y0, z1, r, g, b, lineAlpha);
            line(buf, mat, x0, y0, z1, x0, y1, z1, r, g, b, lineAlpha);
            line(buf, mat, x0, y1, z1, x0, y1, z0, r, g, b, lineAlpha);
            line(buf, mat, x0, y1, z0, x0, y0, z0, r, g, b, lineAlpha);
            tess.draw();
        }
        // +X face (interior to east)
        if (interior.contains(pos.east())) {
            buf = tess.getBuffer();
            buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            quad(buf, mat, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, fillAlpha);
            tess.draw();
            buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            line(buf, mat, x1, y0, z0, x1, y0, z1, r, g, b, lineAlpha);
            line(buf, mat, x1, y0, z1, x1, y1, z1, r, g, b, lineAlpha);
            line(buf, mat, x1, y1, z1, x1, y1, z0, r, g, b, lineAlpha);
            line(buf, mat, x1, y1, z0, x1, y0, z0, r, g, b, lineAlpha);
            tess.draw();
        }
    }

    private static void drawOutline(MatrixStack matrices, BlockPos pos, float g, float a) {
        float r = 1f, b = 0f;
        Matrix4f mat = matrices.peek().getModel();
        float x0 = pos.getX() + 0.002f, y0 = pos.getY() + 0.002f, z0 = pos.getZ() + 0.002f;
        float x1 = pos.getX() + 0.998f, y1 = pos.getY() + 0.998f, z1 = pos.getZ() + 0.998f;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);

        line(buf, mat, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(buf, mat, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(buf, mat, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(buf, mat, x0, y0, z1, x0, y0, z0, r, g, b, a);
        line(buf, mat, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(buf, mat, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(buf, mat, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(buf, mat, x0, y1, z1, x0, y1, z0, r, g, b, a);
        line(buf, mat, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(buf, mat, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(buf, mat, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(buf, mat, x0, y0, z1, x0, y1, z1, r, g, b, a);

        tess.draw();
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x0, y0, z0).color(r, g, b, a).next();
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).next();
    }



    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        buf.vertex(mat, x0, y0, z0).color(r, g, b, a).next();
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(mat, x2, y2, z2).color(r, g, b, a).next();
        buf.vertex(mat, x3, y3, z3).color(r, g, b, a).next();
    }
}
