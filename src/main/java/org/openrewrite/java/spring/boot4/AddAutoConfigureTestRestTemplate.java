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
package org.openrewrite.java.spring.boot4;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

public class AddAutoConfigureTestRestTemplate extends Recipe {

    private static final String FQN_TESTRESTTEMPLATE = "org.springframework.boot.resttestclient.TestRestTemplate";
    private static final String AUTOCONFIGURE_TESTRESTTEMPLATE_SIMPLE_NAME = "AutoConfigureTestRestTemplate";
    private static final String FQN_AUTOCONFIGURE_TESTRESTTEMPLATE = "org.springframework.boot.resttestclient.autoconfigure." + AUTOCONFIGURE_TESTRESTTEMPLATE_SIMPLE_NAME;

    private static final AnnotationMatcher SPRING_BOOT_TEST_MATCHER = new AnnotationMatcher("@org.springframework.boot.test.context.SpringBootTest", true);
    private static final AnnotationMatcher AUTOCONFIGURE_TESTRESTTEMPLATE_MATCHER = new AnnotationMatcher("@" + FQN_AUTOCONFIGURE_TESTRESTTEMPLATE, true);

    @Getter
    final String displayName = "Add `@AutoConfigureTestRestTemplate` if necessary";

    @Getter
    final String description = "Adds `@AutoConfigureTestRestTemplate` to test classes annotated with `@SpringBootTest` that use `TestRestTemplate` since this bean is no longer auto-configured as described in the [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#using-webclient-or-testresttemplate-and-springboottest).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_TESTRESTTEMPLATE, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                if (cd.getLeadingAnnotations().stream().anyMatch(SPRING_BOOT_TEST_MATCHER::matches) &&
                        cd.getLeadingAnnotations().stream().noneMatch(AUTOCONFIGURE_TESTRESTTEMPLATE_MATCHER::matches)) {

                    maybeAddImport(FQN_AUTOCONFIGURE_TESTRESTTEMPLATE);
                    return JavaTemplate.builder("@" + AUTOCONFIGURE_TESTRESTTEMPLATE_SIMPLE_NAME)
                            .imports(FQN_AUTOCONFIGURE_TESTRESTTEMPLATE)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-boot-resttestclient-4.+"))
                            .build()
                            .apply(getCursor(), cd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }

                return cd;
            }
        });
    }
}
