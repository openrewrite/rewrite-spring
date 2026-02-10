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
package org.openrewrite.java.spring.mvc;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class JaxrsToSpringmvcAnnotations extends Recipe {

    String displayName = "Migrate jax-rs annotations to spring MVC annotations";
    String description = "Replaces all jax-rs annotations with Spring MVC annotations.";
    Set<String> tags = new HashSet<>(Arrays.asList("Java", "Spring"));

    private static final List<AnnotationMatcher> PATH_ANNOTATION_MATCHERS = Arrays.asList(
            new AnnotationMatcher("@jakarta.ws.rs.Path"),
            new AnnotationMatcher("@javax.ws.rs.Path")
    );


    private static final Map<String, String> ANNOTATION_MAPPING = new HashMap<>();

    static {
        ANNOTATION_MAPPING.put("jakarta.ws.rs.GET", "GetMapping");
        ANNOTATION_MAPPING.put("jakarta.ws.rs.POST", "PostMapping");
        ANNOTATION_MAPPING.put("jakarta.ws.rs.PUT", "PutMapping");
        ANNOTATION_MAPPING.put("jakarta.ws.rs.DELETE", "DeleteMapping");
        ANNOTATION_MAPPING.put("javax.ws.rs.GET", "GetMapping");
        ANNOTATION_MAPPING.put("javax.ws.rs.POST", "PostMapping");
        ANNOTATION_MAPPING.put("javax.ws.rs.PUT", "PutMapping");
        ANNOTATION_MAPPING.put("javax.ws.rs.DELETE", "DeleteMapping");
    }

    private static final Map<String, String> PARAM_ANNOTATION_MAPPING = new HashMap<>();

    static {
        PARAM_ANNOTATION_MAPPING.put("jakarta.ws.rs.QueryParam", "RequestParam");
        PARAM_ANNOTATION_MAPPING.put("jakarta.ws.rs.FormParam", "RequestParam");
        PARAM_ANNOTATION_MAPPING.put("jakarta.ws.rs.PathParam", "PathVariable");
        PARAM_ANNOTATION_MAPPING.put("jakarta.ws.rs.HeaderParam", "RequestHeader");
        PARAM_ANNOTATION_MAPPING.put("javax.ws.rs.QueryParam", "RequestParam");
        PARAM_ANNOTATION_MAPPING.put("javax.ws.rs.FormParam", "RequestParam");
        PARAM_ANNOTATION_MAPPING.put("javax.ws.rs.PathParam", "PathVariable");
        PARAM_ANNOTATION_MAPPING.put("javax.ws.rs.HeaderParam", "RequestHeader");
    }

    private static final String ANNOTATION_PREFIX = "org.springframework.web.bind.annotation.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                List<String> annotationArgs = new ArrayList<>();
                List<Expression> args = new ArrayList<>();
                getArgumentsForRequestMappings(cd.getLeadingAnnotations(), annotationArgs, args);
                if (annotationArgs.isEmpty()) {
                    return cd;
                }
                String annTemplate = "@RestController\n@RequestMapping(" + String.join(", ", annotationArgs) + ")";

                JavaCoordinates coordinates = cd.getCoordinates().addAnnotation((o1, o2) -> 1);
                cd = JavaTemplate.builder(annTemplate)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web"))
                        .imports("org.springframework.web.bind.annotation.RestController",
                                "org.springframework.web.bind.annotation.RequestMapping")
                        .build()
                        .apply(updateCursor(cd), coordinates, args.toArray());
                maybeAddImport("org.springframework.web.bind.annotation.RequestMapping");
                maybeAddImport("org.springframework.web.bind.annotation.RestController");

                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                String jaxrsAnnotation = null;
                for (String jaxrs : ANNOTATION_MAPPING.keySet()) {
                    Optional<J.Annotation> annOpt = md.getLeadingAnnotations().stream()
                            .filter(a -> TypeUtils.isOfClassType(a.getType(), jaxrs))
                            .findFirst();
                    if (annOpt.isPresent()) {
                        jaxrsAnnotation = jaxrs;
                        break;
                    }
                }
                if (jaxrsAnnotation == null) {
                    return md;
                }
                String springAnnotation = ANNOTATION_MAPPING.get(jaxrsAnnotation);
                doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + jaxrsAnnotation)));
                maybeRemoveImport(jaxrsAnnotation);

                List<String> annotationArgs = new ArrayList<>();
                List<Expression> args = new ArrayList<>();
                Expression consumes = getArgumentsForRequestMappings(md.getLeadingAnnotations(), annotationArgs, args);
                String annTemplate;
                if (annotationArgs.isEmpty()) {
                    annTemplate = "@" + springAnnotation;
                } else {
                    annTemplate = "@" + springAnnotation + "(" + String.join(", ", annotationArgs) + ")";
                }


                JavaCoordinates coordinates = md.getCoordinates().addAnnotation((o1, o2) -> 1);
                md = JavaTemplate.builder(annTemplate)
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web"))
                        .imports(ANNOTATION_PREFIX + springAnnotation)
                        .build()
                        .apply(updateCursor(md), coordinates, args.toArray());
                maybeAddImport(ANNOTATION_PREFIX + springAnnotation);

                getCursor().putMessage("methodParams", md.getParameters());
                getCursor().putMessage("isPutOrPost", jaxrsAnnotation.endsWith("PUT") || jaxrsAnnotation.endsWith("POST"));
                getCursor().putMessage("needRequestPart", isMultiPart(consumes));
                return super.visitMethodDeclaration(md, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variableDecl, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(variableDecl, ctx);

                List<Statement> methodParams = getCursor().getNearestMessage("methodParams");
                if (methodParams == null || !methodParams.contains(vd)) {
                    return vd;
                }

                String springAnnotation = null;
                Expression value = null;
                Expression defaultValue = null;
                boolean isPutOrPost = Boolean.TRUE.equals(getCursor().getNearestMessage("isPutOrPost"));
                boolean needRequestPart = Boolean.TRUE.equals(getCursor().getNearestMessage("needRequestPart"));
                boolean isContextParam = false;
                for (J.Annotation ann : vd.getLeadingAnnotations()) {
                    String annType = ann.getType() != null ? ann.getType().toString() : "";
                    if (PARAM_ANNOTATION_MAPPING.containsKey(annType)) {
                        springAnnotation = PARAM_ANNOTATION_MAPPING.get(annType);
                        value = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    } else if ("jakarta.ws.rs.DefaultValue".equals(annType) || "javax.ws.rs.DefaultValue".equals(annType)) {
                        defaultValue = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    } else if ("jakarta.ws.rs.core.Context".equals(annType) || "javax.ws.rs.core.Context".equals(annType)) {
                        isContextParam = true;
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    }
                }

                StringBuilder annBuilder = null;
                List<Expression> args = new ArrayList<>();
                if (springAnnotation != null) {
                    args.add(value);
                    annBuilder = new StringBuilder("@" + springAnnotation + "(");
                    if ("PathVariable".equals(springAnnotation)) {
                        annBuilder.append("#{any(String)})");
                    } else {
                        if (defaultValue == null) {
                            annBuilder.append("value = #{any(String)}, required = false)");
                        } else {
                            annBuilder.append("value = #{any(String)}, defaultValue = #{any()})");
                            args.add(defaultValue);
                        }
                    }
                } else if (!isContextParam && isPutOrPost) {
                    if (needRequestPart) {
                        springAnnotation = "RequestPart";
                    } else {
                        springAnnotation = "RequestBody";
                    }
                    annBuilder = new StringBuilder("@" + springAnnotation);
                }
                if (annBuilder != null) {
                    JavaCoordinates coordinates = vd.getCoordinates().addAnnotation((o1, o2) -> 1);
                    vd = JavaTemplate.builder(annBuilder.toString())
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web"))
                            .imports(ANNOTATION_PREFIX + springAnnotation)
                            .build()
                            .apply(updateCursor(vd), coordinates, args.toArray());
                    maybeAddImport(ANNOTATION_PREFIX + springAnnotation);
                }

                return vd;
            }

            private Expression getArgumentsForRequestMappings(List<J.Annotation> annotations, List<String> annotationArgs, List<Expression> args) {
                Expression path = null;
                Expression produces = null;
                Expression consumes = null;

                for (J.Annotation ann : annotations) {
                    String annType = ann.getType() != null ? ann.getType().toString() : "";
                    switch (annType) {
                        case "jakarta.ws.rs.Path":
                        case "javax.ws.rs.Path":
                            path = getValueFromAnnotation(ann);
                            doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                            maybeRemoveImport(annType);
                            break;
                        case "jakarta.ws.rs.Consumes":
                        case "javax.ws.rs.Consumes":
                            consumes = getValueFromAnnotation(ann);
                            doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                            maybeRemoveImport(annType);
                            break;
                        case "jakarta.ws.rs.Produces":
                        case "javax.ws.rs.Produces":
                            produces = getValueFromAnnotation(ann);
                            doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                            maybeRemoveImport(annType);
                            break;
                    }
                }

                if (path != null && produces == null && consumes == null) {
                    annotationArgs.add("#{any(String)}");
                    args.add(path);
                } else {
                    if (path != null) {
                        annotationArgs.add("value = #{any(String)}");
                        args.add(path);
                    }
                    if (produces != null) {
                        annotationArgs.add("produces = #{any()}");
                        args.add(produces);
                    }
                    if (consumes != null) {
                        annotationArgs.add("consumes = #{any()}");
                        args.add(consumes);
                    }
                }

                return consumes;
            }

            private Expression getValueFromAnnotation(J.Annotation ann) {
                if (ann.getArguments() == null || ann.getArguments().isEmpty()) {
                    return null;
                }
                Expression arg = ann.getArguments().get(0);
                if (arg instanceof J.Assignment) {
                    return ((J.Assignment) arg).getAssignment();
                }
                return arg;
            }

            private boolean isMultiPart(Expression consumes) {
                if (consumes == null) {
                    return false;
                }
                if (consumes instanceof J.NewArray) {
                    J.NewArray arr = (J.NewArray) consumes;
                    if (arr.getInitializer() != null && arr.getInitializer().size() == 1) {
                        consumes = arr.getInitializer().get(0);
                    } else {
                        return false;
                    }
                }
                return consumes.toString().toLowerCase().contains("multipart");
            }

        };
    }
}
