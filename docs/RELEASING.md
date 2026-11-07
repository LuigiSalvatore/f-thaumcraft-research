# Release procedure

Repository: https://github.com/LuigiSalvatore/f-thaumcraft-research. Keep prereleases until instructed otherwise. Fetch actual remote tags before choosing a version. Update the Java VERSION constant, mcmod.info, and README together. The GTNH convention derives archive names from Git tags; build after creating the annotated version tag on the release commit.

Commit all required Java sources, resources, build scripts, and documentation. Exclude build outputs, dependency caches, local configuration, and dist JARs. Preserve remote commits and unrelated work. Push the matching source commit and annotated tag without force.

Use Java 25 and the pinned wrapper:

```
./gradlew -I scripts/verify.gradle check assemble solverDiagnostics --no-configuration-cache
```

The diagnostics task is supplied by the init script and runs the standalone deterministic solver checks. Existing Spotless and Checkstyle settings disable those tools. Standard test currently has no test sources. Only upload the reobfuscated build/libs JAR, never the -dev or -sources JAR. Inspect mcmod.info, expected classes, Java 8 major version 52, and SHA-256; verify GitHub's uploaded asset digest. Release notes must compare against the last published release and distinguish local checks from runtime acceptance.

If an IDE locks generated outputs, build from a fresh checkout; do not kill the user's running game or IDE. Use `gh release create` with an explicit repository, --verify-tag, --prerelease, and --notes-file docs/RELEASE_NOTES.md. The older scripts/release.ps1 helper does not implement this complete procedure.

Documentation-only changes do not require a version bump or a new binary release.
