package com.pppopipupu.wish.client;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WishLightningEffect {
    private final Vec3 origin;
    private final List<List<Vec3>> branches;
    private int remainingTicks;
    private final int maxTicks;
    private static final Random RANDOM = new Random();

    public WishLightningEffect(Vec3 origin, int durationTicks) {
        this.origin = origin;
        this.maxTicks = durationTicks;
        this.remainingTicks = durationTicks;
        this.branches = generateBranches(origin);
    }

    private List<List<Vec3>> generateBranches(Vec3 base) {
        List<List<Vec3>> result = new ArrayList<>();
        int branchCount = 3 + RANDOM.nextInt(4);
        for (int b = 0; b < branchCount; b++) {
            List<Vec3> branch = new ArrayList<>();
            double x = base.x + (RANDOM.nextDouble() - 0.5) * 3.0;
            double z = base.z + (RANDOM.nextDouble() - 0.5) * 3.0;
            double topY = base.y + 6.0 + RANDOM.nextDouble() * 4.0;
            branch.add(new Vec3(x, topY, z));

            int segments = 8 + RANDOM.nextInt(6);
            double segHeight = (topY - base.y) / segments;
            double currentY = topY;
            double currentX = x;
            double currentZ = z;

            for (int i = 1; i <= segments; i++) {
                currentY -= segHeight;
                double offsetX = (RANDOM.nextDouble() - 0.5) * 0.8;
                double offsetZ = (RANDOM.nextDouble() - 0.5) * 0.8;
                currentX += offsetX;
                currentZ += offsetZ;

                if (i == segments) {
                    currentY = base.y;
                }
                branch.add(new Vec3(currentX, currentY, currentZ));

                if (RANDOM.nextFloat() < 0.3 && i > 2 && i < segments - 1) {
                    List<Vec3> subBranch = generateSubBranch(new Vec3(currentX, currentY, currentZ));
                    result.add(subBranch);
                }
            }
            result.add(branch);
        }
        return result;
    }

    private List<Vec3> generateSubBranch(Vec3 start) {
        List<Vec3> sub = new ArrayList<>();
        sub.add(start);
        int subSegments = 2 + RANDOM.nextInt(3);
        double cx = start.x;
        double cy = start.y;
        double cz = start.z;
        for (int i = 0; i < subSegments; i++) {
            cx += (RANDOM.nextDouble() - 0.5) * 1.2;
            cy -= RANDOM.nextDouble() * 0.8;
            cz += (RANDOM.nextDouble() - 0.5) * 1.2;
            sub.add(new Vec3(cx, cy, cz));
        }
        return sub;
    }

    public void tick() {
        remainingTicks--;
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    public float getAlpha() {
        float life = (float) remainingTicks / maxTicks;
        float flicker = 0.7f + 0.3f * (float) Math.sin(remainingTicks * 1.5);
        return life * flicker;
    }

    public List<List<Vec3>> getBranches() {
        return branches;
    }

    public Vec3 getOrigin() {
        return origin;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void regenerateBranches() {
        branches.clear();
        branches.addAll(generateBranches(origin));
    }
}
