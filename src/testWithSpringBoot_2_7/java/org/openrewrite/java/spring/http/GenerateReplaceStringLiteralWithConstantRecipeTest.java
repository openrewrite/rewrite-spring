/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.http;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

class GenerateReplaceStringLiteralWithConstantRecipeTest {
    public static void main(String[] args) throws Exception {
        // Usage: GenerateReplaceStringLiteralWithConstantRecipe <fully qualified class name> <required dependency groupId> <required dependency artifactId> <output file>
        // Examples:
        // - org.springframework.http.HttpHeaders org.springframework spring-web src/main/resources/META-INF/rewrite/replace-HttpHeaders-constants.yml
        // - org.springframework.http.MediaType org.springframework spring-web src/main/resources/META-INF/rewrite/replace-MediaType-constants.yml
        String fullyQualifiedClassName = args[0];
        String requiredDependencyGroupId = args[1];
        String requiredDependencyArtifactId = args[2];
        Path outputFile = Path.of(args[3]);

        generateRecipe(fullyQualifiedClassName, requiredDependencyGroupId, requiredDependencyArtifactId, outputFile);
    }

    private static void generateRecipe(String fullyQualifiedClassName, String requiredDependencyGroupId, String requiredDependencyArtifactId, Path outputFile) throws ClassNotFoundException, IOException {
        // Write license header
        Files.write(outputFile, """
          #
          # Copyright 2023 the original author or authors.
          # <p>
          # Licensed under the Apache License, Version 2.0 (the "License");
          # you may not use this file except in compliance with the License.
          # You may obtain a copy of the License at
          # <p>
          # https://www.apache.org/licenses/LICENSE-2.0
          # <p>
          # Unless required by applicable law or agreed to in writing, software
          # distributed under the License is distributed on an "AS IS" BASIS,
          # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
          # See the License for the specific language governing permissions and
          # limitations under the License.
          #
          """.getBytes());

        // Write recipe header
        Class<?> fqclass = Class.forName(fullyQualifiedClassName);
        Files.write(outputFile, """
            ---
            type: specs.openrewrite.org/v1beta/recipe
            name: org.openrewrite.java.spring.http.ReplaceStringLiteralsWith%1$sConstants
            displayName: Replace String literals with `%1$s` constants
            description: Replace String literals with `%2$s` constants.
            applicability:
              anySource:
                - org.openrewrite.maven.search.DependencyInsight:
                    groupIdPattern: %3$s
                    artifactIdPattern: %4$s
                    scope: compile
            recipeList:
            """.formatted(
            fqclass.getSimpleName(),
            fullyQualifiedClassName,
            requiredDependencyGroupId,
            requiredDependencyArtifactId).getBytes(),
          StandardOpenOption.APPEND);

        // Write recipe for each field
        Files.write(outputFile,
          Stream.of(fqclass.getFields())
            .filter(field -> field.getType().equals(String.class))
            .map(field -> """
                - org.openrewrite.java.ReplaceStringLiteralWithConstant:
                    fullyQualifiedConstantName: %s.%s
              """.formatted(field.getDeclaringClass().getName(), field.getName()))
            .collect(joining("")).getBytes(),
          StandardOpenOption.APPEND);
    }

    @Test
    @Disabled("This test is disabled because it is not a test. It is a utility for generating recipes.")
    void generateReplaceHttpHeadersConstantsRecipe() throws Exception {
        generateRecipe("org.springframework.http.HttpHeaders", "org.springframework", "spring-web", Path.of("src/main/resources/META-INF/rewrite/replace-HttpHeaders-literals.yml"));
    }

    @Test
    @Disabled("This test is disabled because it is not a test. It is a utility for generating recipes.")
    void generateReplaceMediaTypeConstantsRecipe() throws Exception {
        generateRecipe("org.springframework.http.MediaType", "org.springframework", "spring-web", Path.of("src/main/resources/META-INF/rewrite/replace-MediaType-literals.yml"));
    }

    @Test
    void generateReplaceHttpMethodConstantsRecipe(@TempDir Path tempdir) throws Exception {
        Path tempFile = tempdir.resolve("recipe.yml");
        generateRecipe("org.springframework.http.MediaType", "org.springframework", "spring-web", tempFile);
        String contents = Files.readString(tempFile);
        assertThat(contents).contains("""
          ---
          type: specs.openrewrite.org/v1beta/recipe
          name: org.openrewrite.java.spring.http.ReplaceStringLiteralsWithMediaTypeConstants
          displayName: Replace String literals with `MediaType` constants
          description: Replace String literals with `org.springframework.http.MediaType` constants.
          applicability:
            anySource:
              - org.openrewrite.maven.search.DependencyInsight:
                  groupIdPattern: org.springframework
                  artifactIdPattern: spring-web
                  scope: compile
          recipeList:
            - org.openrewrite.java.ReplaceStringLiteralWithConstant:
                fullyQualifiedConstantName: org.springframework.http.MediaType.ALL_VALUE
            - org.openrewrite.java.ReplaceStringLiteralWithConstant:
                fullyQualifiedConstantName: org.springframework.http.MediaType.APPLICATION_ATOM_XML_VALUE
          """);
    }
}
