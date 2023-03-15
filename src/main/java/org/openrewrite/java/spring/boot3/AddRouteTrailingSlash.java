package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.PartProvider;
import org.openrewrite.java.tree.*;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.java.tree.Space.EMPTY;

public class AddRouteTrailingSlash extends Recipe {
    private static final String GET_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.GetMapping";
    private static final String REQUEST_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String POST_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.PostMapping";
    private static final String PUT_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.PutMapping";
    private static final String PATCH_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.PatchMapping";
    private static final String DELETE_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.DeleteMapping";
    @Nullable private static J.NewArray twoStringsArrayTemplate;
    @Nullable private static J.Assignment valueAssignmentTemplate;

    @Override
    public String getDisplayName() {
        return "Add trailing slash to Spring routes";
    }

    @Override
    public String getDescription() {
        return "This is part of Spring MVC and WebFlux URL Matching Changes, as of Spring Framework 6.0, the trailing" +
               " slash matching configuration option has been deprecated and its default value set to false. This " +
               "means that previously, a controller `@GetMapping(\"/some/greeting\")` would match both \"GET " +
               "/some/greeting\" and \"GET /some/greeting/\", but it doesn't match \"GET /some/greeting/\" anymore by" +
               " default and will result in an HTTP 404 error. This recipe is to add declaration of additional route" +
               " explicitly on the controller handler (like @GetMapping(\"/some/greeting\", \"/some/greeting/\").";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation anno = super.visitAnnotation(annotation, ctx);
                if (anno.getType() == null || !isHttpVerbMappingAnnotation(anno.getType().toString())) {
                    return anno;
                }

                if (anno.getArguments().size() == 1 &&
                    isStringLiteral(anno.getArguments().get(0))) {

                    J.Literal str = (J.Literal) anno.getArguments().get(0);
                    if (!matchTrailingSlash(str.getValue().toString())) {
                        J.Assignment assignment = buildAssignment(str);
                        return annotation.withArguments(Collections.singletonList(assignment));
                    }

                } else {
                    // search for value
                    List<Expression> args = anno.getArguments();
                    for (int i = 0; i < args.size(); i++) {
                        Expression exp = args.get(i);
                        if (exp instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) exp;
                            if (assignment.getVariable() instanceof J.Identifier &&
                                ((J.Identifier) assignment.getVariable()).getSimpleName().equals("value") &&
                                isStringLiteral(assignment.getAssignment())) {

                                J.Literal str = (J.Literal) assignment.getAssignment();
                                if (!matchTrailingSlash(str.getValue().toString())) {
                                    args.set(i, buildAssignment(str));
                                    return annotation.withArguments(args);
                                }
                            }
                        }
                    }
                }

                return anno;
            }
        };
    }

    private boolean matchTrailingSlash(String str) {
        return str.endsWith("/") || str.endsWith("*");
    }

    private J.Assignment buildAssignment(J.Literal path) {
        String oriPath = path.getValue().toString();
        String pathWithTrailingSlash = oriPath + '/';

        J.Assignment assignmentTemplate = getAssignmentTemplate().withPrefix(Space.EMPTY);
        J.NewArray twoPaths = getTwoStringsArrayTemplate();
        List<Expression> exps = twoPaths.getInitializer();
        exps.set(0, path.withPrefix(EMPTY));
        exps.set(1, path.withValue(pathWithTrailingSlash)
            .withValueSource("\"" + pathWithTrailingSlash + "\"")
            .withPrefix(Space.build(" ", emptyList())));
        twoPaths = twoPaths.withInitializer(exps);
        return assignmentTemplate.withAssignment(twoPaths);
    }

    private static boolean isHttpVerbMappingAnnotation(String fqn) {
        return GET_ANNOTATION_TYPE.equals(fqn) ||
               REQUEST_ANNOTATION_TYPE.equals(fqn) ||
               POST_ANNOTATION_TYPE.equals(fqn) ||
               PUT_ANNOTATION_TYPE.equals(fqn) ||
               PATCH_ANNOTATION_TYPE.equals(fqn) ||
               DELETE_ANNOTATION_TYPE.equals(fqn);
    }

    private static J.NewArray getTwoStringsArrayTemplate() {
        if (twoStringsArrayTemplate == null) {
            twoStringsArrayTemplate = PartProvider.buildPart(
                "class Test {\n" +
                "    String[] value = { \"a\", \"b\"};\n" +
                "}",
                J.NewArray.class);
        }
        return twoStringsArrayTemplate;
    }

    private static J.Assignment getAssignmentTemplate() {
        if (valueAssignmentTemplate == null) {
            valueAssignmentTemplate = PartProvider.buildPart(
                "class Test {\n" +
                "    void method() {\n" +
                "        String[] value;\n" +
                "        value = null;\n" +
                "    }\n" +
                "}",
                J.Assignment.class);
        }
        return valueAssignmentTemplate;
    }

    private static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
    }
}
