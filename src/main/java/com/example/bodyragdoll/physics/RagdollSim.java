package com.example.bodyragdoll.physics;

import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Лёгкий verlet-рэгдолл из 9 точек. Полностью детерминирован: одинаковые входные данные
 * дают одинаковый результат на всех клиентах, поэтому тело у всех игроков лежит одинаково.
 */
public final class RagdollSim {
    public static final int HEAD = 0, NECK = 1, HIP = 2, SL = 3, SR = 4, HL = 5, HR = 6, FL = 7, FR = 8, N = 9;

    private static final double R = 0.15;       // радиус частицы
    private static final double GRAV = 0.08;
    private static final double DAMP = 0.985;
    private static final double FRICTION = 0.7;
    private static final int ITER = 8;

    public final double[] p = new double[N * 3]; // текущие позиции
    public final double[] q = new double[N * 3]; // предыдущие (для verlet)
    public final double[] r = new double[N * 3]; // позиции на прошлом тике (для интерполяции)
    private final double[] cn = new double[N * 4];

    private final int[] la, lb;
    private final double[] ll;
    private final int[] ma, mb;
    private final double[] ml;

    public boolean asleep;
    private int calm;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    public RagdollSim(double x, double y, double z, float yawDeg, double s) {
        double yaw = Math.toRadians(yawDeg);
        double lx = Math.cos(yaw), lz = Math.sin(yaw); // вектор "влево" у сущности
        double y0 = y + R;

        put(NECK, x, y0 + 1.5 * s, z);
        put(HIP, x, y0 + 0.75 * s, z);
        put(SL, x + lx * 0.3125 * s, y0 + 1.375 * s, z + lz * 0.3125 * s);
        put(SR, x - lx * 0.3125 * s, y0 + 1.375 * s, z - lz * 0.3125 * s);
        put(HEAD, x, y0 + 2.0 * s, z);
        put(HL, x + lx * 0.3125 * s, y0 + 0.75 * s, z + lz * 0.3125 * s);
        put(HR, x - lx * 0.3125 * s, y0 + 0.75 * s, z - lz * 0.3125 * s);
        put(FL, x + lx * 0.125 * s, y0, z + lz * 0.125 * s);
        put(FR, x - lx * 0.125 * s, y0, z - lz * 0.125 * s);
        System.arraycopy(p, 0, q, 0, p.length);
        System.arraycopy(p, 0, r, 0, p.length);

        int[][] rigid = {
                {HIP, NECK}, {HIP, SL}, {HIP, SR}, {NECK, SL}, {NECK, SR}, {SL, SR},
                {HEAD, NECK}, {HL, SL}, {HR, SR}, {FL, HIP}, {FR, HIP}};
        la = new int[rigid.length];
        lb = new int[rigid.length];
        ll = new double[rigid.length];
        for (int i = 0; i < rigid.length; i++) {
            la[i] = rigid[i][0];
            lb[i] = rigid[i][1];
            ll[i] = dist(la[i], lb[i]);
        }

        double[][] mins = {
                {HEAD, HIP, 0.70}, {HEAD, SL, 0.45}, {HEAD, SR, 0.45},
                {HL, HIP, 0.28}, {HR, HIP, 0.28}, {HL, NECK, 0.35}, {HR, NECK, 0.35},
                {FL, NECK, 0.85}, {FR, NECK, 0.85}, {FL, FR, 0.22}, {HL, HR, 0.30}};
        ma = new int[mins.length];
        mb = new int[mins.length];
        ml = new double[mins.length];
        for (int i = 0; i < mins.length; i++) {
            ma[i] = (int) mins[i][0];
            mb[i] = (int) mins[i][1];
            ml[i] = mins[i][2] * s;
        }
    }

    private void put(int i, double x, double y, double z) {
        p[i * 3] = x;
        p[i * 3 + 1] = y;
        p[i * 3 + 2] = z;
    }

    private double dist(int a, int b) {
        double dx = p[a * 3] - p[b * 3], dy = p[a * 3 + 1] - p[b * 3 + 1], dz = p[a * 3 + 2] - p[b * 3 + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Начальный импульс (блоков/тик). hitHeight 0..1 — высота попадания, туда прикладывается основная сила. */
    public void launch(double ix, double iy, double iz, float hitHeight, long seed) {
        Random rnd = new Random(seed);
        int hitP = hitHeight > 0.8f ? HEAD : (hitHeight > 0.45f ? NECK : HIP);
        for (int i = 0; i < N; i++) {
            double f = (i == hitP) ? 1.0 : 0.4;
            if (hitP == NECK && (i == SL || i == SR)) f = 1.0;
            for (int k = 0; k < 3; k++) {
                double v = (k == 0 ? ix : k == 1 ? iy : iz) * f + rnd.nextGaussian() * 0.02;
                q[i * 3 + k] = p[i * 3 + k] - v;
            }
        }
    }

    public void snapshot() {
        System.arraycopy(p, 0, r, 0, p.length);
    }

    public double pos(int i, int k, float pt) {
        int b = i * 3 + k;
        return r[b] + (p[b] - r[b]) * pt;
    }

    public void step(Level level) {
        if (asleep) return;
        System.arraycopy(p, 0, r, 0, p.length);

        for (int i = 0; i < N; i++) {
            int b = i * 3;
            for (int k = 0; k < 3; k++) {
                double v = (p[b + k] - q[b + k]) * DAMP;
                q[b + k] = p[b + k];
                p[b + k] += v;
            }
            p[b + 1] -= GRAV;
        }
        java.util.Arrays.fill(cn, 0.0);

        for (int it = 0; it < ITER; it++) {
            for (int i = 0; i < la.length; i++) solve(la[i], lb[i], ll[i], false);
            for (int i = 0; i < ma.length; i++) solve(ma[i], mb[i], ml[i], true);
            for (int i = 0; i < N; i++) collide(level, i);
        }

        // реакция на контакт: гасим скорость вдоль нормали и трение
        for (int i = 0; i < N; i++) {
            int c = i * 4;
            double cnt = cn[c + 3];
            if (cnt <= 0) continue;
            double nx = cn[c], ny = cn[c + 1], nz = cn[c + 2];
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9) continue;
            nx /= len; ny /= len; nz /= len;
            int b = i * 3;
            double vx = p[b] - q[b], vy = p[b + 1] - q[b + 1], vz = p[b + 2] - q[b + 2];
            double vn = vx * nx + vy * ny + vz * nz;
            if (vn < 0) { vx -= nx * vn; vy -= ny * vn; vz -= nz * vn; }
            double f = ny > 0.5 ? FRICTION : 0.9;
            vx *= f; vy *= f; vz *= f;
            q[b] = p[b] - vx; q[b + 1] = p[b + 1] - vy; q[b + 2] = p[b + 2] - vz;
        }

        double max = 0;
        for (int i = 0; i < N; i++) {
            int b = i * 3;
            double dx = p[b] - q[b], dy = p[b + 1] - q[b + 1], dz = p[b + 2] - q[b + 2];
            max = Math.max(max, dx * dx + dy * dy + dz * dz);
        }
        if (max < 1.0E-5) calm++; else calm = 0;
        if (calm >= 15) {
            asleep = true;
            snapshot();
        }
    }

    private void solve(int a, int b, double rest, boolean minOnly) {
        int ia = a * 3, ib = b * 3;
        double dx = p[ib] - p[ia], dy = p[ib + 1] - p[ia + 1], dz = p[ib + 2] - p[ia + 2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-9) return;
        if (minOnly && d >= rest) return;
        double k = (d - rest) / d * 0.5;
        p[ia] += dx * k; p[ia + 1] += dy * k; p[ia + 2] += dz * k;
        p[ib] -= dx * k; p[ib + 1] -= dy * k; p[ib + 2] -= dz * k;
    }

    private void collide(Level level, int i) {
        int b = i * 3;
        double x = p[b], y = p[b + 1], z = p[b + 2];
        int x0 = Mth.floor(x - R), x1 = Mth.floor(x + R);
        int y0 = Mth.floor(y - R), y1 = Mth.floor(y + R);
        int z0 = Mth.floor(z - R), z1 = Mth.floor(z + R);
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    mp.set(bx, by, bz);
                    if (!level.hasChunkAt(mp)) {
                        resolve(i, bx, by, bz, bx + 1, by + 1, bz + 1);
                        continue;
                    }
                    BlockState st = level.getBlockState(mp);
                    if (st.isAir()) continue;
                    VoxelShape shape = st.getCollisionShape(level, mp);
                    if (shape.isEmpty()) continue;
                    List<AABB> boxes = shape.toAabbs();
                    for (AABB a : boxes) {
                        resolve(i, bx + a.minX, by + a.minY, bz + a.minZ, bx + a.maxX, by + a.maxY, bz + a.maxZ);
                    }
                }
            }
        }
    }

    /** Сфера против AABB. */
    private void resolve(int i, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int b = i * 3;
        double x = p[b], y = p[b + 1], z = p[b + 2];
        double cx = Mth.clamp(x, minX, maxX), cy = Mth.clamp(y, minY, maxY), cz = Mth.clamp(z, minZ, maxZ);
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 >= R * R) return;
        double nx, ny, nz, pen;
        if (d2 > 1.0E-12) {
            double d = Math.sqrt(d2);
            nx = dx / d; ny = dy / d; nz = dz / d;
            pen = R - d;
        } else {
            double[] ds = {x - minX, maxX - x, y - minY, maxY - y, z - minZ, maxZ - z};
            int best = 0;
            for (int k = 1; k < 6; k++) if (ds[k] < ds[best]) best = k;
            nx = ny = nz = 0;
            switch (best) {
                case 0 -> nx = -1;
                case 1 -> nx = 1;
                case 2 -> ny = -1;
                case 3 -> ny = 1;
                case 4 -> nz = -1;
                default -> nz = 1;
            }
            pen = R + ds[best];
        }
        p[b] += nx * pen; p[b + 1] += ny * pen; p[b + 2] += nz * pen;
        int c = i * 4;
        cn[c] += nx; cn[c + 1] += ny; cn[c + 2] += nz; cn[c + 3] += 1;
    }
}
