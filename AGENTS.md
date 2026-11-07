# Agent working agreement and project handoff

## Current repository correction (2026-11-07)

This checkout is Thaumcraft Research Solver at https://github.com/LuigiSalvatore/f-thaumcraft-research. The Region Tick Accelerator sections below are copied historical context and do not apply. Retain the general working/documentation conventions. For this mod preserve client-only installation, normal server-authoritative packets, separate aspect provenance, complete-only plans, and bounded diagnostic logs. Target Minecraft 1.7.10, Forge 10.13.4.1614, and Thaumcraft 4.2.3.5 GTNH. Follow docs/RELEASING.md: Java 25 wrapper build, Java 8 output, matching metadata, annotated beta tag, source/docs commit and verified reobfuscated artifact on the intended remote. Current release is 1.3.1-beta.1; preceding published release is v1.3.0. See docs/RELEASE_NOTES.md for scope and runtime limitations. Never apply the tickaccel artifact names, server-only requirements, GregTech constraints, or unrelated repository identity below.

## Scope and portability

These instructions apply throughout this repository. This file records user-approved repository rules, working conventions, and explicit project context. It is not a transcript or an export of hidden memory, model configuration, credentials, tools, plugins, skills, or platform instructions. Those capabilities depend on the destination environment. Follow applicable higher-priority instructions and the user's current request.

To reuse in another repository, copy this file to its root as `AGENTS.md`. Keep the general working and documentation conventions. Adapt the project-specific sections, paths, build commands, release artifact, and repository identity before applying them to an unrelated project. Never publish another project's changes to the repository named in this handoff. Inspect destination instructions and existing work before editing; reconcile them with the user's intended scope. Transfer referenced project documentation as well when continuing this same project, or explicitly record that it is unavailable.

## Working conventions

- Treat requests to implement or fix something as instructions to carry out the work. Continue through implementation, documentation, appropriate verification, and required delivery; do not stop at a proposal when execution is authorized.
- Use reasonable assumptions for routine reversible decisions. Ask only when missing information materially affects correctness, scope, authorization, or an irreversible action. Continue independent work while awaiting clarification.
- Preserve unrelated work. Inspect Git status and relevant diffs before editing. Do not discard user changes, rewrite published history, force-push, or bypass repository protections.
- Keep changes focused on the request. Do not turn a specific issue into unrelated refactoring or dependency upgrades.
- Read applicable repository instructions and relevant source/docs before changing behavior. Prefer `rg` for searches; use available alternatives when it is absent.
- Batch independent reads/checks when useful; keep dependent edits and release steps sequential. Do not delegate to sub-agents unless the user or an applicable skill explicitly requests delegation.
- Use available connected tools when they fit the task. Never assume tools or credentials from a previous session are available. Do not send messages to third parties without explicit authorization.
- Apply relevant installed skills according to their instructions, announce their use, and report missing capabilities honestly. This file does not install skills.
- Start substantial work with a concise progress update. During ongoing work, explain meaningful findings, assumptions, blockers, and next steps; avoid long silent stretches.
- Write plainly and concisely. Lead final responses with the outcome, then relevant checks, artifact/release links, and remaining limitations. Distinguish completed work from proposed work. Do not claim commands, tests, commits, uploads, or runtime checks that were not performed.
- If an approval or access restriction blocks delivery, explain the specific action and reason, finish unaffected work, and report the remaining blocker. Do not treat a previous release as evidence of current access.

## Documentation and durable context

- Keep persistent project context in tracked documentation rather than relying on chat history. Record accepted decisions, requirements, unresolved questions, deferred issues, checks actually performed, and remaining work.
- On a new session, inspect `AGENTS.md`, `README.md`, `docs/PLAN.md`, `docs/FEATURES.md`, `docs/TREE.md`, `docs/RELEASING.md`, and the current issue record as relevant. Read specialized documents for the affected area rather than loading every historical record.
- Update documentation in the same change as any implementation, behavior, command, configuration, compatibility, dependency, or build change.
- Keep `docs/FEATURES.md` accurate. Distinguish planned, implemented, and verified features; never describe planned behavior as available.
- Keep `docs/TREE.md` current whenever files or directories are added, moved, renamed, or removed. Explain the purpose of each tracked source directory and documentation file. Exclude generated files and dependency caches from the tree.
- Keep `docs/PLAN.md` current as implementation decisions, milestones, requirements, and open questions change.
- Keep `README.md` current with project status and, once available, build, installation, configuration, and usage instructions.
- Record checks actually performed and any remaining compatibility limitations. Do not claim runtime compatibility without testing the relevant environment. Label previous sessions' results as historical until rerun.
- Never put secrets, tokens, or private account configuration in memory documents or commits.

## Region Tick Accelerator: compatibility and behavior rules

These rules are specific to this mod and its continuations.

- Preserve the server-only installation option: clients must not need this mod to connect. Client keybind functionality is optional and must use the server commands, retaining their permissions and validation.
- Isolate client-only classes so dedicated servers can load the mod without resolving client classes.
- Target Minecraft Forge 1.7.10 and the pinned GTNH 2.9.0 / GT5-Unofficial 5.09.54.265 integration. GregTech remains optional. Do not silently broaden version compatibility claims.
- Keep operator permission checks and server-side validation authoritative. Optional GUIs must not bypass ordinary command permissions, validation, ownership, revisions, or bounded transfers.
- Accelerate through normal update callbacks. Do not inject production/resources, change global tick rate, force-load chunks, or equate requested update counts with measured output speed.
- Highest eligible overlapping speed wins; sources do not multiply or add. Native 1x means zero extra updates, with normal ticking continuing.
- Preserve explicit per-member random-tick opt-in and independent tile/random modes. Do not implicitly enable random ticks through regions or player areas.
- Preserve bounded work, loaded-target checks, identity validation, persistence migration, and conservative cleanup. Do not silently expand saved memberships or invent live network topology from presentation groups.
- Preserve disabled-list state and random opt-in in browser speed edits. Legacy commands may have different documented enabling semantics; inspect them before reusing helpers.
- Global stops persist until explicit resume. Do not introduce automatic resume as a side effect of list toggles or player events.

## Region Tick Accelerator: build and delivery

- For every implementation change, automatically increment the beta number (even when the user does not specify a version), produce a distributable reobfuscated `.jar`, commit the matching source/docs, and publish a GitHub prerelease when access permits. Use beta versions until instructed otherwise; follow `docs/RELEASING.md`. Report publication blockers explicitly.
- Determine the next version from actual source and existing remote tags; do not hardcode a next beta from this handoff. Keep `extra["modVersion"]` in `build.gradle.kts` and `version` in `gradle.properties` equal.
- Run the pinned Gradle wrapper with Java 25; output targets Java 8 bytecode. Use `./gradlew check assemble`; run `./gradlew spotlessApply` first when formatting needs correction.
- Distribute `build/libs/tickaccel-<version>.jar`, never the development or sources JAR. Inspect metadata, bytecode target, reobfuscation, expected classes, and common/client isolation; record SHA-256.
- Commit matching source/docs, create an annotated `v<version>` tag, and publish to the verified intended remote. Inspect the prerelease and uploaded artifact/digest. Report CI separately from local checks and manual publication.
- Never commit generated outputs/caches. Never print credentials. Check actual authentication/network access before diagnosing a publication failure.
- Documentation-only maintenance does not require a version increment, rebuild, retag, or replacement release. Keep existing artifacts and tags unchanged unless explicitly requested; follow the documentation-only section of `docs/RELEASING.md`.

## Explicit project handoff — recorded 2026-11-06

This section is historical context, not proof of the current checkout or remote state. Revalidate before acting.

- Project: Region Tick Accelerator; recorded repository: `https://github.com/LuigiSalvatore/region-tick-accelerator`.
- Recorded source version: `0.1.0-beta.18`. README links its release. This handoff did not independently verify remote publication.
- Latest issue file: `issues/issues.md`. Its three GUI requests are recorded as implemented in beta.18: main-menu Resume accelerations, inline saved-list On/Off controls, and font-measured group-arrow click areas.
- Historical beta.18 checks in the repository: 120 local unit tests, formatting, Checkstyle, compilation, reobfuscated packaging, and `git diff --check` passed. They are not checks performed by reading this file.
- Minecraft/GTNH runtime loading, dedicated-server and unmodified-client behavior, GUI rendering/input, throughput, resource conservation, and pack compatibility remain unverified. User target is GTNH 2.9.0 with server Java 25 and the pack launcher/JVM arguments.
- Earlier missing Et Futurum entry issue remains deferred. Do not claim it was fixed or change its identity/cleanup paths as an incidental GUI fix.
- Future work includes performance profiling/cached discovery, dynamic network membership, additional mod adapters, and pack-specific runtime acceptance. Planned features must remain labeled unavailable until implemented.
- `docs/SETTINGS.md`, `docs/GLOBAL_CONTROLS.md`, `docs/ACCELERATION_HUD.md`, and `docs/GREGTECH.md` describe specialized behavior. `docs/TESTING.md` contains runtime acceptance steps. `docs/ISSUE_PLAN.md` contains prior issue designs; `docs/RELEASE_NOTES.md` contains release notes. `docs/TREE.md` maps source responsibilities.

## Maintaining this agreement

Update this file when the user changes durable working rules or when its handoff context becomes misleading. Keep detailed feature histories in the linked documentation. For a different project, replace the mod-specific sections with that project's actual constraints and verified state rather than carrying over Minecraft assumptions.

AGENTS.md loading reference: https://learn.chatgpt.com/docs/agent-configuration/agents-md
