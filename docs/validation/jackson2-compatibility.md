# Jackson 2 retention validation

Spring Boot 4 supports its deprecated `spring-boot-jackson2` module and the
`spring.jackson2` namespace. Keeping the Jackson 2 library is different from
`spring.jackson.use-jackson2-defaults`, which configures a Jackson 3 mapper.
See the [Boot migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#jackson-2-compatibility).

The prototype separates Boot and Framework migration phases from Jackson
adoption. The standard migration keeps the same leaf recipes and options in the
same order. `UpgradeSpringBoot_4_0RetainingJackson2` instead retains Jackson 2
source APIs, relocates Boot compatibility APIs, moves existing Jackson properties,
adds the compatibility dependency, and sets HTTP converter/codec preferences in
existing default application configuration. Its Maven generator variants keep
Jackson 2 generation while updating Boot/Jakarta configuration. There is no new
option on the standard Boot migration.

`azure-jackson2.yml` is an experimental composition, outside the recipe catalog.
It requires the new `UsesExternalJackson2Api` and
`DoesNotUseExternalJackson2Api` recipes from
[rewrite-jackson #187](https://github.com/openrewrite/rewrite-jackson/pull/187).
The automatic policy retains Jackson 2 across the repository when a resolved,
fixed third-party signature requires it. Otherwise it selects standard Jackson 3
adoption. This repository scope keeps source and build policy together; it does
not attempt an unproven per-path or per-module split. Source-owned APIs, shared
annotations, and call-site inferred generic signatures do not trigger retention.
Unresolved calls, reflection, custom mapper wiring, and wire-format compatibility
still require review of the impact-analysis table. Absence of a detected boundary
is not proof that every runtime integration supports Jackson 3.

The Azure validation composition also upgrades legacy Azure starter/BOM
coordinates to Spring Cloud Azure 7.4.0, the documented Boot 4 line. This is
separate from the general Jackson retention policy. See Microsoft's
[version guidance](https://learn.microsoft.com/en-us/azure/developer/java/spring-framework/developer-guide-overview)
and [starter migration table](https://github.com/microsoft/azure-spring-boot).

## OSS evidence, 2026-11-01

Five public repositories were run together in Moderne:

| Repository | Evidence | Automatic branch |
| --- | --- | --- |
| Azure/cloud-scale-data-for-devs-guide | Cosmos `handleChanges(Consumer<List<JsonNode>>)` | Retain Jackson 2 |
| inversion-api/inversion-engine | Swagger `Json.mapper()` returns Jackson 2 `ObjectMapper` | Retain Jackson 2 |
| Washingtonwei/hogwarts-artifacts-online | Azure dependencies, no fixed third-party Jackson 2 API | Adopt Jackson 3 |
| microsoft/azure-maven-plugins | Azure `JacksonAdapter` callback takes Jackson 2 mappers; no Boot application | No Boot change |
| microsoft/kafka-connect-cosmosdb | Cosmos change-feed callback; no Boot application | No Boot change |

Automatic-policy changeset: `20261101191206-utZgV`. All five runs completed;
three repositories changed. Recipe result rows and patches confirmed the two
retention branches and the control's Jackson 3 imports. No caller-selected
Jackson version was supplied.

The preceding explicit-retention plus Azure-alignment changeset,
`20261101190248-6FmKJ`, compiled all three Azure guide build partitions, including
test sources. Its tests stop on the same missing `AZURE_COSMOS_URI` environment
variable as the original baseline. No Azure runtime/serialization equivalence is
claimed. The other Boot examples expose separate migration gaps: a custom
Security authorization override and obsolete Gradle archive `classifier` syntax.

The runs exposed an immutable-list YAML merge bug, fixed separately in
[rewrite #9525](https://github.com/openrewrite/rewrite/pull/9525). Experimental
bundles included that fixed utility; it is not vendored into rewrite-spring.

## Remaining production work

This is a draft compatibility path and laboratory routing composition; the
standard public Boot entry point has not been switched to automatic routing.
The detector dependency and core fix need to be available together before that
wiring can ship. Applications without an existing default configuration file
need a module-aware way to generate mapper preferences. Gradle generator
configuration and custom converter/bean arrangements need additional fixtures.
A mixed Jackson 2/3 split requires module dependency and exposed-API closure,
rather than path-based import rewriting.
