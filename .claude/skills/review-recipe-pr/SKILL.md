---
name: review-recipe-pr
description: Review an OpenRewrite recipe change in rewrite-spring against the maintainers' conventions. Use it to self-review your own branch before opening a pull request, or when asked to review, polish or tidy up a contributor's recipe PR. Covers Java recipes, declarative YAML under META-INF/rewrite, and RewriteTest tests. Produces fixes on the branch, plus review comments when reviewing someone else's PR.
---

# Review a recipe PR

Distilled from the maintainers' review comments and polish commits on this repository's pull requests. PR numbers point to examples.

What we want from a PR here:
- **Small.** One migration step per PR. Small PRs get reviewed and merged quickly; large ones wait. Split unrelated or unfinished work into separate PRs (#765, #913).
- **Common.** It migrates a pattern people actually have, as seen in the migration guide, the Spring Guides or real projects.
- **Well tested.** Before and after sources for the main shape, and a no-change test for what the recipe should leave alone.
- **Simple.** Handle the common shapes well, and leave the rest unchanged. A recipe that makes no change on an unusual input is better than one that grows to cover every variant.

## Modes
- **Self-review, before opening a PR.** Run it on your own branch against `origin/main`. Fix everything directly, and answer the section 1 questions in the PR description.
- **Reviewing a contributor's PR.** Follow all the steps, then deliver as described in section 7. Many contributors here are new to OpenRewrite, so explain why, and keep requests in proportion to the change.

## Triage by PR shape
- **YAML-only PR** (property, type, dependency or version changes): sections 1–3, "test what ships" in 5, and 6.
- **Java recipe PR:** all sections.

## 0. Setup
1. **Read the prior feedback** so you don't repeat what has been said or done (when reviewing):
   - `gh pr view <n> --comments`
   - `gh api repos/openrewrite/rewrite-spring/pulls/<n>/comments` and the reviews
   - any linked issue

   Also check `gh pr checks <n>`, and whether maintainers can push to the fork (`maintainerCanModify`).
2. **Read the change:** `gh pr view <n> --json title,body,author,files,commits,maintainerCanModify` and `gh pr diff <n>`, or `git diff origin/main...` when self-reviewing. Read the upstream migration guide or release notes that justify it.
3. **When reviewing, check out the PR** with `gh pr checkout <n>`. Merge `main` in if the branch is behind or conflicting. Never force-push over the contributor's commits.
4. **Run the tooling.** Any diff it produces becomes a commit, not a review comment.
   - **Tests:** `./gradlew test --tests '<RecipeTest>'`. `rewriteRun` fails by default (`RecipeQualityValidation`) when a recipe matches by spelling rather than type, builds trees the parser wouldn't, formats more than it changed, needs a second cycle, and more. Fix the recipe rather than opting a test out.
   - **Best practices** (optional, with the Moderne CLI): `mod build . && mod run . --recipe org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices`. Apply only the hunks for the PR's files, with `git apply --include='*<Recipe>*' <fix.patch>`.
   - **`recipes.csv`:** `./gradlew recipeCsvGenerate`, after merging `main`. Commit only this PR's rows and its composites' counts. Never hand-edit the file.
   - **License headers:** `./gradlew licenseFormat`.
   - **Type tables:** if the PR adds `parserClasspath` or `testParserClasspath` entries, regenerate with `./gradlew createTypeTable` or `createTestTypeTable`, and splice in only the new rows, since version ranges cause unrelated drift.
5. **Run the built-in reviews.** `/code-review --fix` for correctness bugs and `/simplify` for cleanups. Review their changes as you would a contributor's before committing them.

## 1. Is this the right change?
Ask these questions first. When reviewing, these are comments, because the contributor has to decide.
- **Do we already have it?** Search this repo, and the [recipe catalog](https://docs.openrewrite.org/recipes), for a recipe with the same intent. Also check for an open PR doing the same (#973). If you suspect overlap with work outside this repository, don't discuss it on the PR: raise it with the user and let a maintainer decide.
- **Is it common?** Does the pattern occur in practice, or is it a convention specific to one codebase (#1019)? Skip niche or unlikely shapes.
- **Could it be declarative?** Many migrations are just `ChangeType`, `ChangePackage`, `ChangeMethodName`, `ChangeSpringPropertyKey` or `ChangeDependency`. Prefer a few lines of YAML over a Java recipe, and reuse existing recipes over new ones (#805, #822).
- **Is it verified upstream?** Does the target type, property or version exist in the release being migrated to? Link the migration guide or commit.
- **Is it complete enough to ship?** A migration that does only part of the job, or a composite that doesn't do much yet, confuses users and reflects on the tooling (#765, #860). Changing a type without the dependency that provides it leaves code that doesn't compile.
- **Does it do no harm?** The recipe should:
  - change only what it targets
  - make no change when it isn't sure, e.g. two variants that don't map onto the one replacement (#727)
  - respect explicit user configuration, e.g. `=false`
  - map values when the replacement property takes different ones, rather than only renaming the key (#765)
- **Is it opinionated?** Best-practice recipes go in `best-practices.yml` and are not included in upgrade composites, so an upgrade doesn't bring optional changes (#877, #1154).

## 2. Placement and wiring
- **Reachable:** reference every new recipe from a composite, typically the upgrade for the version that removes or replaces the API (#808, #922). Otherwise folks have to find and run it on their own.
- **Right composite:** put a library migration in that library's composite, and include it from the Spring Boot upgrade that brings the new version (#752). Chain the previous version's upgrade instead of copying its entries.
- **No duplicates:** don't add a recipe that is already included through another composite (#877), or a second recipe with the same name in another package (#893).
- **Order:** specific type changes before a broad `ChangePackage` of the same package, and text-based recipes last.
- **Naming:** verb-first names in the matching `org.openrewrite.java.spring.<area>` package, following the existing version suffixes (`UpgradeSpringBoot_4_0`).

## 3. Preconditions
- Use the narrowest cheap check that implies the recipe can act: `UsesMethod` over `UsesType` over nothing.
- Preconditions apply per file. A `UsesType` precondition never matches a `pom.xml` or `build.gradle` (#856).
- Don't add a dependency or BOM unconditionally: require a dependency that shows it's relevant (#980).
- Don't gate on the old version when matching on the old API is enough. Recipes should be no-ops on code that's already migrated (#1189).
- `Singleton` goes only on the top recipe of a YAML file (#986).

## 4. Java recipe code
Most of this is mechanical, so fix it directly:
- **Java 8:** main sources compile for Java 8, so no `List.of`, `var` or text blocks (#903).
- **Types over strings:** `MethodMatcher`, `TypeMatcher` and `AnnotationMatcher` instead of comparing simple names. Put the matched attribute in the matcher pattern, e.g. `nativeQuery = true` (#752).
- **Constants:** FQNs and matchers as `private static final` fields. Build `JavaTemplate` inline.
- **`JavaTemplate`:** pass existing LST elements as `#{any()}` arguments instead of calling `toString()`, which loses type information and forces `contextSensitive()` (#727).
- **No options for one behaviour:** if only one value makes sense, drop the option and make it the default (#752).
- **Structure:**
  - Inline single-use helpers and fields (#821, #1189).
  - Prefer loops over streams, and `ListUtils` over copying lists (#1189, #858).
  - Use early returns rather than `else` after `return` (#752).
  - Avoid passing trees through cursor messages (#1189).
- **Scanning recipes:** key accumulators by `JavaProject` so multi-module projects work (#563, #772). Ask whether a precondition would avoid the scan.
- **Property files:** reuse `SpringConfigFiles` rather than matching `application*` names, so profile-specific files are covered too (#1098, #727).
- **Probe it.** For each guard or bail-out, write a throwaway test that should reach it. A branch you can't reach is a bug or dead code.

## 5. Tests
- **Before and after:** each changing test has a before and an after text block (#885). A test with one text block asserts that nothing changes.
- **Naming:** `FooTest` runs `Foo` in `defaults()`. Remove a per-test `spec.recipe` identical to `defaults()`.
- **Test what ships:** load YAML recipes with `recipeFromResources` rather than rebuilding them in the test. A pure YAML composite of tested recipes doesn't need its own test of every step (#986).
- **Minimal inputs:** no unused imports, no `public` or `package` lines unless needed, readable names. Each test has one reason to pass.
- **Realistic inputs:** "before" sources compile against the version being migrated from. Prefer type tables with `classpathFromResources` over `classpath(...)`: add a `testParserClasspath` entry with a major-version range like `7.+` (#1182), and regenerate with `./gradlew createTestTypeTable`. Don't use `TypeValidation.none()`.
- **Coverage, in proportion:**
  - the common shape
  - an already-migrated input that is left unchanged
  - Maven and Gradle for build changes
  - Kotlin where it's common, grouped in a `@Nested class Kotlin` (#874)

  Group no-change tests in a `@Nested class NoChange`. Don't ask for tests of niche shapes the recipe doesn't claim to handle.
- **Expected output must be right,** not today's behaviour. Odd spacing in an expectation points to a recipe bug (#1061).
- **Leftovers:** no commented-out tests, stale `@Disabled`, or TODOs; open an issue or a separate draft PR instead (#913). Reference issues with `@Issue`.

## 6. Metadata
- **Display name:** sentence case, with correct product names ("Spring Boot").
- **Description:** backticks around types and properties; states what changes and what doesn't; ends with a period; doesn't over-claim.
- **YAML:** no options set to their default value; `description: >-` for long descriptions.
- **Comments:** keep only a non-obvious "why". No javadoc that repeats the description.
- **PR description:** link the issue and the upstream migration guide or commit, so a reviewer doesn't have to dig.
- **Hygiene:** no stray files (`rewrite.yml`, scripts), and the license header on new files.

## 7. Deliver
**When self-reviewing,** commit the fixes, make sure the PR description answers section 1, and open the PR.

**When reviewing, choose how to deliver each finding:**
- **Push polish** for anything mechanical, when the fork allows maintainer edits.
  - One concern per commit, with an imperative headline and backticked APIs, e.g. "Use `MethodMatcher`".
  - Run the tests before pushing.
  - No AI attribution.
- **Comment** when the contributor has to decide, or when you can't push. Use ` ```suggestion ` blocks for one-line fixes.
- **Request changes** only for harm or a confirmed bug, with a reproducer test. Otherwise leave a comment-only review.
- **Never approve or merge.** Tell the user whether the PR looks ready, and list the open questions.
- **Order:** post line comments before pushing polish, so line references stay valid.

**Tone.**
- Phrase suggestions as questions: "Could we perhaps…?", "Would it make sense to…?".
- State the consequence for users.
- Hedge where unverified ("I think").
- After pushing polish, post one summary comment listing what changed and why, so it helps with the next PR.

**Escalate rather than patch locally.** If the root cause is in rewrite core or another recipe module, say so and link or open the upstream issue (#853).
