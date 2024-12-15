![Logo](https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss.png)
### Eliminate legacy Spring patterns. Automatically.

[![ci](https://github.com/openrewrite/rewrite-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/openrewrite/rewrite-spring/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.openrewrite.recipe/rewrite-spring.svg)](https://mvnrepository.com/artifact/org.openrewrite.recipe/rewrite-spring)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.openrewrite.org/scans)
[![Contributing Guide](https://img.shields.io/badge/Contributing-Guide-informational)](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md)

## What is this?

This project contains a series of Rewrite recipes and visitors to automatically apply best practices in Java Spring Boot applications.

Browse [a selection of recipes available through this module in the recipe catalog](https://docs.openrewrite.org/recipes/java/spring).

## How to use?

See the full documentation at [docs.openrewrite.org](https://docs.openrewrite.org/).

## How to generate Spring Boot configuration properties replacement recipes?

1. Run [GeneratePropertiesMigratorConfiguration.java](src/test/java/org/openrewrite/java/spring/internal/GeneratePropertiesMigratorConfiguration.java)
2. Revert any unwanted changes to src/main/resources/META-INF/rewrite/*.yml
3. Commit & push changes.
4. Repeat periodically as new minor versions of Spring Boot are released.

## Why do artifact scanners detect vulnerabilities in recipe artifacts/JARs?

In order to modernize and upgrade old or vulnerable code, some OpenRewrite recipe modules bundle copies of old libraries. Libraries bundled into recipe modules are never executed.

OpenRewrite exercises the Java compiler internally to compile code patterns that exist in these old and/or vulnerable libraries. These patterns are then used to match old or vulnerable code for the sake of modernizing or repairing it.

Using a library in compilation in this way does not trigger class initialization in the way that reflection might, for example. In other words, code paths in libraries used in compilation are never executed, and thus the vulnerability is not exploitable.

The jar has libraries bundled inside of the [META-INF/rewrite/classpath directory](https://github.com/openrewrite/rewrite-spring/tree/main/src/main/resources/META-INF/rewrite/classpath). However, those JARs are not made into a Fat Jar or a shaded library in the traditional sense. It is not possible that by using rewrite-spring that one of those libraries gets called.

## Contributing

We appreciate all types of contributions. See the [contributing guide](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md) for detailed instructions on how to get started.
