package org.openrewrite.java.spring.mvc;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.*;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class JaxrsToSpringmvcAnnotations extends Recipe {

    @Override
    public @NotNull String getDisplayName() {
        return "Migrate jax-rs annotations to spring MVC annotations";
    }

    @Override
    public @NotNull String getDescription() {
        return "Replaces all jax-rs annotations with Spring MVC annotations.";
    }

    @Override
    public @NotNull Set<String> getTags() {
        return Set.of("Java", "Spring");
    }

    private static final List<AnnotationMatcher> PATH_ANNOTATION_MATCHERS = List.of(
            new AnnotationMatcher("@jakarta.ws.rs.Path"),
            new AnnotationMatcher("@javax.ws.rs.Path")
    );


    private static final Map<String, String> ANNOTATION_MAPPING = Map.of(
            "jakarta.ws.rs.GET", "GetMapping",
            "jakarta.ws.rs.POST", "PostMapping",
            "jakarta.ws.rs.PUT", "PutMapping",
            "jakarta.ws.rs.DELETE", "DeleteMapping",
            "javax.ws.rs.GET", "GetMapping",
            "javax.ws.rs.POST", "PostMapping",
            "javax.ws.rs.PUT", "PutMapping",
            "javax.ws.rs.DELETE", "DeleteMapping"
    );

    private static final Map<String, String> PARAM_ANNOTATION_MAPPING = Map.of(
            "jakarta.ws.rs.QueryParam", "RequestParam",
            "jakarta.ws.rs.FormParam", "RequestParam",
            "jakarta.ws.rs.PathParam", "PathVariable",
            "jakarta.ws.rs.HeaderParam", "RequestHeader",
            "javax.ws.rs.QueryParam", "RequestParam",
            "javax.ws.rs.FormParam", "RequestParam",
            "javax.ws.rs.PathParam", "PathVariable",
            "javax.ws.rs.HeaderParam", "RequestHeader"
    );

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
                if(annotationArgs.isEmpty()) {
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
                if(annotationArgs.isEmpty()) {
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
                    if(PARAM_ANNOTATION_MAPPING.containsKey(annType)) {
                        springAnnotation = PARAM_ANNOTATION_MAPPING.get(annType);
                        value = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    } else if(annType.equals("jakarta.ws.rs.DefaultValue") || annType.equals("javax.ws.rs.DefaultValue")) {
                        defaultValue = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    } else if(annType.equals("jakarta.ws.rs.core.Context") || annType.equals("javax.ws.rs.core.Context")) {
                        isContextParam = true;
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    }
                }

                StringBuilder annBuilder = null;
                List<Expression> args = new ArrayList<>();
                if(springAnnotation != null) {
                    args.add(value);
                    annBuilder = new StringBuilder("@" + springAnnotation + "(");
                    if(springAnnotation.equals("PathVariable")) {
                        annBuilder.append("#{any(String)})");
                    } else {
                        if(defaultValue == null) {
                            annBuilder.append("value = #{any(String)}, required = false)");
                        } else {
                            annBuilder.append("value = #{any(String)}, defaultValue = #{any()})");
                            args.add(defaultValue);
                        }
                    }
                } else if(!isContextParam && isPutOrPost) {
                    if(needRequestPart) {
                        springAnnotation = "RequestPart";
                    } else {
                        springAnnotation = "RequestBody";
                    }
                    annBuilder = new StringBuilder("@" + springAnnotation);
                }
                if(annBuilder != null) {
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
                    if(annType.equals("jakarta.ws.rs.Path") || annType.equals("javax.ws.rs.Path")) {
                        path = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    } else if(annType.equals("jakarta.ws.rs.Consumes") || annType.equals("javax.ws.rs.Consumes")) {
                        consumes = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    } else if(annType.equals("jakarta.ws.rs.Produces") || annType.equals("javax.ws.rs.Produces")) {
                        produces = getValueFromAnnotation(ann);
                        doAfterVisit(new RemoveAnnotationVisitor(new AnnotationMatcher("@" + annType)));
                        maybeRemoveImport(annType);
                    }
                }

                if(path != null && produces == null && consumes == null) {
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
                if(ann.getArguments() == null || ann.getArguments().isEmpty()) {
                    return null;
                }
                Expression arg = ann.getArguments().get(0);
                if(arg instanceof J.Assignment assign) {
                    return assign.getAssignment();
                } else {
                    return arg;
                }
            }

            private boolean isMultiPart(Expression consumes) {
                if(consumes == null) {
                    return false;
                }
                if(consumes instanceof J.NewArray arr) {
                    if(arr.getInitializer().size() == 1) {
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
