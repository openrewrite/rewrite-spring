# Rewrite-Spring Project Guidelines

## Coding Conventions

### General Conventions
- The project follows standard Java coding conventions
- Uses the Moderne Source Available License for all source files
- Each file includes a standard copyright header
- Classes are named according to their purpose with descriptive names
- Methods follow camelCase naming convention
- Constants use UPPER_SNAKE_CASE

### Code Style
- Indentation uses 4 spaces (not tabs)
- Line endings are LF (Unix-style)
- Maximum line length is respected for readability
- Multi-line string literals are used for test inputs and expected outputs
- Builder pattern is commonly used for creating complex objects

### Nullness Annotations
- JSpecify is used for nullness support via the `@NullMarked` annotation
- Nullness is enabled at the package-level using `@NullMarked` in the `package-info.java` files
- Java code only needs to use `@Nullable` annotation where a parameter, return value, or field can be `null`
- A nested type that has a qualified reference in the source code (as e.g. `J.Identifier`) needs to be annotated as `J.@Nullable Identifier`

### Dependencies
- Uses Gradle with Kotlin DSL for build configuration
- Dependencies are organized by purpose in the `build.gradle.kts` file
- Uses OpenRewrite as the core framework for code transformation
- Supports multiple Spring Boot versions (1.5 through 3.4)

### Type Reference Dependencies
- Dependencies for type references from Java sources declared in tests (via `Assertions#java()`) and in templates (`JavaTemplate` usage) need to be configured via the `JavaParser`
- These dependencies should be configured using the `JavaParser.Builder#classpathFromResources()` method
- The dependencies are specified as regex patterns that match against Maven artifact names from the `recipeDependencies` configuration in the Gradle build script
- As there are multiple versions for many dependencies, the version needs to be encoded into the pattern (e.g., `"spring-beans-6"`)
- Adding a new dependency to the `recipeDependencies` configuration in `build.gradle.kts` requires running the Gradle `createTypeTable` task

#### Examples:

In tests:
```
JavaParser.fromJavaVersion()
    .classpathFromResources(new InMemoryExecutionContext(),
        "spring-context-6.+", "spring-beans-6.+")
```

In production code with JavaTemplate:
```
JavaTemplate.builder("@Bean")
    .imports(FQN_BEAN)
    .javaParser(JavaParser.fromJavaVersion()
        .classpathFromResources(ctx, "spring-context-6"))
    .build()
```

## Code Organization and Package Structure

### Package Structure
The project follows a hierarchical package structure:

- `org.openrewrite` - Top-level package
  - `.gradle` - Gradle-specific transformations
    - `.spring` - Spring-specific Gradle transformations
  - `.java` - Java-specific transformations
    - `.spring` - Spring-specific Java transformations
      - `.amqp` - Spring AMQP transformations
      - `.batch` - Spring Batch transformations
      - `.boot2` - Spring Boot 2.x transformations
      - `.boot3` - Spring Boot 3.x transformations
      - `.cloud2022` - Spring Cloud 2022 transformations
      - `.data` - Spring Data transformations
      - `.framework` - Spring Framework transformations
      - `.http` - Spring HTTP transformations
      - `.internal` - Internal utilities
      - `.kafka` - Spring Kafka transformations
      - `.search` - Search-related utilities
      - `.security5` - Spring Security 5.x transformations
      - `.security6` - Spring Security 6.x transformations
      - `.table` - Table-related utilities
      - `.test` - Test-related transformations
      - `.trait` - Trait-related utilities
      - `.util` - Utility classes
  - `.maven` - Maven-specific transformations
    - `.spring` - Spring-specific Maven transformations

### Source Sets
- `main` - Main source code
- `test` - Standard test code

## Unit and Integration Testing Approaches

### Testing Framework
- Uses JUnit 5 for unit testing
- Tests implement the `RewriteTest` interface from OpenRewrite
- Uses `@Test` annotations to mark test methods
- Uses `@DocumentExample` for tests that serve as documentation examples

### Test Structure
- Tests follow a declarative style using the `rewriteRun` method
- Test inputs and expected outputs are defined using multi-line string literals
- Helper methods are used to create test fixtures and reduce duplication
- Tests are organized to mirror the main source code structure

### Test Execution
- Each specialized source set has its own test task
- Tests can be run for specific Spring Boot or Spring Security versions
- The build is configured to run all tests during the check phase
- JVM arguments are configured for better diagnostics during test execution

### Integration Testing
- Integration tests verify the behavior of transformations across different Spring versions
- Separate source sets allow testing against multiple Spring Boot and Spring Security versions
- Tests use actual Spring dependencies to ensure compatibility
- The build is configured to run integration tests as part of the verification process
