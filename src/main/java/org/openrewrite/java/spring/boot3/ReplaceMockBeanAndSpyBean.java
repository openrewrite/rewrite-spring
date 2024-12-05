package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

public class ReplaceMockBeanAndSpyBean extends Recipe {
    private static final AnnotationMatcher MOCKBEAN_ANNOTATION_MATCHER =
            new AnnotationMatcher("@org.springframework.boot.test.mock.mockito.MockBean");
    private static final AnnotationMatcher SPYBEAN_ANNOTATION_MATCHER =
            new AnnotationMatcher("@org.springframework.boot.test.mock.mockito.SpyBean");


    @Override
    public String getDisplayName() {
        return "Replace @MockBean and  @SpyBean";
    }

    @Override
    public String getDescription() {
        return "Replaces `@MockBean` and `@SpyBean` annotations with `@MockitoBean` and `@MockitoSpyBean`. " +
                "Also Update the relevant import statements.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {

                J.Annotation a = super.visitAnnotation(annotation, ctx);

                // Check if the annotation is @MockBean
                if (MOCKBEAN_ANNOTATION_MATCHER.matches(a)) {
                    //remove the old import and add the new one
                    maybeRemoveImport("org.springframework.boot.test.mock.mockito.MockBean");
                    maybeAddImport("org.springframework.test.context.bean.override.mockito.MockitoBean");

                    //Change the annotation
                    a = (J.Annotation) new ChangeType("org.springframework.boot.test.mock.mockito.MockBean",
                            "org.springframework.test.context.bean.override.mockito.MockitoBean", false)
                            .getVisitor().visit(a, ctx, getCursor().getParentOrThrow());
                }

                // Check if the annotation is @SpyBean
                if (SPYBEAN_ANNOTATION_MATCHER.matches(a)) {
                    //remove the old import and add the new one
                    maybeRemoveImport("org.springframework.boot.test.mock.mockito.SpyBean");
                    maybeAddImport("org.springframework.test.context.bean.override.mockito.MockitoSpyBean");

                    //Change the annotation
                    a = (J.Annotation) new ChangeType("org.springframework.boot.test.mock.mockito.SpyBean",
                            "org.springframework.test.context.bean.override.mockito.MockitoSpyBean", false)
                            .getVisitor().visit(a, ctx, getCursor().getParentOrThrow());
                }
                return a != null ? a : annotation;
            }
        };
    }
}
