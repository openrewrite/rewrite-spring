<p align="center">
  <a href="https://docs.openrewrite.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss-dark.svg">
      <source media="(prefers-color-scheme: light)" srcset="https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss-light.svg">
      <img alt="OpenRewrite Logo" src="https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss-light.svg" width='600px'>
    </picture>
  </a>
</p>

<div align="center">
  <h1>rewrite-spring</h1>
</div>

<div align="center">

<!-- Keep the gap above this line, otherwise they won't render correctly! -->
[![ci](https://codegenomeproject.org/ci/modules/openrewrite/rewrite-spring/badge.svg)](https://codegenomeproject.org/ci/modules/openrewrite/rewrite-spring/builds/latest)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://community.develocity.cloud/scans)
[![Contributing Guide](https://img.shields.io/badge/Contributing-Guide-informational)](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md)
</div>

## What is this?

This project contains a series of Rewrite recipes and visitors to automatically apply best practices in Java Spring Boot applications.

Browse [a selection of recipes available through this module in the recipe catalog](https://docs.openrewrite.org/recipes/java/spring).

## How to use?

See the full documentation at [docs.openrewrite.org](https://docs.openrewrite.org/).

## Spring Boot upgrade recipes

Each recipe migrates a project to the listed Spring Boot version from the preceding one (2.0 migrates from Spring Boot 1.x).

| Spring Boot version | Recipe |
|---------------------|--------|
| 2.0 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0` |
| 2.1 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_1` |
| 2.2 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_2` |
| 2.3 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_3` |
| 2.4 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_4` |
| 2.5 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_5` |
| 2.6 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_6` |
| 2.7 | `org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7` |
| 3.0 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0` |
| 3.1 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1` |
| 3.2 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2` |
| 3.3 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3` |
| 3.4 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_4` |
| 3.5 | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5` |
| 4.0 | `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` |
| 4.1 | `org.openrewrite.java.spring.boot4.SpringBootProperties_4_1` (configuration properties only) |

## How to generate Spring Boot configuration properties replacement recipes?

1. Run [GeneratePropertiesMigratorConfiguration.java](src/test/java/org/openrewrite/java/spring/internal/GeneratePropertiesMigratorConfiguration.java)
2. Commit & push changes.
3. Repeat periodically as new minor versions of Spring Boot are released.

## Why do artifact scanners detect vulnerabilities in recipe artifacts/JARs?

In order to modernize and upgrade old or vulnerable code, some OpenRewrite recipe modules bundle copies of old libraries. Libraries bundled into recipe modules are never executed.

OpenRewrite exercises the Java compiler internally to compile code patterns that exist in these old and/or vulnerable libraries. These patterns are then used to match old or vulnerable code for the sake of modernizing or repairing it.

Using a library in compilation in this way does not trigger class initialization in the way that reflection might, for example. In other words, code paths in libraries used in compilation are never executed, and thus the vulnerability is not exploitable.

The jar has libraries bundled inside of the [META-INF/rewrite/classpath directory](https://github.com/openrewrite/rewrite-spring/tree/main/src/main/resources/META-INF/rewrite/classpath). However, those JARs are not made into a Fat Jar or a shaded library in the traditional sense. It is not possible that by using rewrite-spring that one of those libraries gets called.

## Contributing

We appreciate all types of contributions. See the [contributing guide](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md) for detailed instructions on how to get started.

### Licensing

For more information about licensing, please visit our [licensing page](https://docs.openrewrite.org/licensing/openrewrite-licensing).
