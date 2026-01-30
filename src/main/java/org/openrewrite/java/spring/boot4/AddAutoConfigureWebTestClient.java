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

public class AddAutoConfigureWebTestClient extends Recipe {

    private static final String SIMPLE_NAME = "AutoConfigureWebTestClient";
    private static final String FULLY_QUALIFIED = "org.springframework.boot.webtestclient.autoconfigure." + SIMPLE_NAME;
    private static final String WEB_TEST_CLIENT = "org.springframework.test.web.reactive.server.WebTestClient";

    private static final AnnotationMatcher AUTO_CONFIGURE_WEB_TEST_CLIENT = new AnnotationMatcher("@" + FULLY_QUALIFIED, true);
    private static final AnnotationMatcher SPRING_BOOT_TEST = new AnnotationMatcher("@org.springframework.boot.test.context.SpringBootTest", true);

    @Getter
    final String displayName = "Add `@AutoConfigureWebTestClient` if necessary";

    @Getter
    final String description = "Adds `@AutoConfigureWebTestClient` to test classes annotated with " +
            "`@SpringBootTest` that use `WebTestClient` since this bean is no longer auto-configured as described " +
            "in the [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#using-webclient-or-testresttemplate-and-springboottest).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(WEB_TEST_CLIENT, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                AnnotationService annotationService = service(AnnotationService.class);
                if (annotationService.matches(getCursor(), SPRING_BOOT_TEST) &&
                        !annotationService.matches(getCursor(), AUTO_CONFIGURE_WEB_TEST_CLIENT)) {
                    maybeAddImport(FULLY_QUALIFIED);
                    return JavaTemplate.builder("@" + SIMPLE_NAME)
                            .imports(FULLY_QUALIFIED)
                            .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                    "package org.springframework.boot.webtestclient.autoconfigure;" +
                                    "public @interface AutoConfigureWebTestClient {}"))
                            .build()
                            .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
                }

                return cd;
            }
        });
    }
}
