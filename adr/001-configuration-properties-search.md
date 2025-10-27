# ADR 001: Search Mechanism for @ConfigurationProperties

## Status

Accepted

## Context

Spring Boot applications use the `@ConfigurationProperties` annotation to bind externalized configuration properties to strongly-typed Java objects. This annotation is a core mechanism for managing application configuration in Spring Boot projects.

As applications grow and evolve, it becomes increasingly important to:
- Understand what configuration properties are defined across a codebase
- Document the configuration surface area of an application
- Identify where specific configuration prefixes are used
- Support migration efforts when configuration schemas change
- Audit configuration patterns for consistency and best practices

Currently, there is no automated way to discover and catalog all `@ConfigurationProperties` usages across Spring Boot projects. Manual searching is time-consuming and error-prone, especially in large codebases or microservice architectures with multiple repositories.

## Decision

We will implement a search recipe called `FindConfigurationProperties` that:

1. **Scans for `@ConfigurationProperties` annotations**: The recipe will traverse the AST to find all class declarations annotated with `@ConfigurationProperties`.

2. **Extracts the prefix value**: The recipe will extract the `value` or `prefix` attribute from the annotation, which represents the configuration namespace (e.g., `"my.service"` in `@ConfigurationProperties("my.service")`).

3. **Emits structured data**: The recipe will populate a data table with the following information:
   - Source file path
   - Fully qualified class name
   - Configuration prefix value

4. **Marks search results**: Classes with the annotation will be marked as search results for easy identification in the OpenRewrite UI.

The implementation follows established patterns in the rewrite-spring project:
- Uses a `DataTable` to emit structured, queryable results
- Extends the `Recipe` base class with a `JavaIsoVisitor`
- Provides clear display names and descriptions for discoverability

## Consequences

### Positive

- **Discoverability**: Developers can quickly find all configuration properties in a codebase
- **Documentation**: The data table output serves as living documentation of configuration
- **Migration support**: Enables automated analysis when refactoring configuration schemas
- **Audit capability**: Makes it easy to review configuration patterns across projects
- **Tooling foundation**: Provides data that can be used by other recipes or analysis tools
- **Cross-repository analysis**: When used with Moderne's platform, can analyze configuration across entire organizations

### Negative

- **Maintenance overhead**: New recipe requires ongoing maintenance as Spring Boot evolves
- **Scope limitations**: Initial implementation only handles class-level annotations, not method-level `@Bean` definitions that might also use configuration properties
- **Value extraction complexity**: Only extracts literal string values; doesn't handle dynamic or computed prefixes

### Neutral

- **Testing requirements**: Requires comprehensive tests to ensure correct annotation detection and value extraction
- **Documentation needs**: Should be documented in user-facing recipe catalogs

## Implementation Notes

The recipe implementation consists of two main components:

1. **ConfigurationPropertiesTable**: A data table class that defines the schema for captured data
2. **FindConfigurationProperties**: The recipe class that implements the search logic

The recipe handles multiple annotation formats:
- `@ConfigurationProperties("prefix")`
- `@ConfigurationProperties(value = "prefix")`
- `@ConfigurationProperties(prefix = "prefix")`

## Future Considerations

- Support for record types with constructor-based binding
- Detection of nested configuration properties
- Analysis of property usage within the class
- Integration with Spring Boot's configuration metadata
- Validation of prefix naming conventions (kebab-case)
