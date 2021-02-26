/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

/**
 * spring-boot 1.X has SpringBootTest but it does not have an ExtendsWith annotation.
 * The purpose of JUnit 5 extensions is to extend the behavior of test classes or methods
 * SpringExtension integrates the Spring TestContext Framework into JUnit 5's Jupiter programming model.
 * So thats why if SpringBootTest exists just remove RunWith and if it does not then just add the JUnit 5 SpringExtension
 */
public class SpringRunnerToSpringExtension extends Recipe {

    @Override
    public String getDisplayName() {
        return "Replace @RunWith(SpringRunner.class) with @ExtendsWith(SpringExtension.class)";
    }

    @Override
    public String getDescription() {
        return "SpringExtension integrates the Spring TestContext Framework into JUnit 5's Juipiter programming model";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SpringRunnerToSpringExtensionVisitor();
    }

    public static class SpringRunnerToSpringExtensionVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final String springExtensionType =
                "org.springframework.test.context.junit.jupiter.SpringExtension";
        private static final String springRunnerType =
                "org.springframework.test.context.junit4.SpringRunner";


        public static final JavaType.Class runWithType = JavaType.Class.build("org.junit.runner.RunWith");
        public static final J.Identifier runWithIdent = J.Identifier.build(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                "RunWith",
                runWithType);

        public static final JavaType.Class extendWithType = JavaType.Class.build("org.junit.jupiter.api.extension.ExtendWith");
        public static final J.Identifier extendWithIdent = J.Identifier.build(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                "ExtendWith",
                extendWithType
        );

        // Reference @RunWith(SpringRunner.class) annotation for semantically equal to compare against
        private static final J.Annotation runWithSpringRunnerAnnotation = new J.Annotation(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                runWithIdent,
                JContainer.build(
                        Collections.singletonList(
                                JRightPadded.build(
                                        new J.FieldAccess(
                                                randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                J.Identifier.build(
                                                        randomId(),
                                                        Space.EMPTY,
                                                        Markers.EMPTY,
                                                        "SpringRunner",
                                                        JavaType.buildType(springRunnerType)
                                                ),
                                                JLeftPadded.build(J.Identifier.build(randomId(), Space.EMPTY, Markers.EMPTY, "class", null)),
                                                JavaType.Class.build("java.lang.Class")
                                        )
                                )
                        )
                )
        );

        private static final JavaType.Class springJUnit4ClassRunnerType =
                JavaType.Class.build("org.springframework.test.context.junit4.SpringJUnit4ClassRunner");

        // Reference @RunWith(SpringJUnit4ClassRunner.class) annotation for semantically equal to compare against
        private static final J.Annotation runWithSpringJUnit4ClassRunnerAnnotation = new J.Annotation(
                randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                runWithIdent,
                JContainer.build(
                        Collections.singletonList(
                                JRightPadded.build(
                                        new J.FieldAccess(
                                                randomId(),
                                                Space.EMPTY,
                                                Markers.EMPTY,
                                                J.Identifier.build(
                                                        randomId(),
                                                        Space.EMPTY,
                                                        Markers.EMPTY,
                                                        "SpringJUnit4ClassRunner",
                                                        springJUnit4ClassRunnerType
                                                ),
                                                JLeftPadded.build(J.Identifier.build(randomId(), Space.EMPTY, Markers.EMPTY, "class", null)),
                                                JavaType.Class.build("java.lang.Class")
                                        )
                                )
                        )
                )
        );

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            List<J.Annotation> keepAnnotations = cd.getLeadingAnnotations().stream().filter(
                    a -> !shouldReplaceAnnotation(a)
            ).collect(Collectors.toList());
            if (keepAnnotations.size() != cd.getLeadingAnnotations().size()) {
                maybeAddImport(extendWithType);
                maybeAddImport(springExtensionType);
                maybeRemoveImport(springRunnerType);
                maybeRemoveImport(springJUnit4ClassRunnerType);
                maybeRemoveImport(runWithType);
                cd = cd.withLeadingAnnotations(keepAnnotations);
                cd = cd.withTemplate(
                        template("@ExtendWith(SpringExtension.class)")
                                .imports("org.junit.jupiter.api.extension.ExtendWith", springExtensionType)
                                .javaParser( JavaParser.fromJavaVersion().dependsOn(Arrays.asList(
                                        Parser.Input.fromString(
                                                "package org.junit.jupiter.api.extension;\n" +
                                                        "@Target({ ElementType.TYPE, ElementType.METHOD })\n" +
                                                        "public @interface ExtendWith {\n" +
                                                        "Class<? extends Extension>[] value();\n" +
                                                        "}"),
                                        Parser.Input.fromString(
                                                "package org.springframework.test.context.junit.jupiter;\n" +
                                                        "public class SpringExtension {}"
                                        ))).build())
                                .build(),
                        cd.getCoordinates().addAnnotation(
                                // TODO should this use some configuration (similar to styles) for annotation ordering?
                                Comparator.comparing(
                                        a -> TypeUtils.asFullyQualified(a.getType()).getFullyQualifiedName()
                                )
                        )
                );
            }
            return cd;
        }

        private boolean shouldReplaceAnnotation(J.Annotation maybeSpringRunner) {
            return SemanticallyEqual.areEqual(runWithSpringRunnerAnnotation, maybeSpringRunner)
                    || SemanticallyEqual.areEqual(runWithSpringJUnit4ClassRunnerAnnotation, maybeSpringRunner);
        }
    }
}
