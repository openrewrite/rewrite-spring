/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.cloud2024;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.AddExplicitDependencyVersion;
import org.openrewrite.gradle.IsBuildGradle;

public class AddExplicitGradleSpringSecurityRsaVersion extends Recipe {
    @Getter
    final String displayName = "Preserve Gradle Spring Security RSA versions";

    @Getter
    final String description = "Preserve resolved versions of versionless `org.springframework.security:spring-security-rsa` " +
            "dependencies before Spring Cloud 2024 removes their dependency management. Explicit versions are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new IsBuildGradle<>(),
                new AddExplicitDependencyVersion("org.springframework.security", "spring-security-rsa"));
    }
}
