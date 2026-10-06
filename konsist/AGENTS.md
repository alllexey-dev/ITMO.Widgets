# konsist

## Owns
- `src/test/kotlin/.../architecture/`: the architecture rules over the sources of `app/` and every shared module,
  one `*RulesTest` per topic over the shared `ArchitectureScope` and `TypeNames`.
- `src/test/resources/architecture/ratchet/<owner>.txt`: today's known violations, one file per feature, `core` or
  `app`; lines only go away.

## Depends on
- A plain Kotlin JVM test module: JUnit, Konsist and the test-runtime `kotlin-compiler-embeddable` pin
  (ADR 0018) that lets Konsist parse Kotlin 2.4 syntax.
- Never `:app` or a shared module on its classpath: Konsist reads source files, and `build.gradle.kts` declares
  them as task inputs so a source-only edit reruns the suite.

## Verify
`scripts/verify.sh run -- :konsist:test`; `scripts/verify.sh quick` runs it too.

## Hot files
- `ArchitectureScope.kt` and `*RulesTest.kt`: lane L06; another lane hands in a rule.
- `ratchet/<owner>.txt`: the lane owning that feature deletes its fixed lines; nobody adds one.

## Docs
- [Boundary enforcement](../docs/architecture.md#boundary-enforcement) and [layers](../docs/architecture.md#layers).
- ADR [0018](../docs/decisions/0018-module-graph-and-toolchain.md) (module graph, the Konsist pin).
