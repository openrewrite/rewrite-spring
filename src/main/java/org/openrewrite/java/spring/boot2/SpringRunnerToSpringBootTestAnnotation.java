package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

public class SpringRunnerToSpringBootTestAnnotation extends Recipe {
    @Override
    public String getDisplayName() {
        return "Replace @RunWith(SpringRunner.class) with @SpringBootTest";
    }

    @Override
    public String getDescription() {
        return "Replaces @RunWith(SpringRunner.class) with @SpringBootTest";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SpringRunnerToSpringBootTestAnnotationVisitor();
    }

    private static class SpringRunnerToSpringBootTestAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher SPRING_RUNNER_MATCHER = new AnnotationMatcher("@org.junit.runner.RunWith");

        private final JavaTemplate t = template("@SpringBootTest")
                .imports("org.springframework.boot.test.context.SpringBootTest")
                .javaParser(JavaParser.fromJavaVersion()
                        .dependsOn(Parser.Input.fromResource("/META-INF/rewrite/SpringBootTest.java", "---")).build())
                .build();

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
            J.Annotation a = super.visitAnnotation(annotation, executionContext);
            if (SPRING_RUNNER_MATCHER.matches(a)) {
                a = maybeAutoFormat(a, a.withTemplate(t, a.getCoordinates().replace()), executionContext);
                maybeRemoveImport("org.junit.runner.RunWith");
                maybeRemoveImport("org.springframework.test.context.junit4.SpringRunner");
                maybeAddImport("org.springframework.boot.test.context.SpringBootTest");
            }
            return a;
        }
    }
}
