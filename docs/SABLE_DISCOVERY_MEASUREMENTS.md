# Create discovery capture measurements, 2026-10-09

This is a capture-only comparison, not full physics or multiplayer acceptance.
Both variants use the corrected working tree based on
`2edc4269eb7c854e43babc2eb3ed8b94b895d864`. The baseline is an isolated source
copy with only `CreateRigidCollisionProvider.candidates` replaced by a bounded
full-source scan. It is not the historical release baseline. The exact variant
patch, source hash, raw CSVs, stdout and machine details are in
`build/create-discovery-comparison-20261009/`.

Runtime: NeoForge 21.1.256, Sable 2.0.6, Create 6.0.10, Oracle Java 21.0.11,
Windows 11, AMD Ryzen 9 9950X (16 cores / 32 threads). Both runs use
`-Xbatch -Xms1G -Xmx2G`. The harness performs 40 warmup frames then 160 measured
frames, with 16 distinct operation subjects per frame. Every frame changes all
contraption angles, so rebuilding the index is included. Sources are registered
with the actual provider but are not independently ticked in the world. Other
strict runtime controls separately exercise native assembly and physics.

| Scenario | Full scan median / p95 (µs) | Indexed median / p95 (µs) | Full scan / indexed allocated bytes | Full scan / indexed trajectory reads |
| --- | --- | --- | --- | --- |
| Empty | 4.3 / 4.8 | 7.5 / 9.0 | 1,198,080 / 1,649,664 | 0 / 0 |
| 64 distant sources | 773.8 / 937.1 | 58.9 / 673.2 | 388,290,560 / 36,130,320 | 163,840 / 10,240 |
| 1 near + 63 distant | 914.4 / 1,574.8 | 135.4 / 856.6 | 411,228,800 / 63,330,704 | 176,640 / 15,520 |
| 16 near sources | 506.9 / 627.7 | 534.7 / 597.2 | 240,756,736 / 279,841,280 | 46,080 / 48,640 |
| Two blocks spanning 512 × 512 | 75.4 / 83.8 | 79.2 / 87.5 | 30,114,784 / 55,142,256 | 5,280 / 5,440 |

Allocation and trajectory reads are totals over the measured frames; timing is
per frame. Both variants emit identical primitive totals: 0, 0, 2,560, 40,960,
and 2,560 respectively. The index reduces repeated distant-source work while
adding measurable overhead to empty, dense-local and sparse-large-bound cases.
These single-run distributions do not establish whole-game speedups or stable
cross-machine tail latency. No budget was raised or correctness work removed.
The host was not exclusively reserved: unrelated Java applications were present.
The runs were sequential with identical harness controls, but ambient host load
was not controlled, so especially the timing tails should not be overinterpreted.

Commands from the repository root, with `JAVA_HOME=C:/Program Files/Java/jdk-21.0.11`:

```powershell
./targets/neoforge-1.21.1/gradlew.bat -p targets/neoforge-1.21.1 build sableCompatibilityVerification -PcompatibilityProfile=create -PcreateDiscoveryMeasurements=true --offline --console plain --no-daemon
./targets/neoforge-1.21.1/gradlew.bat -p build/create-discovery-comparison-20261009/full-scan-source/targets/neoforge-1.21.1 sableCompatibilityVerification -PcompatibilityProfile=create -PcreateDiscoveryMeasurements=true --offline --console plain --no-daemon
```

Both strict gates passed, including same-tick raw relocation and parent-bound
invalidation. The isolated baseline reused the already accepted EULA directory;
the EULA was not changed. Its build file changes only that directory's absolute
path in addition to the source variant above. The original working tree remains
the indexed implementation. Full-stack mass/FIELD sampling, high-substep fleets,
environment capture costs and real multiplayer workloads still need measurement.


## Native physics/FIELD and capture continuation, 2026-10-13

Raw artifacts and the exact source manifest/variant patch are in
`build/native-workload-comparison-20261013/`. This again compares an isolated
bounded full-source scan with the indexed implementation from the same corrected
working tree. Both strict full-stack Aeronautics server gates passed. The native
workload adds a real powered bearing child and Sable/Rapier parent, a complete
zero-gravity FIELD, 64 distant Create publications and 16 independent character
collision/environment capture domains per frame. It runs 40 warmup and 160
measured frames at each of 2 and 8 native physics substeps. Child controller and
physics ticks, mass/FIELD sampling, source invalidation/index rebuild and query
work are inside the timer. Source construction and chunk loading are outside.

| Native workload | Full scan median / p95 (ms) | Indexed median / p95 (ms) | Full scan / indexed allocated bytes | FIELD queries / emitted primitives (both) |
| --- | --- | --- | --- | --- |
| 2 substeps | 2.895 / 40.389 | 1.250 / 42.823 | 995,094,584 / 259,665,888 | 960 / 7,680 |
| 8 substeps | 3.734 / 11.428 | 1.996 / 7.941 | 1,369,563,920 / 671,333,880 | 3,840 / 7,680 |

Allocation is measured on the owning server thread and excludes native Rapier
allocations and other threads. Both variants preserve identical counted work;
8 substeps perform four times as many FIELD queries. The 2-substep indexed p95
regresses despite its lower median and allocation. No samples or tails were
removed. This measures the specified server physics plus captured domains, not
full character travel, graphics, multiplayer latency or native balloon gameplay.
The full mod stack is loaded; its independent balloon/floating correctness
controls run separately. Timing is a single sequential pair on a non-exclusive
host, using the same JDK and `-Xbatch -Xms1G -Xmx2G` controls as above.

The capture-only matrix was also rerun. Raw current CSVs retain the empty and
sparse-bound overhead of indexing; no universal speedup is claimed. These results
precede the subsequent supported-velocity deadzone corrections, predicting-client
locomotion-mode refresh and deferred Create dismount validation. Those paths are
outside this measured server capture workload. The retained manifest identifies
the measured source snapshot; these numbers do not measure the complete final
client artifact.

Commands used the primary wrapper and Java 21.0.11 with
`-PcompatibilityProfile=aeronautics -PcreateDiscoveryMeasurements=true`, unique
`-PcompatibilityWorld` values, `--offline --console plain --no-daemon`, and the
pinned local Aeronautics Bundled 1.3.2 release JAR. Indexed command:
`build sableCompatibilityVerification prepareCompatibilityMultiplayer` against
the primary target. Full-scan command: `sableCompatibilityVerification` against
`build/native-workload-comparison-20261013/full-scan-source/targets/neoforge-1.21.1`.
The baseline reuses the already accepted verification directory; it does not
change EULA acceptance. Source hashes, exact discovery-only code change and
absolute run-directory adjustment are retained beside both stdout logs.


## Rigid sweep correction, 2026-10-13

`build/rigid-sweep-comparison-20261013/` retains a separate before/after probe
of the touching-band correction and moving-plane proof. The baseline restores
and hash-checks the three changed common sources against multiplayer snapshot
`20261013-133350`; other common sources and the harness are identical. Each
variant uses Java 21.0.11, Java 17 source compatibility,
`-Xbatch -Xms256m -Xmx512m`, 2,000 warmup queries and 100 batches of 100 queries
per case. Both variants assert the same clear/contact outcome on these valid
inputs. The new failing-before correctness cases are not timed as equivalent
work. Command: `python build/measure-sweep-review.py`.

| OBB sweep | Before / after median (microseconds/query) | Before / after p95 (microseconds/query) | Before / after allocated bytes/query |
| --- | --- | --- | --- |
| Yaw, clear | 0.416 / 0.425 | 1.587 / 2.149 | 2,631 / 3,157 |
| Yaw, entering | 1.064 / 1.766 | 9.375 / 16.391 | 4,944 / 7,675 |
| Native correlated tiny tilt | 281.010 / 2.084 | 288.544 / 3.887 | 1,021,644 / 10,419 |

The moving-plane proof removes repeated spatial refinement for the correlated
tilt case. Ordinary clear and entering cases have extra cost; those regressions
are retained. Allocation covers the measuring Java thread only. This is one
sequential pair on a non-exclusive host, not a whole-game or complete character
travel benchmark. Raw batches, harness, exact source copies/hashes and VM
metadata remain beside the summary. The earlier native capture matrix does not
measure these sweep paths and cannot substitute for this probe.
