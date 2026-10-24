package com.arzaroth.lumen;

/** Value noise, its fractal sum and a smoothstep: what every baked sky and landscape is grown from. */
final class Noise {
    private Noise() {
    }

    static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 362437;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / 16777215f;
    }

    static float noise(float x, float y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        float ux = fx * fx * (3 - 2 * fx), uy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy, seed), b = hash(ix + 1, iy, seed), c = hash(ix, iy + 1, seed), d = hash(ix + 1, iy + 1, seed);
        return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy;
    }

    static float fbm(float x, float y, int seed, int octaves) {
        float v = 0, amp = 0.5f, norm = 0;
        for (int o = 0; o < octaves; o++) {
            v += amp * noise(x, y, seed + o * 31);
            norm += amp;
            x = x * 2.03f + 17f;
            y = y * 2.03f + 9f;
            amp *= 0.5f;
        }
        return v / norm;
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
