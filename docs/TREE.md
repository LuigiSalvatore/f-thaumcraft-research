# Repository map

- `src/main/java/com/luigi/thaumcraft/researchsolver/`: mod entry point/configuration, GUI overlay and rendering, asynchronous solver snapshots, resource-aware search, trace logging, tool/cursor controllers, execution state machine, and development diagnostics.
- `src/main/resources/`: mod metadata and META-INF access transformers.
- `gradle/`: wrapper and pinned Java daemon configuration. Root build.gradle, settings.gradle, dependencies.gradle, and gradle.properties configure the GTNH build.
- `scripts/release.ps1`: legacy release helper; `scripts/verify.gradle`: standalone solver diagnostic task.
- `issues/ISSUES.md`: current reports, investigation, and checks; `issues/image.png` and `image-1.png`: reported puzzle screenshots.
- `PLAN.MD`: original design, historical implementation analysis, and algorithm references.
- `docs/PLAN.md`: current release scope and next acceptance steps.
- `docs/FEATURES.md`: implemented, locally verified, and planned features.
- `docs/RELEASING.md`: build, versioning, packaging, and publication procedure.
- `docs/RELEASE_NOTES.md`: changes since the last published release and known limitations.
- `README.md`: installation, configuration, behavior, and build overview.
- `AGENTS.md`: repository working agreement and project handoff.

Generated build outputs, game runtime directories, JARs in dist, and caches are excluded.
