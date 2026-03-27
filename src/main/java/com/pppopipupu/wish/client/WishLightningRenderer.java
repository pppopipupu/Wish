package com.pppopipupu.wish.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class WishLightningRenderer {

    public static void render(WishLightningEffect effect, PoseStack poseStack, Camera camera) {
        Vec3 camPos = camera.getPosition();
        float alpha = effect.getAlpha();
        if (alpha <= 0.01f) return;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();

        for (List<Vec3> branch : effect.getBranches()) {
            renderGlow(matrix, branch, alpha, 0.35f, 1.0f, 1.0f, 0.6f, 0.15f);
            renderGlow(matrix, branch, alpha, 0.18f, 1.0f, 0.95f, 0.2f, 0.4f);
            renderCore(matrix, branch, alpha, 0.06f);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    private static void renderCore(Matrix4f matrix, List<Vec3> points, float alpha, float width) {
        if (points.size() < 2) return;

        Tesselator tesselator = Tesselator.getInstance();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);

            Vec3 dir = b.subtract(a).normalize();
            Vec3 perp;
            if (Math.abs(dir.y) > 0.9) {
                perp = dir.cross(new Vec3(1, 0, 0)).normalize().scale(width);
            } else {
                perp = dir.cross(new Vec3(0, 1, 0)).normalize().scale(width);
            }

            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            addLightingVertex(matrix, alpha, a, perp, buffer);
            addLightingVertex(matrix, alpha, b, perp, buffer);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
    }

    private static void addLightingVertex(Matrix4f matrix, float alpha, Vec3 b, Vec3 perp, BufferBuilder buffer) {
        buffer.addVertex(matrix, (float)(b.x + perp.x), (float)(b.y + perp.y), (float)(b.z + perp.z))
                .setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, (float)(b.x - perp.x), (float)(b.y - perp.y), (float)(b.z - perp.z))
                .setColor(1.0f, 1.0f, 1.0f, alpha);
    }

    private static void renderGlow(Matrix4f matrix, List<Vec3> points, float alpha, float width,
                                    float r, float g, float b, float baseAlpha) {
        if (points.size() < 2) return;

        Tesselator tesselator = Tesselator.getInstance();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);

            Vec3 dir = p2.subtract(p1).normalize();
            Vec3 perp;
            if (Math.abs(dir.y) > 0.9) {
                perp = dir.cross(new Vec3(1, 0, 0)).normalize().scale(width);
            } else {
                perp = dir.cross(new Vec3(0, 1, 0)).normalize().scale(width);
            }

            float a = alpha * baseAlpha;

            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            buffer.addVertex(matrix, (float)(p1.x + perp.x), (float)(p1.y + perp.y), (float)(p1.z + perp.z))
                    .setColor(r, g, b, a);
            buffer.addVertex(matrix, (float)(p1.x - perp.x), (float)(p1.y - perp.y), (float)(p1.z - perp.z))
                    .setColor(r, g, b, a);
            buffer.addVertex(matrix, (float)(p2.x + perp.x), (float)(p2.y + perp.y), (float)(p2.z + perp.z))
                    .setColor(r, g, b, a);
            buffer.addVertex(matrix, (float)(p2.x - perp.x), (float)(p2.y - perp.y), (float)(p2.z - perp.z))
                    .setColor(r, g, b, a);

            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
    }
}
