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
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;

import static java.util.Comparator.comparing;

public class AddAutoConfigureTestRestTemplate extends Recipe {

    private static final String SIMPLE_NAME = "AutoConfigureTestRestTemplate";
    private static final String FULLY_QUALIFIED = "org.springframework.boot.resttestclient.autoconfigure." + SIMPLE_NAME;
    private static final String TEST_REST_TEMPLATE = "org.springframework.boot.resttestclient.TestRestTemplate";

    private static final AnnotationMatcher AUTO_CONFIGURE_TEST_REST_TEMPLATE = new AnnotationMatcher(FULLY_QUALIFIED, true);
    private static final AnnotationMatcher SPRING_BOOT_TEST = new AnnotationMatcher("@org.springframework.boot.test.context.SpringBootTest", true);

    @Getter
    final String displayName = "Add `@AutoConfigureTestRestTemplate` if necessary";

    @Getter
    final String description = "Adds `@AutoConfigureTestRestTemplate` to test classes annotated with " +
            "`@SpringBootTest` that use `TestRestTemplate` since this bean is no longer auto-configured as described " +
            "in the [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#using-webclient-or-testresttemplate-and-springboottest).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(TEST_REST_TEMPLATE, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                AnnotationService annotationService = service(AnnotationService.class);
                if (annotationService.matches(getCursor(), SPRING_BOOT_TEST) &&
                        !annotationService.matches(getCursor(), AUTO_CONFIGURE_TEST_REST_TEMPLATE)) {
                    maybeAddImport(FULLY_QUALIFIED);
                    return JavaTemplate.builder("@" + SIMPLE_NAME)
                            .imports(FULLY_QUALIFIED)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-boot-resttestclient-4"))
                            .build()
                            .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                }

                return cd;
            }
        });
    }
}
