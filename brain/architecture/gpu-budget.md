# GPU budget

The Thomson's GPU (Realtek, PowerVR Rogue) is the bottleneck, not the CPU. A frame has 16.7 ms.

## What a frame costs (hold, measured on the TV)

| Layer | GPU per frame |
|---|---|
| Dock, tiles, clock, date, pills (everything but the stage) | 5.2 ms |
| Field | 1.8 ms |
| Nebula (with the airglow baked in) | 0.9 ms |
| Voxels | 0.6 ms |
| Reflection | 0.3 ms |
| Voxel glows | 0.3 ms |
| Live stars, sprites, meteors | about 0 |

Total about 8.6 ms in a hold (14.3 ms before the airglow was baked). Any blended full-screen layer
costs about 5 ms; half the screen about 2.7 ms, however it is drawn. Android composites Lumen's
1920x1080 window up to the 4K panel with the GPU (`CLIENT` composition), which is normal on this TV.

## Measuring

- Per-frame data: `adb shell dumpsys gfxinfo com.arzaroth.lumen framestats`, polled every second.
- GPU busy per frame: `GpuCompleted - max(SwapBuffers, previous GpuCompleted)`.
  `GpuCompleted - IssueDrawCommandsStart` is latency (two frames in flight), not cost.
- Late frames: consecutive `IntendedVsync` more than 20 ms apart.
- To attribute cost, a diagnostic build reads a global setting (`adb shell settings put global ...`)
  to skip layers or pin an exit, and logs phase changes; never commit it.
- After hours of uptime the TV degrades (the main thread starts each frame ~12 ms late, 19% late
  frames for any build); a reboot clears it. Measure after a reboot.

## Rules

- No new blended full-screen layer; bake static content into an existing one.
- Blit the field on whole pixels.
- Cap counts that scale with the logo (`CAP`, `GHOST_CAP`) and spark bursts.
- Sparks that linger draw over the next entrance: keep them short.

## Sources

- `lumen/src/com/arzaroth/lumen/Cosmos.java`, `Stage.java`
- `scripts/frames.sh`
