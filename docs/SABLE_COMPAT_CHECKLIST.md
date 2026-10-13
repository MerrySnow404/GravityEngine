# Sable compatibility review — 2026-10-13

Target: Minecraft 1.21.1, NeoForge 21.1.256, Sable **2.0.6**, Java 21.
This is an assessment of the existing adapter, not a claim of complete Sable
feature parity. Other loader targets do not contain this integration.

## Current review continuation (2026-10-13)

Baseline: `2edc4269eb7c854e43babc2eb3ed8b94b895d864`, plus the working-tree
corrections described in `REFACTOR_STATUS.md`. The earlier checkpoint below is
historical; its list of unimplemented adapters no longer describes this checkout.
The supported target remains NeoForge 21.1.256, Sable 2.0.6, Create 6.0.10 and
Aeronautics Bundled 1.3.2. Software identity is restored to **0.0.2**; nothing
has been published or substituted for an existing release.

| Review area | Current implementation and acceptance boundary |
| --- | --- |
| SB-01 mass evidence | Native edits withhold intermediate derived evidence; distributed mass/COM agree before force commit. Actual Create.setBlock now reuses Sable property construction and pipeline replacement. Transformed replacement, unexpected-failure rollback, last-block removal and revival pass. Native Sable owns all mass and collider state. |
| SB-02 gravity/lift | Mass, floating and balloon GE corrections stage atomically for one native substep; late incomplete evidence rejects all GE corrections while preserving native forces. Native uniform/zero/oblique/inverted gravity, native friction and analytic gradient cluster force/torque pass. Floating uses native material-cluster centroid quadrature, with an explicitly documented generalized self-lift cap; arbitrary nonlinear within-cluster integration is not claimed. Rapier water buoyancy stays native. |
| SB-03 motion evidence | Composed and sampled trajectories retain actual native tick/interval and reject stale relabeling. Native substep, paused and near-full-revolution controls pass; final parent/child client motion, edit, untrack/retrack and removal cases pass. Server substeps and native client interpolation remain distinct motion domains; hidden within-tick reversal parity is not claimed. |
| SB-04 ownership | GE owns character collision/carry; native physics owns body motion and native mounted transforms. Powered bearing, piston and rail cart assembly/controller/disassembly pass. Real client walking, jumping and incoming-body push pass. Local travel refreshes locomotion mode before capture; support momentum survives numerical cleanup until its movement owner releases it. Fixed and real-world regressions cover touching-band accumulation, affine projection and rotating voxel seams. |
| SB-05 environment | Captured convex wet volumes subtract native occlusion masks; clipped cell weights prevent duplicated flow. Native Player partial immersion/dry eye, swimming, air/fire, full exit, rotated water, ladder/vine/scaffold and dirty-mask rejection pass. No scalar center/foot veto erases remaining wet body volume. Unsupported or unavailable capture remains an explicit failure. |
| SB-06 dismount | Native candidate selection and passenger removal remain native. Rejected final placement retains the pre-selection anchor/pose. Both immediate and client-deferred Create placement are validated in world coordinates; blocked and clear deferred candidates pass, together with mounted self/observer transforms and native Shift removal. Rejection can leave the actor at an already-overlapping seat anchor: no automatic escape or remount is promised. |
| SB-07 cost | Conservative bounds discovery, geometry-lifetime address order and precharged expansion remain bounded. Matched full-scan/indexed capture and native powered-bearing/Rapier/FIELD/environment workloads now have raw timing, allocation and work counts at 2/8 substeps. Identical counted work is retained. Dense/empty/sparse costs and the 2-substep p95 regression are reported; no whole-client or universal speedup is claimed. See SABLE_DISCOVERY_MEASUREMENTS.md. |
| SB-08 clients | Fresh dedicated-server/two-real-client runs pass parent, Create child and general lifecycle cases at both 0 and 100 ms ordered TCP+UDP delay. Dynamic cases include translation, reversal, stop, rotation, standing, 80 input ticks of walking, jump, block edit, source replacement/removal and incoming native push; child cases additionally exercise seats and immediate/deferred dismount. General cases cover concurrent assignment, tracking, dimension change, death/respawn and reconnect. Camera observation cannot mutate physical state. Loss/reordering remains unverified. |
| SB-09 evidence | Fresh final common/all-target builds, ordinary transformed server, all three strict server profiles and all three integrated graphical clients pass. Six complete multiplayer runs share the final production source and artifact. Per-run manifests retain commands, source/dirty diff, source additions, native/embedded mod hashes and result markers. Failed-before cases and case-specific performance regressions remain available; build/review-completion-20261013/manifest.json is the final index. |

**CP-S1 and CP-S2 are complete within the declared target and tested scope.**
This is bounded compatibility acceptance, not complete Sable feature parity.
Final artifact SHA256: `736D5EAB5AF2892AC2DED3C41DEED334714419EBD2EB40BA1B7B2DD720A98B50`.
See the current completion entry in [REFACTOR_STATUS.md](REFACTOR_STATUS.md) and
[measurements](SABLE_DISCOVERY_MEASUREMENTS.md). Other targets have build/API
acceptance only. Nonlinear floating-cluster quadrature, native water buoyancy,
client interpolation, rejected-seat retention and transport limits above remain
explicit. The chronological entries below retain their original historical
scope and do not override this completion record.

## Historical composed-motion implementation checkpoint

The common collision kernel now accepts an internal immutable `RigidTrajectory`
for both single-body motion and captured parent/child composition. Collision
poses, swept enclosures, contact velocity and support transport use that same
trajectory. Composite velocity includes both components once; chord subdivision
uses a conservative acceleration bound including the Coriolis term. The
single-axis clearance shortcut is restricted to single-body publications.

This is partial implementation of the requested NeoForge 21.1.256 / Sable 2.0.6 /
Create 6.0.10 / Aeronautics Bundled 1.3.2 work, **not complete compatibility**.
The Sable adapter still publishes single-body endpoint motion and rejects
collidable contraptions. Plot rebasing, sampled substep motion, lifecycle coverage,
swimming/climbing/scaffolding, vehicle interactions, sparse attached-mass capture,
lift operands and the three runtime profiles still need implementation. Existing
fixed-reach and unsupported-geometry failures have not been removed. Native
character/vehicle coupling still needs its requested audit.

Client/multiplayer acceptance and comparative performance measurements have not
been run for this checkpoint. Full-stack acceptance remains blocked by unfinished
integration and missing required runtime evidence. See the new checkpoint in
[REFACTOR_STATUS.md](REFACTOR_STATUS.md) for fresh commands and results; historical
PASS results below do not certify the requested full stack.

## Verdict

**PASS within the exercised server scope; compatibility is incomplete.**
The updated artifact compiles, its Mixin seams apply, the absent-mod server
works, and the strict installed-mod server passes all eleven required areas.
No production algorithm change was needed for this Sable patch update.
New regression coverage verifies distributed torque and all-or-nothing rejection
when the second mass point has incomplete FIELD evidence.

Client presentation/network ordering, fluid-surface locomotion and several
unsupported geometry domains remain outside this acceptance. Passing a server
fixture is not proof of multiplayer or packaged-client behavior.

## Version and source evidence

- Official [Sable release](https://github.com/ryanhcode/sable/releases/tag/mc1.21.1-2.0.6-neoforge),
  commit `ad8b4d3`, and
  [tag comparison](https://github.com/ryanhcode/sable/compare/mc1.21.1-2.0.5-neoforge...mc1.21.1-2.0.6-neoforge).
  The NeoForge-relevant behavior change corrects the fluid argument passed to
  `LivingEntity.canStandOnFluid`; the other code changes concern Fabric networking.
- The official [Modrinth release](https://modrinth.com/mod/sable/version/fg9dTRz9)
  and the actual resolved Maven runtime have identical SHA-512:
  `76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8`.
- Embedded dependencies remain companion **1.6.0** and Veil **4.3.2**;
  Rapier is **2.0.6**. Keep the companion compile dependency at the embedded ABI:
  Sable explicitly rejects companion versions newer than 1.6.0.
- Reviewed tagged `SubLevel`, `ServerSubLevel`, `SubLevelPhysicsSystem`,
  `LevelPlot` and `SubLevelEntityCollision` sources. These match the local
  reference source. Actual release bytecode was also inspected with Java 21
  `javap -p -c -s`; output is `build/sable-2.0.6/seams-javap.txt`.
- All NeoForge attachment package sources are unchanged between 21.1.249 and
  21.1.256. Fresh transformed lifecycle controls validate the upgraded target;
  no attachment/persistence ownership migration was introduced.

## Correctness and ownership

| Area | Assessment and evidence |
| --- | --- |
| Optional loading | Sable is compile-only in ordinary runs. The compatibility plugin guards Sable Mixins, the facade delays adapter loading, and exact optional metadata pins 2.0.6. The ordinary server and independent API artifact loading pass without Sable. |
| Mixin ABI | Required injection points still apply. `collide` retains two `getFeetPos` calls and two raw position writes; only the first raw write is adapted. Bounding-box ordinals/slices and the local intersecting set survive transformed launch. `initialize` still calls `PhysicsPipeline.init(Vector3dc,double)`. `applyQueuedForces(SubLevelPhysicsSystem,RigidBodyHandle,double)` runs before each physics substep. |
| Collision authority | On the frozen GE exact-body route, `parentOnly` bypasses Sable's character solve and clears Sable tracking/inherited-motion carriers. Sable retains its native route otherwise. Strict controls verify single solving, packet occupancy, native-mode handoffs and passive entities. Sable owns rigid pose and integration throughout. |
| Geometry and coordinates | Plot voxels become immutable oriented obstacles, captured with the operation's pose/motion. Subject-specific collision shapes and plot-space `isAbove` are used. Tests cover translated/rotated geometry, changed local shape and material, context-sensitive blocks, and frozen packet captures across removal. |
| Support transport | Contact identity, local geometry and lifecycle epochs validate carry. Tests exercise elevators, translation, rotation, acceleration, reversal, stop, walkoff and real Rapier-driven movement. Stone/slime/bed landing checks exercise normal surface velocity and avoid duplicate bounce/release impulses. |
| Lifecycle | Bounds updates publish; block changes retire affected primitive identities; removal and Level unload invalidate sources. Registration/re-registration and unload are tested. Arbitrary client chunk-arrival/replacement order is not established by those fixtures. |
| Rigid gravity | Sable owns mass, COM, inertia, pose and velocities. GE samples mass points and submits one velocity increment before each solver substep; it stores no competing rigid angular state. Tick acceleration converts by 400, velocity by 20, and the captured native baseline is subtracted only where a complete active field applies. Absent or disabled fields leave native gravity intact. |
| Distributed torque | New rotated two-stone fixture checks the analytic angular impulse against `I_world * deltaOmega`. A spatially odd field distinguishes distributed torque from a COM-only approximation. A second provider reports incomplete coverage at the later mass point; both linear and angular velocities must remain unchanged. The strict gate requires the new `distributed-gravity` marker. |
| Failure behavior | Unsupported motion/geometry and collision budgets raise coverage failures rather than successful empty scenes. Incomplete gravity sampling or mass disagreement makes no partial velocity commit. Unexpected provider errors retain the engine's propagation behavior. |

The index and primitive IDs are transient per-Level collision evidence; they are
not durable field assignment or Sable pose authority. Gravity baseline capture is
per physics-system lifetime. FIELD coverage is evaluated for each query; registry
emptiness is never promoted into completeness. No new persistence, readiness,
transport protocol or physical state owner was added in this update.

## Limits and remaining acceptance work

| Priority | Gap | Current behavior / required evidence |
| --- | --- | --- |
| High | Real clients and multiplayer | **NOT RUN.** Verify riding/carry during delayed or reordered SubLevel pose/entity correction packets, login/respawn/dimension changes, camera/body rendering, remote players and chunk streaming. Dedicated-server delayed-endpoint fixtures do not reproduce client networking. |
| High | Fluids on moving plots | **NOT VERIFIED.** The 2.0.6 upstream fluid-standing fix does not establish gravity-aware fluid surfaces in GE's voxel-collision route. Test Striders, water/lava surfaces and swimming on rotated/moving SubLevels before claiming support. |
| High | Kinematic/Create contraptions | **UNSUPPORTED by GE rigid collision.** A relevant valid collidable contraption rejects capture; it needs its own motion publication. The gravity bridge uses COM-only fallback when contraptions are present, so distributed/tidal behavior is not implemented there. Create/Aeronautics were not installed in these gates. |
| Medium | Scale/pivot/deformation | Fixed positive scale can be represented by collision boxes. Changing scale/pivot, nonpositive scale and nonrigid geometry are rejected. Gravity uses COM-only fallback for non-unit scale. Dedicated scaled-body and contraption fallback force tests remain absent. |
| Medium | Scaffolding and special locomotion | Nearby scaffolding rejects GE capture. Shape context alone cannot implement climbing/descending policy. Native fallback remains capability-dependent; the existing mode tests are not exhaustive entity/mod compatibility. |
| Medium | Motion bounds | Publications cover one tick and refuse material-point displacement above 16 blocks. Endpoint shortest-arc rotation cannot certify arbitrary intra-tick rotation or multiple revolutions. High angular-speed/teleport cases require separate evidence and potentially a richer motion contract. |
| Medium | Plot chunk lifecycle | Source review confirms chunk-holder addition updates plot bounds and full removal clears loaded holders; bounds publication and source removal are separate hooks. Test same-bounds chunk replacement and actual client load/unload ordering before claiming complete streaming coverage. |
| Medium | Gravity cost and budget | Distributed capture scans up to 65,536 bounding-box positions per body per physics substep and samples each positive mass point. Budget/mass mismatch leaves Sable baseline gravity for that step. Large/sparse fleets and configurable substep counts need profiling; no performance acceptance is claimed. |
| Medium | Two-way physical response | Character collision/carry does not implement general character-to-rigid reaction impulses. One-way kinematic contact is not a complete coupled rigid/character solver. |

## Reproduction and evidence

Use the primary target's wrapper with Java 21 and accepted EULAs in both
verification directories:

```powershell
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification --console plain --no-daemon
```

The gates delete old result files before launching. Required fresh outputs are:

- `targets/neoforge-1.21.1/run/control-verification/control-boundary-result.txt`
- `targets/neoforge-1.21.1/run/sable-verification/control-boundary-result.txt`
- `targets/neoforge-1.21.1/run/sable-verification/sable-checks-result.txt`

The installed manifest must say `PASS Sable 2.0.6` and contain contacts, context,
continuity, delayed-endpoints, distributed-gravity, gravity, landing-commit,
modes, passive-entities, physics and transport. Final commands, logs and statuses
are recorded in [REFACTOR_STATUS.md](REFACTOR_STATUS.md).
