# Standardization checkpoints

## Current completion — Sable review CP-S1 / CP-S2, 2026-10-13

**PASS within the declared primary-target scope.** The implementation and
required evidence for `GravityEngine_Sable_WIP_Review_and_Continuation.md` are
complete. Native mass/pose/physics and passenger lifecycle remain authoritative;
GE owns its captured character collision, body/application and support evidence.
Identity remains `cc.sighs.gravityengine` / `gravityengine`, version **0.0.2**.
The working tree is uncommitted; nothing was pushed, tagged or published.

Final primary artifact SHA256: `736D5EAB5AF2892AC2DED3C41DEED334714419EBD2EB40BA1B7B2DD720A98B50`.
The artifact, final document copies, source additions and verified evidence index
are in `build/review-completion-20261013/`. Its manifest rejects mismatched
production source/artifact hashes across final server/client/multiplayer runs.

| Final gate | Result / evidence |
| --- | --- |
| Common and all configured target builds, JVM/API artifact controls | PASS — `build/sb-sealed-all-targets.log` |
| Ordinary transformed server, without optional mods | PASS — `build/sb-sealed-ordinary.log` |
| sable: strict server and integrated graphical client | PASS — `build/compatibility-evidence/sable/20261013-141415/` |
| create: strict server and integrated graphical client | PASS — `build/compatibility-evidence/create/20261013-141635/` |
| aeronautics: strict server and integrated graphical client | PASS — `build/compatibility-evidence/aeronautics/20261013-141843/` |
| parent: dedicated server plus two real clients, 0 ms TCP+UDP | PASS — `build/multiplayer/aeronautics/20261013-140330/` |
| parent: dedicated server plus two real clients, 100 ms TCP+UDP | PASS — `build/multiplayer/aeronautics/20261013-140432/` |
| child: dedicated server plus two real clients, 0 ms TCP+UDP | PASS — `build/multiplayer/aeronautics/20261013-140534/` |
| child: dedicated server plus two real clients, 100 ms TCP+UDP | PASS — `build/multiplayer/aeronautics/20261013-140640/` |
| lifecycle: dedicated server plus two real clients, 0 ms TCP+UDP | PASS — `build/multiplayer/aeronautics/20261013-140750/` |
| lifecycle: dedicated server plus two real clients, 100 ms TCP+UDP | PASS — `build/multiplayer/aeronautics/20261013-140848/` |

The dynamic runs include actual native translation/reversal/stop/rotation,
continuous walking and jumping after block edits, support retirement and fresh
native object/chunk re-tracking, and an incoming wall pushing both self and
observer. Child runs add native seats, Shift removal and client-deferred Create
placement checks. The lifecycle pair includes concurrent assignment, dimension
change, death/respawn and disconnect/reconnect. Final source additions include
fixed regressions for native deferred placement, credited deadzone behavior,
sub-threshold contact entry/projection and sixteen native-frame walk directions.

**受限支持 / NOT RUN beyond scope:** runtime acceptance is limited to Minecraft
1.21.1, NeoForge 21.1.256, Sable 2.0.6, Create 6.0.10 and Aeronautics Bundled 1.3.2.
Other target builds do not establish gameplay parity. Delay tests preserve FIFO;
packet loss/reordering is not verified. Server substeps and native client
interpolation are separate domains, with no invented intermediate sample
protocol. Illegal/unknown seat placement is rejected while native removal
completes; an already-overlapping original anchor is retained, with no automatic
escape/remount guarantee. Native rigid bodies do not receive an invented
character reaction impulse. Floating uses native cluster-centroid quadrature
for nonuniform fields; arbitrary nonlinear within-cluster integration and GE
replacement of native water buoyancy are not claimed.

Performance evidence is scenario-specific. The retained native 2/8-substep
comparison includes the negative 2-substep p95 result. Rigid-sweep measurements
show the correlated-tilt benefit and ordinary yaw-query overhead separately.
No whole-client or universal speedup is claimed; see
[SABLE_DISCOVERY_MEASUREMENTS.md](SABLE_DISCOVERY_MEASUREMENTS.md).
Historical failed-before runs and intermediate diagnostics remain below and in
the final index. They are not reused as final PASS evidence.

## Historical checkpoint chronology


## CP1 — implementation checkpoint

Input: `GravityEngine_Standardization_Two_Checkpoints.md`. Baseline:
`186f70fa765e1565c428768a2a5d5af178b0d8fc`. CP1 implementation committed as
`288bb99` on 2026-10-09. Scope: common Java 17 and NeoForge 1.21.1,
NeoForge 21.1.249; optional compatibility tested with Sable 2.0.5.

The pre-existing edits in `targets/neoforge-1.21.1/build.gradle` and
`src/main/resources/META-INF/neoforge.mods.toml` were preserved. Other targets,
root build orchestration, release configuration, save formats and network
protocols were not migrated. Nothing was pushed, tagged or published.

### Implementation closure

| Item | Implementation and direct evidence |
| --- | --- |
| CP1-01 | `GravityFieldService.compose` now adapts to `contributions` and `composeContributions`; the second summation algorithm is removed. All active evaluators validate before OVERRIDE selection. Duplicate identity, overflow, invalid intervals, zero presence and excluded evaluator failures have common regression coverage. Registry evaluation explicitly covers only its local publications. |
| CP1-02 | One spatial index per publication owner replaces the shared candidate index. Provider sampling selects its partition and performs contains once, then samples already-selected sources. Stable provider/evaluator ordering and global sum ordering remain. Identity validation reuses a compiled pattern; sorting no longer allocates parsed IDs per comparison. Measurements compare actual baseline/current production classes. |
| CP1-03 | `GravityFieldProvider.blockDiscovery` supports bounded non-publication sources, explicit unbounded traversal and sampling-only defaults. Publication bounds are captured once during successful preparation. Native chunk events feed an owned round-robin key queue; source updates cannot reset it. Section/cell and removal-cleanup cursors retain progress; unload removes both queued and active work. Real-server checks discover 40 chunks during every-tick replacement and clean each affected position once on removal. Existing native scheduling, freeze, readiness and duplicate-tick checks pass. |
| CP1-04 | Capture records unavailable chunk domains, including shape/neighbor dependencies. Movement, support, pose and material/cell queries reject only relevant gaps. No solve-time world read is added. Native-proven space outside build height is not marked unavailable. Common gap tests and a transformed loaded-interior/missing-neighbor/void test verify this. Existing geometry and movement failure boundaries preserve installed ownership. |
| CP1-05 | Qualified rigid identities have direct lookup. Cell lookup builds a private immutable-value map on first use and preserves multiple primitives. Repeated block broadphase scans activate a linear-build, packed hierarchy after four scans for scenes of at least 64 primitives; short-lived scenes keep linear queries. Large primitives occur once. A fixed-seed oracle compares indexed and linear results, including negative coordinates and large bounds. Construction, total query work, allocations and lookup costs are measured separately. |
| CP1-06 | Internal rigid providers can prove `mayAffect(query)==false` for a complete swept domain/time. Unknown providers default to true. Capture skips proven-empty providers; outer movement can bypass scene creation only when committed state allows it and no support/sublevel continuity could widen the trajectory. Common tests cover empty, distant, swept-intersecting and unknown sources. Travel/pose paths and Sable remain conservative without a suitable proof. |
| CP1-07 | Removed duplicate composition, the shared publication candidate path, the discovery revision mirror, retained field-list mirror and live ChunkHolder iterator. Kept independent assignment/application/geometry, FIELD coverage/presence, publication token/revision and durable/sync revisions. Existing attachment, replacement and synchronization controls pass unchanged. Architecture tests retain real dependency and platform-seam checks; no arbitrary layout rewrite was needed. |
| CP1-08 | Field callback failures identify provider, Level and phase and retain causes/cleanup failures. Publication failures retain argument exception types with contextual diagnostics. Geometry application now handles coverage/budget refusal explicitly and propagates unexpected failures after rollback instead of swallowing them. A real pre-connection geometry failure test verifies committed state, anchor, proxy, velocity and installed axis. Existing work budgets and fail-closed collectors remain; discovery and scene candidate counters are aggregate/on-demand. |

### Ownership and complexity

| Fact | Producer / unique owner | Consumers, lifetime and invalidation |
| --- | --- | --- |
| Publication identity, owner, revision, release token | Provider input / `GravityFieldRegistry` | Provider sampling and discovery; replaced/released/unloaded atomically. Bounds and owner-partition indexes are derived captured data, not additional source authorities. |
| Contributions and coverage | Each provider / one query in `GravityFieldRuntime` | Global composition and existing reconciliation; never persisted or inferred from discovery. After reload, only an all-complete query makes absence authoritative. |
| Loaded discovery candidates and scan progress | Native chunk events / `FallingBlockRechecks` | Budgeted periodic work and one-time removal cleanup; chunk unload or Level disposal invalidates them. The queue is not a completeness oracle. |
| Block coverage, exact primitives, lookup indexes | Target capture / one `CapturedCollisionScene` | Movement, geometry, support and materials within that operation. Derived lazy indexes have only the scene lifetime; they cannot read or replace world evidence. |
| Durable assignment / live FIELD evidence / installed application | Existing persistence slot / entity state / application and geometry owners | Retained unchanged; no new readiness flags, generations, incarnation protocol or persistence copier. |

The packed index replaces an initially attempted recursively sorted hierarchy:
measurement showed that version spent more time building than it saved. Maps
and the hierarchy now initialize only when used. Small-scene bookkeeping and
index memory costs are reported rather than presented as free improvements.

### Contract corrections and validation

Maintained API declarations, package Javadocs, the supported consumer fixture and
`API_BOUNDARY.md` describe the minimal discovery hook and callback exception
migration. No CP2 API stability claim is made.

Two former assertions were corrected to match the implementation contract:
provider opening failure is now the contextual exception's original cause, and
native chunk reload queues discovery rather than immediately bypassing its work
budget. Cleanup-failure and unload checks were retained. The packet known-floor
fixture now loads the neighboring chunks its shape queries depend on. The
entities-unready fixture compares callback counts at entry to that phase, so
callbacks from earlier phases in a reused test world cannot create a false
failure. No behavior case was disabled or excluded.

Pinned `neoforge-21.1.249-sources.jar` `BlockCollisions` was inspected: native
discovery uses a one-cell shape margin and `getChunkForCollisions`, and invokes
shape callbacks with the collision getter. The target records missing domains
and accounts for adjacent-cell dependencies instead of treating skipped chunks
as known air. No Mixin descriptor or injection seam changed.

Commands run from repository root:

```powershell
.\targets\neoforge-1.21.1\gradlew.bat -p common test --console plain --no-daemon
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 tasks --all --console plain --no-daemon
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification --console plain --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/measure-hot-paths.ps1 -JavaHome 'C:/Program Files/Java/jdk-21.0.11'
git diff --check
```

Baseline common tests passed before edits. Final combined build/dynamics/Sable
command exited 0 (75 seconds). Current common reports contain 427 tests and target
reports contain 74 tests, with zero failures, errors or skips. JVM controls and
consumer import checks passed. The fresh full server result is
`PASS 2264 sable=SKIPPED (not installed)`; the separate strict result is
`PASS contact compatibility 2265 sable=PASS 2.0.5`, with all ten required Sable
coverage labels. Optional-mod absence and presence are therefore separate evidence.
`git diff --check` passed. Earlier development failures described above were fixed;
old PASS files were not used.

Fresh artifacts:

- `build/hot-path-measurements/verification.log` and `measurement.log`.
- `common/build/test-results/test/` and `targets/neoforge-1.21.1/build/test-results/test/`.
- `targets/neoforge-1.21.1/run/control-verification/control-boundary-result.txt`.
- `targets/neoforge-1.21.1/run/sable-verification/control-boundary-result.txt`
  and `sable-checks-result.txt`.

The fairness control used 40 explicitly populated chunks plus the native loaded
queue. It passed through 2,078 real server ticks. At the measurement boundary,
base/strict runs respectively reported 52,914/52,852 cumulative discovery visits,
a 245-key recurring discovery queue and oldest active-task ages of 112/120 ticks.
These counters describe the whole control sequence, not a per-source latency SLA.

### Performance evidence and limits

The standalone test-only measurement program compiles the baseline common source
from `git archive` and the current common source with the same `--release 17`
compiler settings. It runs each in a separate JVM with `-Xbatch -Xms256m -Xmx512m`, 500
warmup operations and 200 timed samples per scenario. Inputs use deterministic
grids (seed 0); timed loops do not print. It measures median/p95, thread allocation,
contains/evaluator counts and block candidate visits. Baseline block visits are
the known full-list scan count; current visits are the scene's measured counter.
The baseline field branch invokes the original registry/filter/contribution path;
the current branch uses the owner-scoped query through reflection.

Machine: Windows 11 10.0.26300, AMD Ryzen 9 9950X (16 cores / 32 logical CPUs),
approximately 61.5 GiB visible RAM. Final measurement JDK: Oracle 21.0.11.
The game gates likewise use Java 21; common production remains Java 17 compatible.
Raw CSVs, exported source/classes and environment metadata are under
`build/hot-path-measurements/`. These are common hot-path measurements, not a claim
of improved whole-game TPS, client FPS or tail latency under every workload.

Selected final results (microseconds; allocation is bytes per complete sample):

| Scenario | Median before / after | p95 before / after | Allocation before / after | Work before / after |
| --- | ---: | ---: | ---: | --- |
| Empty, 1 provider | 1.9 / 1.8 | 3.2 / 2.2 | 1,992 / 1,448 | 0 evaluations |
| 512 sources, 1 provider | 282.3 / 209.5 | 322.7 / 228.6 | 2,375,776 / 910,728 | contains 1,024 / 512 |
| 512 sources, 8 providers | 1,113.3 / 208.0 | 1,189.9 / 218.4 | 5,318,872 / 809,296 | contains 4,608 / 512 |
| 512 sources, 32 providers | 3,898.4 / 194.5 | 4,376.6 / 207.4 | 15,381,704 / 706,672 | contains 16,896 / 512 |
| 512 unbounded sources, 8 providers | 1,115.4 / 205.4 | 1,332.0 / 231.2 | 5,285,848 / 805,328 | contains 4,608 / 512 |
| 16 primitives, build + 32 queries | 0.9 / 1.1 | 1.0 / 1.1 | 2,968 / 4,808 | visits 512 / 512 |
| 256 primitives, build + 32 queries | 5.2 / 3.0 | 5.5 / 3.2 | 4,888 / 14,848 | visits 8,192 / 1,248 |
| 4,096 primitives, build + 1 query | 6.6 / 7.2 | 7.0 / 7.6 | 33,128 / 33,296 | visits 4,096 / 4,096 |
| 4,096 primitives, build + 32 queries | 98.1 / 30.5 | 101.7 / 31.5 | 35,608 / 137,728 | visits 131,072 / 16,608 |
| 4,096 primitives, build + 128 queries | 375.5 / 38.1 | 387.7 / 40.0 | 43,288 / 156,928 | visits 524,288 / 17,376 |
| 4,096 primitives, build + 128 cell lookups | 343.6 / 50.9 | 354.5 / 55.3 | 49,432 / 236,032 | direct cell lookup after one build |

Each nonempty field case still calls every source evaluator exactly once. Unbounded
sources retain the cost of visiting all owned global candidates; unrelated provider
partitions no longer multiply that work. Sorting remains at provider-local
ordering and global accumulation boundaries.

Index construction alone for 4,096 primitives remains a short-lived scene cost
of 3.9 / 3.1 microseconds (the hierarchy is deferred until repeated queries).
Small scenes and single-query scenes retain minor absolute overhead for coverage,
identity/index bookkeeping and counters: the table includes those regressions.
Repeated-query and cell-lookup indexes trade additional bounded operation-local
memory for fewer candidate visits. Restoring missing-space checks and fair block
work is correctness work, not an optimization against the old skipped-work path.

Background-JIT runs are retained as `baseline-background-jit.csv` and
`current-background-jit.csv`. They exposed a large-scene single-query timing
outlier (72.2 versus 7.1 microseconds); synchronous compilation reduced that case
to 7.2 versus 6.6 without a production-code change. The final comparison therefore
uses identical synchronous-JIT settings and claims steady-state costs only.
Very short empty-provider samples still show tier/compilation outliers in both
versions (roughly 0.39 ms p95 for the 8-provider case); all raw samples' summaries
are retained. No startup-latency or statistically robust p99 claim is made.

Client execution and independent published-artifact consumer loading are NOT RUN
at CP1. No client rendering/input implementation changed; independent artifact
validation remains CP2. Arbitrary Java callbacks cannot be safely time-preempted;
finite collection and operation budgets do not bound callback internals. Explicit
unbounded discovery still costs loaded-world traversal. Persistent support and
unknown external providers intentionally retain conservative capture.

### Result

**FAIL at the reviewed commits - CP1-03 reopened.** The historical checks above
ran, but did not establish non-publication Block -> Entity motion: the falling
tick still used registry emptiness to reject the operation. CP1-08 also lacked
cross-provider aggregation diagnostics. See the corrective verification entry
below for current evidence. Client smoke and whole-game profiling remain NOT RUN;
historical microbenchmarks are not whole-game performance guarantees.

## CP2 - API, artifacts and maintenance checkpoint

Input: verified CP1 commit `288bb99`. Implementation commit:
`e635938a6a7227b7e43f91b369e3e011f4ede05d`. This evidence update follows that implementation.
Date: 2026-10-09. Scope: common Java 17 and NeoForge 1.21.1 / 21.1.249,
Java 21, optional Sable 2.0.5. Software/API migration baseline: **0.0.2**.
The pre-existing Sable version-property edits were preserved and included in the
CP2 commit, as requested by the subsequent instruction to commit and complete CP2.
Other targets received descriptor status/task metadata only; no port is claimed.
Nothing was pushed, tagged or remotely published.

### Implementation closure

| Item | Implementation and direct evidence |
| --- | --- |
| CP2-01 | Supported operation contracts now cover provider registration/session lifecycle, query coverage/contributions, publication/leases, sampling, observation, discovery and finite entity declarations. API_BOUNDARY and package Javadocs specify ownership, thread/side, validation, units, ordering and failures. Artifact reflection checks all 27 public API types and their public generic signatures. No internal public classes are promoted. |
| CP2-02 | Snapshot exposes assigned, effective and applied values plus derived ACTIVE_OPERATION/CURRENT_TICK/ASSIGNMENT_FALLBACK provenance. Outside an operation, current time/position/velocity and physical context must match. FIELD tick reuse remains prohibited without provider-context proof. Lifecycle controls prove frozen operation evidence survives publication changes, old-tick fallback, no session/provider/mutation effects, and preservation of assigned state independently of evaluation. Position-only defaults remain unchanged. |
| CP2-03 | `publicationProvider` delegates to the sole provider-scoped sampling path; the existing provider fixture uses it and separately contributes a bounded non-publication source. Exact EntityType declarations select existing finite integration modes, with explicit-over-built-in precedence, duplicate/late rejection, callback reentry/failure handling and no per-type mutable capability cache. Native dimensional/state exclusions remain. Controls cover dynamic restriction/recovery, inherited ordinary travel, custom travel requiring explicit declaration and duplicate/late registration. Existing geometry controls retain dynamic dimension/pose coverage. Loaded-only versus persistent source authority is documented without a recovery/readiness API. |
| CP2-04 | README now has the verified support matrix, actual artifact dependency and compiling fixture links. AGENTS is shortened; its detailed architecture contracts were moved verbatim to ARCHITECTURE and detailed verification rules to DEVELOPMENT (with corrected build facts). A direct old/new comparison confirmed no ownership-rule loss. Historical execution plan is explicitly superseded. Local link checker passes. Existing LICENSE/source notices remain; LICENSE is also embedded in the built JAR. |
| CP2-05 | CI triggers include complete target trees, wrappers, shared scripts and artifact verification code. Root and CI discovery use target ci.properties; status and aggregate task names come from those descriptors. Windows root aggregate passes; POSIX selects bash, with actual POSIX execution NOT RUN. Windows PowerShell 5 discovery works after moving PSScriptRoot default evaluation into the script body. Fixed verification run configurations remove task-name inspection. An indirect aggregate with controlBoundaryChecks=false still launches the full controls and passes with fresh results. `verifyApiArtifact` independently compiles API-only fixtures from the finished JAR plus platform dependencies, excludes engine/common output directories and Sable, loads every public API type from that JAR, inspects signatures, checks common embedding, metadata, resources and license. |
| CP2-06 | One documented 0.0.1 -> 0.0.2 migration: append assigned values/provenance to the snapshot constructor, reject negative magnitude values, explicitly declare audited custom travel integration. Existing accessors, units, sampling defaults, coverage, ordering and lease lifetime remain. No compatibility constructor guesses provenance. Provider convenience and declarations are additive. Save and wire formats remain internal and unchanged. Support is restricted to verified common/primary-target API; other integrations and client visual acceptance are not implied. |

### Executed verification

Environment: Windows 11, Gradle 9.7.1, Java 26 launcher, pinned Java 21.0.11
target/game toolchain and Java 17 common production. Commands ran from repository
root unless specified otherwise. Logs below are local ignored build outputs.

```powershell
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification --console plain --no-daemon
.\gradlew.bat build '-Ptarget=neoforge-1.21.1' --console plain --no-daemon
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 :javadoc --console plain --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/discover-targets.ps1
python scripts/check-doc-links.py
git diff --check
```

- Full build + dynamics + strict Sable: **exit 0**, 78 seconds, 27 tasks;
  `build/cp2-verification.log`. Common **427 tests**, target **74 tests**, zero
  failures/errors/skips. JVM controls passed. API controls: 110 assertions;
  publication lifecycle: 136 assertions in both servers. Passive scheduling
  exercised 2,078 real server ticks, including bounded non-publication discovery.
- Fresh full server result: `run/control-verification/control-boundary-result.txt`
  under the primary target: `PASS 2264 sable=SKIPPED (not installed)`.
- Fresh strict server result: `run/sable-verification/control-boundary-result.txt`:
  `PASS contact compatibility 2265 sable=PASS 2.0.5`. Its
  `sable-checks-result.txt` contains all ten required areas: contacts, context,
  continuity, delayed-endpoints, gravity, landing-commit, modes, passive-entities,
  physics and transport. Sable absence in the ordinary server is intentional;
  it is not substituted for the strict installed-mod gate.
- Root aggregate: **exit 0**, 20 seconds; `build/cp2-root-build.log`. It discovered
  and executed `buildNeoForge1211` from metadata, including artifact and JVM checks.
  Quote dotted -P values in Windows PowerShell; the unquoted initial invocation
  was parsed as an invalid target and was corrected.
- Independent artifact verification: **PASS**, 27 public API types, final
  `GravityEngine-neoforge-1.21.1-0.0.2.jar`. Both API and common classes were loaded
  from the JAR; Sable and production output directories were absent. Consumer
  fixture compilation and the snapshot-copy operation passed. This does not claim
  an independently packaged game/client launch: transformed behavior was checked
  separately by the server controls.
- Indirect-run regression: **exit 0**, 44 seconds; `build/cp2-indirect.log`. A local
  init script in `build/cp2-indirect.gradle` registered `indirectConsumerVerification`
  depending on `dynamicsCoreVerification` and `verifyApiArtifact`. Executed with
  `-I D:/javaProjects/GravityEngine/build/cp2-indirect.gradle` and
  `-PcontrolBoundaryChecks=false`; controls still loaded and wrote a new PASS.
  This is a temporary verification fixture, not another permanent task layer.
- Supported target/common API Javadocs: **exit 0**, `build/cp2-javadoc.log`; output
  in `targets/neoforge-1.21.1/build/docs/javadoc`. Missing @param/@return warnings
  remain. The task intentionally documents supported API rather than Java-public
  internals. The initial all-implementation doc run found existing malformed HTML
  outside the supported API; it is not claimed as an internal-documentation pass.
- Descriptor discovery: **exit 0**, all four descriptors reported with PRIMARY or
  SCAFFOLD_UNVERIFIED status. Documentation links: **PASS**, all maintained entry
  points. Diff whitespace check passes after removing a trailing blank line.

The first transformed attempt correctly rejected a geometry fixture's custom
player travel override; that fixture records operands then delegates to Vanilla.
It now declares CHARACTER through the same supported consumer API. The corrected
full and indirect runs above pass; the conservative production rule was retained.

### Complexity, compatibility and limits

No freshness state, FIELD coverage generation, second composition path or second
application authority was added. Observation provenance is derived. Entity adapter
definitions have process lifetime and freeze at first integration; their current
per-entity result is not retained. The class-only custom-travel classification is
immutable derived evidence. The publication convenience function owns no registry
or source lifecycle. Duplicate command-line verification-mode state was removed.

CP2 did not change CP1's measured composition, spatial index, scene query or block
scheduler algorithms; those benchmark numbers remain historical CP1 evidence,
not new measurements. Adapter dispatch and the stricter optional observation getter
were not profiled; no zero-cost or whole-game performance claim is made.

**NOT RUN:** client smoke/render/input acceptance, POSIX execution, other-target
runtime parity, live GitHub Actions execution and whole-game profiling. Build/CI
logic was verified locally, with Windows execution and source review of the POSIX
branch. No new client presentation implementation, Mixin seam, save format or
network protocol was introduced. These limits do not expand supported scope.

### Result

**BLOCKED at the reviewed commits - final stability confirmation depends on the
CP1 runtime correction.** The recorded API/artifact and server gate executions
remain historical evidence, not proof that all consumers obey provider coverage.
The historical API baseline was 0.0.2 for common and NeoForge 1.21.1 / 21.1.249.
See the corrective verification entry below; unverified domains remain explicit.

## Dependency refresh and Sable review — 2026-10-09

This entry supersedes version pins in the historical CP2 evidence above, not its
ownership contracts. Minecraft target lines and the supported API remain unchanged.

### Dependency changes

Versions were checked against official Maven metadata and release APIs on the
review date. Prereleases and unrelated Minecraft lines were not selected.

| Dependency | Previous | Selected |
| --- | --- | --- |
| Sable, NeoForge 1.21.1 | 2.0.5 | 2.0.6 |
| NeoForge, 1.21.1 target | 21.1.249 | 21.1.256 |
| NeoForge, 26.1 target | 26.1.2.109 | 26.1.2.114 |
| Forge, 1.20.1 target | 47.4.23 | 47.4.26 |
| ModDevGradle / LegacyForge plugin | 2.0.141 / 2.0.142 | 2.0.148 |
| Fabric Loom | 1.10.5 | 1.18.3 |
| Mod Publish plugin | 2.1.1 | 2.2.1 |
| Gradle, root / NeoForge / Fabric | 9.7.1 / 9.7.1 / 8.14.5 | 9.8.1 |
| Gradle, Forge | 8.14.5 | 8.14.6, retained compatible maintenance line |
| JUnit BOM | 5.11.4 | 6.1.3 |
| JOML, test oracle only | 1.10.5 | 1.10.9 |
| ASM / ASM Tree, target tests | 9.7.1 | 9.10.1 |

Fabric Loader 0.19.5 and Fabric API 0.92.12+1.20.1 were already current for the
target. Sable companion remains 1.6.0, matching the embedded API and Sable's
explicit incompatibility with newer companion versions. Minecraft/loader-owned
transitive libraries were not independently forced to unrelated versions.

Metadata sources include [NeoForge](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml),
[Forge](https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml),
[Loom](https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml),
[JUnit](https://repo.maven.apache.org/maven2/org/junit/junit-bom/maven-metadata.xml),
and [Gradle releases](https://services.gradle.org/versions/all).
Sable release/tag/artifact provenance is in the
[compatibility review](SABLE_COMPAT_CHECKLIST.md).

Loom now needs a Java 25 launcher; production/common bytecode stays Java 17.
Fabric's CI descriptor therefore selects Java 25. Root aggregation now resolves
each target's launcher from the same descriptor through Gradle toolchains,
allowing Forge/primary NeoForge to use Java 21 and Fabric/NeoForge 26.1 to use 25.
No production collision algorithm, supported API, physical state owner,
persistence format or network protocol changed.

### Verification

- **PASS:** common JUnit, 427 tests, zero failures/errors/skips; primary JUnit,
  74 tests, zero failures/errors/skips. Counts are from this run's XML, not CP2.
- **PASS:** primary build, JVM controls, consumer import checks and independent
  final-JAR API fixture loading without Sable (27 public API types).
- **PASS:** full transformed server without Sable, fresh result
  `PASS 2264 sable=SKIPPED (not installed)`.
- **PASS:** strict transformed server with Sable 2.0.6, fresh result
  `PASS contact compatibility 2265 sable=PASS 2.0.6` and all eleven manifest areas.
  The new required `distributed-gravity` area checks rotated two-mass tidal
  torque against angular momentum and late incomplete-query atomicity.
- **PASS:** individual Forge, Fabric and NeoForge 26.1 builds. These remain
  scaffold build evidence, not runtime parity.
- **PASS:** root `build '-PallTargets=true' --console plain --no-daemon`, launched
  from Java 21 and selecting each target's declared JDK. All four target builds
  and common completed in 52s; log: `build/dependency-all-targets.log`.
- **PASS:** maintained documentation-link check and `git diff --check`.

Final primary command (Java 21, target wrapper 9.8.1):

```powershell
.\targets\neoforge-1.21.1\gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification --console plain --no-daemon
```

It completed successfully in 1m 19s; log: `build/dependency-verification.log`.
The Forge 8.14.6 confirmation log is `build/dependency-forge-final.log`;
other target logs are `build/dependency-fabric.log` and `build/dependency-neo26.log`.
The user explicitly accepted the EULA for both verification directories before
the acceptance files were created. Existing gate preparation removed old PASS
files before each launch.

The first primary launch failed with missing common `CellPos` metadata while
independent builds were recompiling the shared `common/build` directory.
Sequential reruns passed without changing production code or weakening tests.
Independent targets sharing this directory must not compile concurrently.
An initial direct Maven metadata request returned HTTP 403; normal Gradle
resolution succeeded. Its Sable runtime hash matches the official Modrinth
artifact inspected during review. Neither initial attempt is claimed as PASS.

**NOT RUN:** client smoke, packaged-client/multiplayer acceptance, POSIX execution,
Create/Aeronautics combinations, other-target gameplay parity, and performance
profiling. Sable logs unknown `create:flywheel` physics-property entries when
Create is absent; the installed-mod gate still completed, and this does not
certify Create compatibility. Remaining supported-scope limits and prioritized
acceptance gaps are in [SABLE_COMPAT_CHECKLIST.md](SABLE_COMPAT_CHECKLIST.md).


## Provider lifecycle review correction - 2026-10-09

The review of `288bb99`, `e635938` and `631a57c` against `186f70f` correctly
reopened CP1-03 and blocked final CP2 stability confirmation. The historical
result paragraphs above now reflect that judgment; old successful executions
are not retroactive proof of this missing behavior.

### Correction and ownership

- FallingBlock tick opening no longer treats a missing runtime or an empty
  publication registry as absence. The committed application selects evaluation
  authority. The server evaluates through the existing ballistic/provider path
  once, before deciding whether a collision scene is necessary. Native
  acceleration with no external collision requirement skips scene capture;
  other cases install the same evaluation in the operation. Nested operations
  retain their existing owner and clients retain server snapshot authority.
- Contributions/coverage remain query-local evidence; committed application,
  durable assignment and native attachment ownership are unchanged. Only a
  complete provider query establishes FIELD absence, including after reload;
  discovery bounds and publication membership cannot do so. Incomplete results
  continue through the existing assignment/application continuity policy.
- Runtime aggregation keeps an invocation-local contribution ID -> provider map.
  Duplicate identities identify both providers, the Level and query. Pure
  composition failures include Level/query/provider context and retain their
  original cause. No diagnostic registry, persistence or new service was added.

### Retained behavior controls and fresh evidence

No new small unit-test class or standalone test framework was added. Existing
`FallingLifecycleChecks` trajectory controls now retain a real Block -> Entity
conversion with native downward support, then invoke nine transformed Vanilla
entity ticks per scenario: equivalent publication-backed gravity, non-publication
nonzero gravity with the entire Level registry empty, and that non-publication
source while adding/removing a spatially unrelated publication. They compare
position, velocity, acceleration, direction and ballistic ownership, and verify
one evaluation per expected provider per free-flight tick. These are actual
entity tick invocations within the server control boundary, not nine separately
scheduled world ticks or a client prediction test.

Existing `PublicationLifecycleChecks` now also exercise conflicting provider IDs,
composition overflow diagnostics and successful evaluation after failure. The
existing valid six-face landing, native trajectory, failure/rollback and dynamic
support tests remain; none was deleted merely for being small or failing.

Command (Java 21.0.11, primary target wrapper; NeoForge 21.1.256):

```powershell
./targets/neoforge-1.21.1/gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification --console plain --no-daemon
```

- PASS: common 427 and target 74 JUnit tests, no failures/errors/skips; target
  build, JVM controls and finished-JAR API consumer validation. Unit tests ran
  in the earlier build of this correction; unchanged test tasks were up-to-date
  in the final aggregate.
- PASS: fresh ordinary and strict Sable 2.0.6 transformed server gates. Both log
  `FALLING_LIFECYCLE_PASSED ... provider_trajectory` and
  `PUBLICATION_LIFECYCLE_CHECKS_PASSED assertions=142`. Both include the provider
  correction alongside existing landing, native fallback and ownership checks.
- Logs: `build/provider-fix-build.log`, `build/provider-fix-verification.log`,
  and final `build/provider-fix-final.log` (BUILD SUCCESSFUL, 1m 11s).
  The gates cleared their result files before launching; no earlier PASS file
  was reused. Assertion counts are not counts of independent game scenarios.
- NOT RUN for this correction: client smoke/prediction, POSIX, other target
  runtime parity, baseline microbenchmark rerun and whole-game profiling.
  Sable's missing `create:flywheel` tag warning remains because Create is absent;
  it did not prevent the strict installed-mod gate from passing.

**Corrective scope PASS:** P1 provider-driven falling motion and P2 aggregation
diagnostics are repaired and verified above. The identified CP1 runtime blocker
for CP2 is resolved in this working tree. This does not reclassify historical
commits as complete or extend acceptance to the explicitly unverified domains.

## 2026-10-09: composed rigid trajectory groundwork (partial compatibility task)

The requested complete NeoForge 21.1.256, Sable 2.0.6, Create 6.0.10 and
Aeronautics Bundled 1.3.2 integration is **not complete**. This checkpoint
implements only its shared trajectory foundation. No target adapter or Mixin was
changed, and no Create/Aeronautics runtime profile was added.

`RigidTrajectory` is an internal sealed immutable Java 17 contract implemented by
the existing single-body snapshot and new `ComposedRigidTrajectory`. Parent and
child captures must cover the same tick and interval. The producer owns composite
revision/continuity invalidation; these records are transient operation evidence,
not another physics, persistence or network owner. Collision publications, sweeps,
support carry and release velocity consume the same trajectory. Composite point
velocity sums parent and transformed child velocity once. Nested enclosures are
conservative; support chord error uses an acceleration bound including Coriolis
acceleration. Existing single-body paths retain their optimized bounds. The
fixed-axis collision clearance proof is used only for single-body snapshots.
Supported consumer APIs and persistence formats are unchanged.

Seven focused tests cover composed pose/velocity, material-corner swept enclosure
in a moving observer frame, speed bounds, full-turn child-origin carry despite
equal endpoints, translation ownership, invalid time coverage, mixed-axis capsule
and box collision, and chord/acceleration bounds with normalized-linear rotation.
Existing tests were retained. Work budgets and numerical thresholds were not
weakened. Conservative composition may still reject work exceeding those budgets;
whole-game performance has not been measured.

Fresh verification, run sequentially with the target-owned wrappers:

```powershell
# Java 21.0.11 launcher; Java 17 common production toolchain
./targets/neoforge-1.21.1/gradlew.bat -p common test --console plain --no-daemon
./targets/neoforge-1.21.1/gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification --console plain --no-daemon
./targets/forge-1.20.1/gradlew.bat -p targets/forge-1.20.1 build --console plain --no-daemon
# Java 25.0.3 launcher
./targets/fabric-1.20.1/gradlew.bat -p targets/fabric-1.20.1 build --console plain --no-daemon
./targets/neoforge-26.1/gradlew.bat -p targets/neoforge-26.1 build --console plain --no-daemon
```

- **PASS:** common 434 tests and primary target 74 tests, zero failures, errors or
  skipped tests. The final seventh regression was added after the primary server
  run and passed in the subsequent common run and cross-target builds.
- **PASS:** primary build, JVM controls, consumer-import guard and finished-JAR API
  artifact verification; all three other target builds. Scaffold builds do not
  establish other-loader gameplay parity.
- **PASS:** fresh `dynamicsCoreVerification` and strict
  `sableCompatibilityVerification`. The ordinary result is `PASS 2264
  sable=SKIPPED (not installed)`; the installed result is `PASS contact
  compatibility 2265 sable=PASS 2.0.6`, with all eleven required Sable markers.
  Each gate deleted its previous result files before launching.
- Evidence: `build/composed-trajectory-common.log`,
  `build/composed-trajectory-primary-final.log`, and
  `build/composed-trajectory-{forge-1.20.1,fabric-1.20.1,neoforge-26.1}.log`.
  An intermediate test compilation failed because of a test variable typo; it
  was corrected before the final common run. No production failure was excluded.
- **NOT RUN:** full Create/Aeronautics servers, clients, multiplayer, sparse/fleet
  baseline comparisons and packaged-client smoke. Existing Sable-only gates do
  not exercise the new composite publication through a real contraption adapter.
- **BLOCKED acceptance:** the requested integration is unfinished. Contraption
  publications, actual substep motion capture, stable plot rebasing, conservative
  discovery, native chunk/assembly lifecycle coverage, swimming/climbing/scaffold
  movement, boarding/dismount/interaction, distributed attached masses, sparse
  mass caching, balloon/floating-block gravity operands and runtime profiles still
  need implementation and their required client/multiplayer/performance evidence.

Native Rapier water buoyancy and native Sable rigid integration remain unchanged.
This checkpoint must not be used as a complete compatibility acceptance report.

## 2026-10-09: WIP review continuation (in progress)

Review baseline: `2edc4269eb7c854e43babc2eb3ed8b94b895d864`; changes below are
uncommitted working-tree corrections to that baseline. The current capability
matrix is in [SABLE_COMPAT_CHECKLIST.md](SABLE_COMPAT_CHECKLIST.md). The historical
composed-only entry above is retained as historical evidence, not current source
inventory. CP-S1 and CP-S2 are **not closed**.

The confirmed mass replacement defect is repaired using native tracker-derived,
cell-relative first moments. `updateMassDataFromBlockChange` and `addBlockMass`
provide nested transaction boundaries; intermediate or failed edits cannot yield
complete evidence. The actual native COM operand is captured before mutation,
without invoking custom COM providers twice. Native tracker rebuild is the
recovery/lifetime boundary. No GE mass, inertia or rigid integrator was added.
The regression changes COM through the native extension point, performs native
replacement/removal/addition, checks native mass/COM agreement and verifies that
the following gravity correction is applied exactly once.

Other corrections record the native sample tick, reject stale interval relabeling,
retain observed intermediate bounds on failed capture, preserve native dismount
candidate dimensions and world borders, and charge Create environment enumeration
before address sorting. Address order is derived from native geometry lifetime;
subject-specific collision shapes remain uncached. Floating velocity prediction
uses the same sampled-gravity units and location as the floating lift operand,
preserving the native coefficient and call order. Its real native regression
covers complete/incomplete, zero, sideways, inverted and oblique uniform fields.
This is not distributed gradient-lift acceptance.

Software identity is restored to `0.0.2`, matching the documented API migration.
No remote publication, tag, push or consumer/persistence format change occurred.

Commands use Java 21.0.11 and the primary wrapper, sequentially:

```powershell
./targets/neoforge-1.21.1/gradlew.bat -p targets/neoforge-1.21.1 build dynamicsCoreVerification sableCompatibilityVerification -PcompatibilityProfile=sable --offline --console plain --no-daemon
./targets/neoforge-1.21.1/gradlew.bat -p targets/neoforge-1.21.1 build sableCompatibilityVerification prepareCompatibilityMultiplayer -PcompatibilityProfile=aeronautics -PaeronauticsReleaseJar=D:/javaProjects/GravityEngine/build/provider-gradle-home/caches/modules-2/files-2.1/curse.maven/create-aeronautics-676721/8763471/ddbe9cbc25e8f663218aa1e9fdfc4b99f0e11e4b/create-aeronautics-676721-8763471.jar --offline --console plain --no-daemon
./scripts/verify-compatibility-multiplayer.ps1
```

- **PASS:** `build/sb01-native-mass.log`, initial native replacement correction;
  `build/sb01-sb03-sb06-verification.log`, ordinary and Sable-only transformed
  gates; `build/sb-review-full-stack.log`, full-stack server and launch preparation;
  `build/sb-mass-operand-verification.log`, actual native mass operand and force
  correction checks; `build/sb-floating-prediction.log`, floating prediction;
  `build/sb-interval-verification.log`, stale/paused native physics intervals.
  These are successive implementation snapshots, not interchangeable final passes.
- **PASS:** `build/sb-review-sable-final.log`, updated ordinary/Sable-only gates
  with the required `mass-replacement` marker. At this checkpoint the common
  reports contain 437 tests and the primary target reports contain 74, with zero
  failures/errors/skips. Unchanged Gradle test tasks may be up-to-date; transformed
  server results were cleared and regenerated before each gate.
- **PASS, limited scope:** fresh files in
  `build/multiplayer/aeronautics/20261009-204757`: dedicated server plus two actual
  Minecraft clients complete eight staged lifecycle phases. Native TCP game input
  is delayed 100 ms in order; Sable UDP is **not** delayed by this fixture. Tests
  cover default/sideways/oblique gravity, observer pose, camera nonmutation,
  dimension transfer, death/respawn, reconnect and distant chunk streaming. The
  initial assignment is acknowledged before the stationary relocation assertion.
  This is not dynamic platform, packet loss or arbitrary reordering acceptance.
- **FAIL / open:** concurrent initial assignment and relocation settled below
  the requested position. Diagnostics are retained in
  `build/multiplayer/aeronautics/20261009-204145`. This case remains selectable
  through `verify-compatibility-multiplayer.ps1 -ConcurrentAssignment`; staging
  setup for the other lifecycle checks does not close it.
- **FAIL:** `build/sb-review-create-client.log` passed server assertions but
  stalled during server shutdown in `ConcurrentLinkedDeque.poll` from native
  `ChunkMap.processUnloads`. Thread evidence is
  `build/sb-create-shutdown-threads.txt`; the owned JVM was stopped. The client
  task did not run in that invocation. A fresh retry is required.
- **PASS:** `build/sb-create-callback-client.log` and
  `build/sb-review-aeronautics-client.log`: fresh Create/full-stack server and
  actual integrated-client checks. Native landing callbacks receive the
  contraption's storage world exactly once. The earlier shutdown failure remains
  historical evidence; both retries shut down successfully.
- **PASS:** `build/sb-review-sable-client.log`: ordinary dynamics, Sable-only
  server and actual client, including native pause and near-full-revolution
  intermediate-excursion assertions. This run predates profile-world isolation.
- **PASS:** `build/sb-native-bearing-motor.log` (Create) and
  `build/sb-native-bearing-aeronautics.log` (full stack): a real native creative
  motor powers a mechanical bearing. Native assembly removes the plot block;
  controller ticks rotate the child while Sable's physics system moves/rotates
  the parent; disassembly removes the entity and retires its collision identity.
  These runs use separate `--world compatibility-<profile>` directories.
  The initial fixture with an unpowered bearing failed in
  `build/sb-native-bearing.log`; it was replaced with a real kinetic power source.
- **PASS, build scope only:** `build/sb-review-forge-build.log` (Java 21 launcher),
  `build/sb-review-fabric-build.log` and `build/sb-review-neoforge261-build.log`
  (Java 25 launchers), each using its own wrapper with
  `build --offline --console plain --no-daemon`, sequentially. Common tests
  actually executed in these builds. Other loaders remain runtime-unverified.
- **Concurrent-handoff diagnosis:** the fresh trace in
  `build/multiplayer/aeronautics/20261009-210642` shows teleport/application
  epochs 2/3 using VANILLA, followed by epoch 4 CHARACTER only after the native
  acknowledgement. Both sides acquire downward momentum during the pending
  interval and retain it on commit. `PlayerBodyHandoff.afterConnectionTick`
  explicitly defers while awaiting a teleport; ARCHITECTURE requires retaining
  world momentum and forbids treating desired assignment as committed ownership.
  Thus the old immediate-rest assertion was invalid for this fixture. Its FAIL
  logs are retained. The concurrent test now checks each zero-gravity tick's
  displacement and native drag from the prior world velocity, with 1e-6 tolerance,
  including the first committed tick; subsequent lifecycle phases retain their
  stationary position checks. Fresh execution is still required.
- **PASS:** `build/sb-lift-sampling-scope.log`: native prediction and floating
  force methods evaluate their identical query once per pre-physics call, both
  with complete and incomplete provider evidence. Evidence is transient to that
  call; it is not a cross-substep field cache or an additional force owner.
- **PASS:** `build/sb-environment-halo-verification.log`: ordinary dynamics and
  full-stack transformed server gates after client chunk-source and flow-halo
  coverage fixes, plus bounded streaming of Create shape primitives.
- **PASS:** `build/multiplayer/aeronautics/20261009-212941`, launched with
  `./scripts/verify-compatibility-multiplayer.ps1 -ConcurrentAssignment`:
  both clients complete eight phases, each with 59 precise zero-gravity momentum
  assertions after the deferred handoff. Both also execute a deterministic
  unloaded-world environment probe and record `*-environment-PASS.txt`.
  This particular handoff did not encounter missing chunks (`unavailableFreezes=0`);
  the deterministic probe is what verifies rejection independently of timing.
  The earlier `211940` and `212139` failures exposed the difference between
  `ClientLevel.hasChunk` (always true) and the actual chunk-source availability.
  Neither their failures nor the original immediate-rest failures are overwritten.
- **PASS:** `build/sb-create-index-measurements-retry.log`: primary build/API
  artifact checks and strict Create server gate, including parent-bound and raw
  child relocation invalidation. The first measurement build failed for a missing
  harness import (`build/sb-create-index-measurements.log`); the retry corrected it.
  The isolated full-scan comparison also passed. Exact workloads, raw evidence
  paths and measured regressions are in [SABLE_DISCOVERY_MEASUREMENTS.md](SABLE_DISCOVERY_MEASUREMENTS.md).
- **PASS:** `build/sb-fresh-world-fixture-verification.log`: primary build,
  ordinary dynamics and Sable-only strict gate in the fresh native world
  `compatibility-sable-fresh-fixed-20261009`. Valid nonuniform scale replacement
  retires identities/continuity and installs scaled geometry without deformation.
  Direct public fluid updates defer missing evidence without impulse or partial
  metadata writes. The initial scale build had a missing fully qualified type;
  `sb-valid-scale-lifecycle-retry.log` then passed assertions but stalled during
  native shutdown (stack in `sb-scale-shutdown-threads.txt`), so that run failed.
  A fresh-world retry exposed asynchronous fixture chunk setup; the falling-block
  controls now wait, with a 30-second deadline, for native entity/block ticking
  before starting their unchanged 80-tick assertions. All 21 scenarios pass.
- **PASS:** `build/multiplayer/aeronautics/20261009-220431`, launched with
  `./scripts/verify-compatibility-multiplayer.ps1 -MovingPlatforms`: two real
  clients each record 318 observations standing on a native physics-driven Sable
  platform through translation, reversal, stop and rotation. Support remains
  installed and relative local drift stays within 0.1 block. TCP delay is 100 ms;
  Sable UDP is not delayed. Per-tick poses, velocity, continuity and support are
  retained in each client's `*-platform-samples.txt`. This is parent-platform
  standing coverage, not the complete moving-child/walking/impact matrix.
  Earlier fixture failure `215948` accepted spawn-floor support prematurely;
  `220125` then exposed expected capture failure escaping the public fluid method
  during native packet handling. The production boundary now explicitly defers
  that update. The passing run required no relaxed drift/support assertions.
- **PASS:** `build/sb-native-clock-boundary.log`: full-stack build and strict
  server gate after auditing Sable's `ServerLevel.tick` HEAD hook against
  Minecraft's later `tickTime()`. The optional clock hook labels the completed
  physics interval at that actual one-tick transition; arbitrary old queries
  still reject. No motion history or transport protocol was added.
- **PASS:** `build/sb-native-body-movement-owner.log`: full-stack build and strict
  server gate after native-shaped actors' teleports were made to retire retained
  GE support, carry and native material addresses. Native `Entity.move` writes
  retain their movement ownership independently of collider representation.
  The first attempt (`sb-native-body-teleport.log`) failed the existing carry
  regression because it misclassified those writes; the correction passes the
  original carry assertions plus teleport/reacquisition assertions.
- **Pending:** final per-profile server/client archives and artifact hashes.
  Distributed lift/late-incomplete semantics, partial water occlusion, complete
  real-controller motion/ownership and dismount rollback scenarios, conservative
  local Create source discovery, dynamic multiplayer and matched performance
  measurements remain open in the review matrix. None is reclassified as PASS.


## 2026-10-13 continuation — force transactions, clipped fluids and network CCD

CP-S1 / CP-S2 remain open while the remaining runtime matrix is executed.
This entry supersedes only the named pending items in the preceding checkpoint.

- Native Sable owns mass, pose and integration. One transient force step on the
  native body captures pose/mass/velocity, caches identical FIELD queries only
  within that step and stages GE lift/friction differences. Native queued forces
  execute once; GE mass and lift corrections commit together only after all
  evidence remains complete. Late incomplete coverage discards the whole GE
  correction and preserves native forces. No durable state or protocol was added.
- Floating lift samples each native material-cluster centroid with parent and
  relative child velocity. This is centroid quadrature, not exact integration
  of arbitrary nonlinear fields. Native uniform-field self-lift capping is
  retained; the varying-field capped group uses its lift-weighted mean gravity.
  Balloon operands and callback ordering were checked against Bundled 1.3.2
  bytecode. Attached tracker replacement/edit coverage uses native mass APIs;
  it does not yet establish Create.setBlock native property rebuilding.
- Host fluid cells are clipped against captured native occlusion voxels. Exact
  body intersection and eye membership use the retained convex pieces, whose
  cell-fraction weights prevent fragmentation from multiplying flow influence.
  Dirty native masks reject capture. On the GE-owned route, Sable's scalar
  center/feet swimming veto no longer overrides captured fluid metadata.
- The ordered delay harness now intercepts both native TCP and Sable UDP,
  checks delivery counters, bounds pending messages and releases queued buffers
  on disconnect. Windows evidence-file replacement retries only transient
  sharing denial, with a 100 ms deadline and unchanged failure propagation.
- **PASS:** `build/sb-fluid-force-verification.log`: primary build/API fixtures,
  ordinary dynamics and strict full-stack server controls, including analytic
  gradient lift, combined late-incomplete rejection and partial native occlusion.
- **FAIL (shutdown):** `build/sb-swimming-verification.log`: all behavior checks
  (including real Player partial-water swimming/fully-dry exit) completed, but
  native ChunkMap.processUnloads did not finish within the existing five-minute
  task timeout. Thread dumps: `build/sb-swimming-shutdown-threads*.txt`. This is
  not a successful complete gate; a fresh final run is still required.
- **PASS:** `build/sb-rigid-yaw-after.log`: full common suite after two regressions
  failed on the preceding implementations. First impact now stops at the near
  edge of the touching band rather than deliberately consuming penetration slop.
  A fixed rotation-axis projection proves clearance over its whole affine
  interval, including slightly entering relative translation that remains clear.
  No angular component is discarded and no tolerance or work limit was raised.
- **PASS, limited scenario:** `build/multiplayer/aeronautics/20261013-101330`:
  dedicated server and two clients, 0 ms TCP/UDP delay, translation/reversal/stop/
  rotation; 340 supported observations per client and <=0.1 block local drift.
  The delayed retry revealed that the launcher replaced only default world names;
  custom prepared world names were reused. The script now requires and replaces
  exactly one `--world` operand. Fresh-world final runs are pending.
- Retained failures include `20261013-100444` (first-impact penetration consumed
  the touching band), `20261013-101021` (near-tangent angular clearance), and
  `20261013-101428` (delayed run before fresh-world argument repair). Per-client
  traces and samples remain under `build/multiplayer/aeronautics/`.


### 2026-10-13 native controllers, edits and rotating support continuation

- **PASS:** `build/sb-cart-powered-verification.log`: primary build/API and
  full-stack transformed controls with actual powered bearing, piston and rail
  cart assembly/controller/disassembly; native swimming/air/fire and dismount
  pose rejection remain exercised. Earlier cart power and piston chunk fixture
  failures are retained in the preceding command logs.
- **PASS:** `build/multiplayer/aeronautics/20261013-102039`: fresh native world,
  dedicated server and two clients, 100 ms ordered TCP and Sable UDP delay,
  parent translation/reversal/stop/rotation with <=0.1 block relative drift.
- **FAIL:** child run `20261013-103825`: native teleport completion was observed
  and support persisted, but local drift exceeded 0.1 block at frame 209.
  The previous setup-only failures are retained; they did not exercise motion.
- A finite clipped support polygon's centroid could move tangent to the face
  even when the foot projection remained inside. The witness now prefers the
  foot projection when it is inside the exact finite intersection; overhanging
  support retains a real finite witness. First-contact distance is unchanged.
  `build/sb-support-material-before.log` fails the added regression;
  `build/sb-support-material-after.log` passes the full common suite.
- **PASS:** `build/sb-material-verification.log`: primary build/API, ordinary
  dynamics, strict full-stack server and multiplayer preparation after that
  contact correction. This precedes the attached-block rebuild below.
- **PASS:** `build/sb-attached-identity-verification.log`: primary build/API and
  strict full-stack server after actual Create.setBlock native property rebuild.
  Replacement, failure rollback, last-block removal, revival and identity
  retirement are required controls. Sable remains the property/pipeline owner.
  Initial Mixin Shadow binding and stale pre-edit identity fixture failures are
  retained in `sb-attached-block-verification.log` and
  `sb-attached-access-verification.log`; neither is a passing gate.
- **IN PROGRESS:** fresh child multiplayer after the contact fix; full-stack
  matched workload measurement; remaining scenario/profile final archives.
  CP-S1/CP-S2 are not closed by these individual passes.


### 2026-10-13 final matrix continuation

- Native aiStep zeroed each world velocity component below 0.003, including
  support velocity, while the support contribution owner retained that credit.
  Cleanup now operates on relative velocity and restores the separately owned
  carrier contribution only with current finite support evidence. The native
  0.02 blocks/second regression fails before the fix (`sb-support-deadzone-before.log`)
  and passes afterward (`sb-support-deadzone-phase.log`). No threshold changed.
- **PASS:** delayed child run `build/multiplayer/aeronautics/20261013-111038`
  after the fix, 340 observations/client; maximum displacement from first local
  sample 0.04087/0.03878 blocks. Extended child 100 ms run `20261013-111405`
  additionally exercises actual block update delivery, client key walking/jump,
  native bearing disassembly, parent removal and subsequent DIRECT zero gravity.
  Extended parent 0/100 ms runs `20261013-111525` / `20261013-111619` pass.
- **FAIL, harness race:** child 0 ms `20261013-111712` read phase 0 then observed
  a newly created phase file in the same callback and skipped placement setup.
  The callback now consistently uses its captured phase. A fresh run is required.
- **PASS, scoped performance:** `build/native-workload-comparison-20261013/`
  contains matched full-scan/indexed native bearing/Rapier/FIELD/environment
  workloads, original outputs, source snapshot, patch, hashes and raw CSVs.
  Counted FIELD queries and captured primitives agree. See
  `SABLE_DISCOVERY_MEASUREMENTS.md`; 2-substep p95 regresses, and this is not
  whole-client or full-fleet profiling. Snapshot predates the aiStep cleanup,
  which is outside the measured capture workload.
- The new incoming-wall test initially asserted a final contact impulse after a
  transient impact. Native damping leaves a measured 0.00224887-block endpoint
  gap, with complete empty current-contact evidence (`sb-incoming-gap-retry.log`).
  That assertion contradicts the existing path-contact/current-contact contract.
  Its replacement requires actual swept push, positive final separation, empty
  current contacts, zero phantom impulse and unchanged native rigid velocity.
  It passes in `sb-final-core.log`; that aggregate still FAILS later in a Create
  fixture cleanup and is not a passing strict gate. An unrelated scheduling
  failure in `sb-incoming-gap.log` is also retained.

- **PASS:** `build/sb-final-isolated.log`: primary build/API, strict full-stack
  server and launch preparation, including incoming-wall regression. The preceding
  Create failures (`sb-final-core-retry.log`, `sb-fixture-trace.log`) were caused
  by same-tick native plot reuse: Vanilla's four-entry chunk cache still returned
  the retired plot's chunk. The control fixture now invokes Vanilla's normal
  tick cache invalidation before the next independent plot fixture. This is a
  test-only accessor, not a production lifecycle or readiness protocol. Cleanup
  also avoids removing an already removed parent and masking the original cause.
  Ordinary dynamics passed earlier in `sb-final-core.log`; its later strict
  failure remains distinguished from that successful ordinary gate.

- **FAIL before scenario:** multiplayer `20261013-113502`, second client native
  Create/Registrate startup exception (`Found unused register callbacks`). The
  observed crash was recorded in a FAIL marker to stop the waiting harness;
  no movement acceptance ran in that launch.
- **FAIL, action fixture:** `20261013-113627` passed standing motion (318
  observations for the first client), then the second actor walked from the
  stone arm onto the recessed honey connector, losing support before the
  grounded-jump assertion. The action setup now replaces the central honey
  with full stone through actual Create.setBlock and waits for both native
  update packets. The original standing geometry and drift threshold remain
  unchanged. Both child network-delay variants require fresh acceptance.

- **PASS, final child actions:** `build/multiplayer/aeronautics/20261013-113838`
  (0 ms) and `20261013-113930` (100 ms TCP and Sable UDP). Both actual clients
  pass unchanged <=0.1-block standing-motion drift, flat-surface native key walk
  and jump, both attached block edits, native disassembly/removal and fresh
  zero DIRECT assignment with no stale support. Actual delivered packet counts
  are recorded separately per client/transport; no loss/reordering is claimed.
- **PASS, final general lifecycle 0 ms:** `20261013-114023`, eight phases with
  concurrent initial assignment, world momentum preservation, self/observer
  gravity and camera, dimension, death/respawn, reconnect and chunk streaming.

- **PASS, final general lifecycle 100 ms TCP/UDP:**
  `build/multiplayer/aeronautics/20261013-114114`, the same eight phases and
  concurrent initial assignment. Together with `20261013-114023`, this closes
  the selected 0/100 ms ordered-delay lifecycle matrix. It does not test loss,
  reordering or certify every possible contraption/controller configuration.

- **PASS:** final Sable-only strict server and actual integrated client,
  `build/compatibility-evidence/sable/20261013-114420/`. Manifest contains source
  HEAD + dirty diff/additions, source hashes, JDK, artifact/mod hashes and exact
  command; server and client results are freshly checked before archival.
- **FAIL, shutdown:** first final Create profile passed behavior controls but
  did not exit, spending over two minutes in native ChunkMap unload rescheduling.
  Two thread dumps are retained in `build/sb-create-shutdown-threads*.txt`; only
  that invocation's JVM was terminated. The raw failed command is retained as
  `build/sb-final-profile-create-shutdown-fail.log`. This is not a passing gate.
- Multiplayer launcher now captures source diff/additions and hashes, mod/artifact
  hashes, profile/JDK and selected network parameters before starting its JVMs.
  A final rerun binds each accepted scenario to that immutable launch snapshot.

- The shutdown loop reproduced in the first final Aeronautics run as well
  (`sb-final-profile-aeronautics-shutdown-fail.log`, `sb-aero-shutdown-threads.txt`).
  Matching Minecraft 1.21.1 ChunkMap.scheduleUnload reschedules when a holder's
  generation reference count is nonzero; stopServer drains that queue without
  returning to the generation work. Control cleanup can still enqueue such work.
  Controls now continue normal ticks after fixture cleanup and inspect native
  updating/unloading holders' isReadyForSaving and pending task count before
  requesting stop. A 600-tick deadline writes FAIL; no queue is erased, generation
  counter changed, save skipped or production lifecycle replaced. All three
  final profiles are rerun with this termination fix.

- **PASS:** final server/client profiles with bounded native shutdown:
  `build/compatibility-evidence/sable/20261013-115514/`,
  `create/20261013-115726/`, `aeronautics/20261013-115942/`.
  They use the same engine JAR SHA256
  `2C770703295EF620A98A845CBE5CB4797425E64E222943B95B4F50E10E069B5F`.
- **PASS:** `build/sb-final-all-targets.log`, sequential root all-target build;
  additional loader targets remain scaffold/runtime-unverified. **PASS:**
  `build/sb-final-ordinary.log`, fresh absent-mod dynamics with native shutdown.
- Final plan-to-evidence audit found that the previous multiplayer matrix did
  not yet exercise incoming native bodies, actual Create seats or source
  tracking replacement, and its four-tick walk was not the requested continuous
  combined parent/child walk. CP-S2 remains open until these additions run.
  The expanded native assembly now includes a deck and seat, 80 continuous
  input ticks under parent and child motion, self/observer mounted transforms,
  native Shift dismount, out-of-range source removal and fresh tracking objects.
  Run `20261013-120907` passes walk/seat/dismount/untracking, but FAILS re-landing
  after immediate return teleport. The setup now observes actual replacement
  source arrival before restoring gravity and placing the falling actor.


### 2026-10-13 — deferred client dismount boundary

Pinned Create 6.0.10 bytecode shows a second placement writer:
`ContraptionSeatMappingPacket` records a native one-shot location, then
`ContraptionHandler.entitiesWhoJustDismountedGetSentToTheRightLocation` commits
it through `LivingEntity.absMoveTo`. This bypassed the earlier `dismountTo`
validation. Both real clients reproduced a raw plot-coordinate write near
20,481,028 in `build/multiplayer/aeronautics/20261013-123404`.
`CreateDeferredDismountMixin` now projects and validates that final write while
leaving tag consumption and native removal intact. The fresh target build and
strict aeronautics server passed (`build/sb-deferred-dismount-after-build.log`);
both clients passed blocked/clear location regression in `20261013-124036`.

That multiplayer run still failed the harness's eventual-clear assertion after
Shift. Its movement trace proves the native seated anchor already overlaps the
seat by 0.2875 before ordinary movement resumes. The maintained rejection
contract permits retaining that anchor and forbids remounting or inventing a
clear coordinate. The harness now observes actual native passenger removal and
requires a changed anchor to be clear, or the rejected anchor/pose to remain
unchanged. This corrects the test's unsupported guarantee of eventual recovery;
it does not claim that every rejected seat exit automatically escapes overlap.
The full extended multiplayer matrix remains open pending fresh execution.


### 2026-10-13 — local travel mode after native removal

The full two-client run `build/multiplayer/aeronautics/20261013-130225`
reproduced the persistent re-tracking failure with observations restricted to
actual local-player operations. The completed source transforms were continuous;
`SupportTransportResolver` found valid current transport, but preflight was
empty because the player's runtime mode remained `NATIVE_FALLBACK` after native
seat removal. `LivingGravityIntegration.openTravel` intentionally does not
rebuild a player's server-owned body on the client, and also omitted local mode
refresh. Nested `Entity.move` correctly refuses mode mutation in an open travel
operation, so the stale passenger mode survived subsequent falls and landings.
The client-player travel entry now invokes the existing `MovementModeIntegration`
owner before capture/preflight. It does not derive a new application or body.
`build/sb-client-mode-build.log` passes; fresh multiplayer acceptance is pending.

Diagnostic-only runs `20261013-125619` skipped earlier motion/seat phases and
passed source replacement plus 30 supported ticks on both clients. They are not
full-matrix passes. That run reached the incoming-wall fixture and exposed an
allocation-origin assumption; the fixture now aligns the completed native
collider with `RigidBodyHandle.teleport`, preserving native COM rebasing.
The `20261013-125945` crash was a test-observer mistake (querying a static item
support as a rigid identity), not an engine regression. Observation is now
restricted to the actual local player's runtime; the launcher also detects
fresh client crash reports without waiting for a native crash dialog to exit.


- **PASS (focused after-fix):** `20261013-130528` completes continuous walk,
  jump, mounted self/observer transforms, native Shift removal, new body/chunk/
  child objects and 30 consecutive supported ticks after re-tracking on both
  clients. It then fails the incoming fixture because its original fixed Z
  placement left one actor outside the wall after native assembly rebasing.
- **PASS (diagnostic scope):** `20261013-130802` uses actual completed native
  collider bounds to align wall and both stationary actors. Both clients see
  self and observer pushed by the native moving wall, verify GE collision
  ownership, and observe source removal. This skipped earlier phases and is
  not a complete multiplayer-matrix result.
- Temporary transport observers and diagnostic phase skipping were removed.
  The final harness retains matched server/client native source UUID, capture
  tick/domain, quaternion endpoints, character position/world velocity, support
  credit and contact evidence. Fresh build/API/JVM checks pass in
  `build/sb-sealed-build.log`; the six complete multiplayer runs are underway.


### 2026-10-13 — pending support momentum and the native deadzone

The final matrix's parent 0/100 ms cases passed (`20261013-130954`,
`20261013-131057`), but child run `20261013-131201` failed its unchanged 0.1-block
standing-drift threshold at frame 130. Matched velocity/credit records show the
native small-component deadzone erasing a world component while its support
credit survives. The earlier fix required current stable-support proof, which
is absent for outward velocity or a decelerating carrier even before travel
has released the momentum's provenance.

A focused real-Rapier regression reproduces this independently:
`build/sb-deadzone-release-before.log` fails with credited X velocity
0.000995635986 erased to zero. Numerical cleanup now preserves world velocity
when stable-support proof is unavailable and existing credit is still owned;
the subsequent movement owner retains authority to validate/release it.
No ground is fabricated, no thresholds are raised and no credit is cleared by
a numerical deadzone. The regression and complete gates are being rerun.


### 2026-10-13 — touching-band accumulation on a rising native platform

Full multiplayer runs `20261013-132159` (parent, 0 ms) and
`20261013-133350` (parent, 100 ms) fail the unchanged post-walk support assertion.
The latter retains per-frame native pose, contact, credit and actor samples:
GESecond's plot-local foot height goes from 128.999999908703 to
128.999999907130 before contact is lost and the rising floor contains the
stationary character. Complete source/pose evidence is present; this is not a
missing provider or stale locomotion-mode case. Diagnostic passing reruns are
retained but did not close this failure.

A fixed common regression fails on the prior implementation in
`build/sb-touching-band-before.log`: invariant-axis and enclosure clearance spent
the whole TOUCHING band even when the initial pair retained a positive gap, or
already had a shallower contact. Both now preserve only the measured shallow
starting overlap. A bounded moving-plane chord proof retains rotation/translation
correlation using the existing angular speed/acceleration bounds; it avoids
exhausting spatial subdivision on a valid near-tangent tilted face. No contact,
time, work or drift threshold is raised. A second regression covers inward
motion below the normal-entry classification threshold, which may not accumulate
into a deeper overlap over successive ticks. All common checks pass; the native
server and complete client matrix are being rerun on this correction.


The stricter sweep exposed a second boundary in the real physics-passenger
control: CCD returned successive contacts, but `ContactConstraintProjector`
allowed an inward input inside its 1e-9 candidate-validation tolerance to win
unconstrained. `sb-touching-actor-trace.log` records the legal captured body,
source, operation interval and repeated contacts ending at ADVANCE_BUMP_LIMIT.
`sb-contact-projection-before.log` independently fails a fixed affine-plane
regression. The unconstrained input now uses only the existing 1e-12 comparison
roundoff allowance; computed active-set candidates retain their numerical
validation tolerance. No public feasibility threshold, CCD bump count or work
budget was weakened. The existing continuous-slope control caught a zero-
roundoff draft and remains passing, rather than being removed. The physics
passenger uses a stable test UUID so this trace can be reproduced without
logging unrelated actors.

`build/sb-contact-projection-after.log` passes the primary build, all common
regressions, API/JVM controls and the strict Aeronautics server. Artifact SHA256:
`84B8122B1AC1FD800E4AEC929C701EBDE3728357E4D49ECBDD37D88AC4FF16C2`.
The unrelated verbose all-entity diagnostic run stopped earlier in the scheduler
fixture (`sb-touching-runtime-trace.log`, finite-replacement assertion); it is
retained as FAIL, not counted as a completed physics control. The scoped trace
and subsequent final gates are the relevant acceptance evidence.

The independent rigid-sweep timing probe is retained under
`build/rigid-sweep-comparison-20261013/`; it shows the correlated-tilt refinement
improvement and ordinary yaw-query overhead separately. See the measurements
report for every raw batch and its limits.


A subsequent parent run (`20261013-135318`) and scoped delayed trace
(`20261013-135751`) exposed the remaining finite-voxel seam. The trace records
one complete legal scene and floor contacts, then a spurious neighbouring voxel
side normal followed by SWEEP_QUERY_INDETERMINATE. The character's accepted
partial path was left behind the still-moving floor. `NativeWalkContactRegressionTest`
uses the captured native pose and body with sixteen tangent directions;
`sb-native-walk-common-before.log` fails, while the complete common suite in
`sb-native-walk-common-after.log` passes after the correction.

Actual rotating-rigid impact displacement now retains the existing
`CCD_CONVERGENCE_GAP` (1e-10 block) on its reported normal. This prevents a rounded
zero-gap endpoint from entering the next finite voxel's side. It is an
operation-local positional CCD separation, not a new velocity credit or native
reaction force; the velocity owner still uses native surface velocity. Static
contacts, non-impact clear motion, budgets and declared tolerances are unchanged.
The final runtime/multiplayer matrix is being repeated after this last change.
