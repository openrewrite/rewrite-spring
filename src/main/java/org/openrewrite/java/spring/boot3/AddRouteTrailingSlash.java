/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;

import static org.openrewrite.java.tree.Space.EMPTY;

public class AddRouteTrailingSlash extends Recipe {
    private static final String GET_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.GetMapping";
    private static final String REQUEST_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.RequestMapping";
    private static final String POST_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.PostMapping";
    private static final String PUT_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.PutMapping";
    private static final String PATCH_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.PatchMapping";
    private static final String DELETE_ANNOTATION_TYPE = "org.springframework.web.bind.annotation.DeleteMapping";

    @Override
    public String getDisplayName() {
        return "Add trailing slash to Spring routes";
    }

    @Override
    public String getDescription() {
        return "This is part of Spring MVC and WebFlux URL Matching Changes, as of Spring Framework 6.0, the trailing " +
               "slash matching configuration option has been deprecated and its default value set to false. This " +
               "means that previously, a controller `@GetMapping(\"/some/greeting\")` would match both `GET " +
               "/some/greeting` and `GET /some/greeting/`, but it doesn't match `GET /some/greeting/` anymore by " +
               "default and will result in an HTTP 404 error. This recipe is to add declaration of additional route " +
               "explicitly on the controller handler (like `@GetMapping(\"/some/greeting\", \"/some/greeting/\")`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation anno = super.visitAnnotation(annotation, ctx);
                if (anno.getType() == null ||
                    !isHttpVerbMappingAnnotation(anno.getType().toString()) ||
                    anno.getArguments() == null) {
                    return anno;
                }

                if (anno.getArguments().size() == 1 && isStringLiteral(anno.getArguments().get(0))) {
                    J.Literal str = (J.Literal) anno.getArguments().get(0);
                    if (shouldAddTrailingSlashArgument(str.getValue().toString())) {
                        J.Annotation replacement = JavaTemplate.builder("{#{any(String)}, #{any(String)}}")
                                .build()
                                .apply(getCursor(),
                                        anno.getCoordinates().replaceArguments(),
                                        (Object[]) buildTwoStringsArray(str));
                        return autoFormat(replacement, ctx);
                    }
                } else {
                    // replace value
                    J.Annotation replacement = anno.withArguments(ListUtils.map(anno.getArguments(), exp -> {
                        if (exp instanceof J.Assignment) {
                            J.Assignment assignment = (J.Assignment) exp;
                            if (assignment.getVariable() instanceof J.Identifier &&
                                ((J.Identifier) assignment.getVariable()).getSimpleName().equals("value") &&
                                isStringLiteral(assignment.getAssignment())) {

                                J.Literal str = (J.Literal) assignment.getAssignment();
                                if (shouldAddTrailingSlashArgument(str.getValue().toString())) {
                                    return JavaTemplate.builder("value = {#{any(String)}, #{any(String)}}")
                                            .contextSensitive()
                                            .build()
                                            .<J.Annotation>apply(getCursor(),
                                                    anno.getCoordinates().replaceArguments(),
                                                    (Object[]) buildTwoStringsArray(str)).getArguments().get(0);
                                }
                            }
                        }
                        return exp;
                    }));
                    return maybeAutoFormat(annotation, replacement, ctx);
                }

                return anno;
            }
        };
    }

    private boolean shouldAddTrailingSlashArgument(String str) {
        return !str.endsWith("/") && !str.endsWith("*");
    }

    private J[] buildTwoStringsArray(J.Literal path) {
        String oriPath = path.getValue().toString();
        String pathWithTrailingSlash = oriPath + '/';
        return new J[]{
                path.withId(Tree.randomId())
                        .withPrefix(EMPTY),
                path.withId(Tree.randomId())
                        .withPrefix(Space.SINGLE_SPACE)
                        .withValue(pathWithTrailingSlash)
                        .withValueSource("\"" + pathWithTrailingSlash + "\"")
        };
    }

    private static boolean isHttpVerbMappingAnnotation(String fqn) {
        return GET_ANNOTATION_TYPE.equals(fqn) ||
               REQUEST_ANNOTATION_TYPE.equals(fqn) ||
               POST_ANNOTATION_TYPE.equals(fqn) ||
               PUT_ANNOTATION_TYPE.equals(fqn) ||
               PATCH_ANNOTATION_TYPE.equals(fqn) ||
               DELETE_ANNOTATION_TYPE.equals(fqn);
    }


    private static boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && TypeUtils.isString(((J.Literal) expression).getType());
    }
}
