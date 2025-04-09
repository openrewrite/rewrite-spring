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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static org.openrewrite.java.MethodMatcher.methodPattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class RenameBean extends ScanningRecipe<List<TreeVisitor<?, ExecutionContext>>> {

    @Option(required = false, example = "foo.MyType")
    @Nullable
    String type;

    @Option(example = "fooBean")
    String oldName;

    @Option(example = "barBean")
    String newName;

    private static final String FQN_QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";

    private static final String FQN_BEAN = "org.springframework.context.annotation.Bean";

    private static final String FQN_COMPONENT = "org.springframework.stereotype.Component";

    private static final Set<String> JUST_QUALIFIER = Collections.singleton(FQN_QUALIFIER);
    private static final Set<String> BEAN_METHOD_ANNOTATIONS = new HashSet<String>() {{
        add(FQN_QUALIFIER);
        add(FQN_BEAN);
    }};

    private static final Set<String> BEAN_TYPE_ANNOTATIONS = new HashSet<String>() {{
        add(FQN_QUALIFIER);
        add(FQN_COMPONENT);
    }};

    @Override
    public String getDisplayName() {
        return "Rename bean";
    }

    @Override
    public String getDescription() {
        return "Renames a Spring bean, both declaration and references.";
    }

    @Override
    public List<TreeVisitor<?, ExecutionContext>> getInitialValue(ExecutionContext ctx) {
        return new ArrayList<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(List<TreeVisitor<?, ExecutionContext>> acc) {

        return Preconditions.check(precondition(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                // handle beans named via methods
                List<J.Annotation> allAnnotations = service(AnnotationService.class).getAllAnnotations(getCursor());
                Expression beanNameExpression = getBeanNameExpression(allAnnotations, BEAN_METHOD_ANNOTATIONS);
                if (beanNameExpression == null && isRelevantType(m.getMethodType().getReturnType()) && m.getSimpleName().equals(oldName)) {
                    acc.add(new ChangeMethodName(methodPattern(m), newName, true, false).getVisitor());
                }

                // handle annotation renames
                acc.add(renameBeanAnnotations(BEAN_METHOD_ANNOTATIONS));
                return m;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                List<J.Annotation> allAnnotations = service(AnnotationService.class).getAllAnnotations(getCursor());
                Expression beanNameExpression = getBeanNameExpression(allAnnotations, BEAN_TYPE_ANNOTATIONS);

                // handle bean named via class name
                if (beanNameExpression == null && isRelevantType(cd.getType()) && StringUtils.uncapitalize(cd.getSimpleName()).equals(oldName)) {
                    String newFullyQualifiedTypeName = cd.getType().getFullyQualifiedName()
                            .replaceAll("^((.+\\.)*)[^.]+$", "$1" + StringUtils.capitalize(newName));
                    acc.add(new ChangeType(cd.getType().getFullyQualifiedName(), newFullyQualifiedTypeName, false, null).getVisitor());
                    acc.add(new ChangeType(cd.getType().getFullyQualifiedName() + "Test", newFullyQualifiedTypeName + "Test", false, null).getVisitor());
                }

                // handle annotation renames
                acc.add(renameBeanAnnotations(BEAN_TYPE_ANNOTATIONS));

                return cd;
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(List<TreeVisitor<?, ExecutionContext>> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit newCu = super.visitCompilationUnit(cu, ctx);
                for (TreeVisitor<?, ExecutionContext> visitor : acc) {
                    newCu = (J.CompilationUnit) visitor.visit(newCu, ctx);
                }
                return newCu;
            }
        };
    }

    /**
     * @param methodDeclaration, which may or may not declare a bean
     * @param newName,           for the potential bean
     * @return a recipe for this methodDeclaration if it declares a bean, or null if it does not declare a bean
     */
    public static @Nullable RenameBean fromDeclaration(J.MethodDeclaration methodDeclaration, String newName) {
        return methodDeclaration.getMethodType() == null ? null :
                fromDeclaration(methodDeclaration, newName, methodDeclaration.getMethodType().getReturnType().toString());
    }

    /**
     * @param methodDeclaration, which may or may not declare a bean
     * @param newName,           for the potential bean
     * @param type,              to override the type field on the returned RenameBean instance
     * @return a recipe for this methodDeclaration if it declares a bean, or null if it does not declare a bean
     */
    public static @Nullable RenameBean fromDeclaration(J.MethodDeclaration methodDeclaration, String newName, @Nullable String type) {
        BeanSearchResult beanSearchResult = isBean(methodDeclaration.getAllAnnotations(), BEAN_METHOD_ANNOTATIONS);
        if (!beanSearchResult.isBean || methodDeclaration.getMethodType() == null) {
            return null;
        }
        String beanName =
                beanSearchResult.beanName != null ? beanSearchResult.beanName : methodDeclaration.getSimpleName();
        return beanName.equals(newName) ? null : new RenameBean(type, beanName, newName);
    }

    /**
     * @param classDeclaration, which may or may not declare a bean
     * @param newName,          for the potential bean
     * @return a recipe for this classDeclaration if it declares a bean, or null if it does not declare a bean
     */
    public static @Nullable RenameBean fromDeclaration(J.ClassDeclaration classDeclaration, String newName) {
        return classDeclaration.getType() == null ? null :
                fromDeclaration(classDeclaration, newName, classDeclaration.getType().toString());
    }

    /**
     * @param classDeclaration, which may or may not declare a bean
     * @param newName,          for the potential bean
     * @param type,             to override the type field on the returned RenameBean instance
     * @return a recipe for this classDeclaration if it declares a bean, or null if it does not declare a bean
     */
    public static @Nullable RenameBean fromDeclaration(J.ClassDeclaration classDeclaration, String newName, @Nullable String type) {
        BeanSearchResult beanSearchResult = isBean(classDeclaration.getAllAnnotations(), BEAN_TYPE_ANNOTATIONS);
        if (!beanSearchResult.isBean || classDeclaration.getType() == null) {
            return null;
        }
        String beanName =
                beanSearchResult.beanName != null ? beanSearchResult.beanName : StringUtils.uncapitalize(classDeclaration.getSimpleName());
        return beanName.equals(newName) ? null : new RenameBean(type, beanName, newName);
    }

    private static BeanSearchResult isBean(Collection<J.Annotation> annotations, Set<String> types) {
        for (J.Annotation annotation : annotations) {
            if (anyAnnotationMatches(annotation, types)) {
                if (annotation.getArguments() != null && !annotation.getArguments().isEmpty()) {
                    for (Expression expr : annotation.getArguments()) {
                        if (expr instanceof J.Literal) {
                            return new BeanSearchResult(true, (String) ((J.Literal) expr).getValue());
                        }
                        J.Assignment beanNameAssignment = asBeanNameAssignment(expr);
                        if (beanNameAssignment != null) {
                            Expression assignmentExpr = beanNameAssignment.getAssignment();
                            if (assignmentExpr instanceof J.Literal) {
                                return new BeanSearchResult(true, (String) ((J.Literal) assignmentExpr).getValue());
                            } else if (assignmentExpr instanceof J.NewArray) {
                                List<Expression> initializers = ((J.NewArray) assignmentExpr).getInitializer();
                                if (initializers != null) {
                                    for (Expression initExpr : initializers) {
                                        // if multiple aliases, just take the first one
                                        if (initExpr instanceof J.Literal) {
                                            return new BeanSearchResult(true,
                                                    (String) ((J.Literal) initExpr).getValue());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return new BeanSearchResult(true, null);
            }
        }
        return new BeanSearchResult(false, null);
    }

    private static boolean anyAnnotationMatches(J.Annotation type, Set<String> types) {
        for (String it : types) {
            if (!FindAnnotations.find(type, '@' + it, true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static class BeanSearchResult {
        public boolean isBean;

        @Nullable
        public String beanName;

        public BeanSearchResult(boolean isBean, @Nullable String beanName) {
            this.isBean = isBean;
            this.beanName = beanName;
        }
    }

    private TreeVisitor<?, ExecutionContext> precondition() {
        return type == null ?
                Preconditions.or(
                        new FindAnnotations("@" + FQN_QUALIFIER, false).getVisitor(),
                        new FindAnnotations("@" + FQN_BEAN, false).getVisitor(),
                        new FindAnnotations("@" + FQN_COMPONENT, true).getVisitor()) :
                Preconditions.or(new UsesType<>(type, false), new DeclaresType<>(type));
    }

    private @Nullable Expression getBeanNameExpression(Collection<J.Annotation> annotations, Set<String> types) {
        for (J.Annotation annotation : annotations) {
            if (anyAnnotationMatches(annotation, types)) {
                if (annotation.getArguments() != null && !annotation.getArguments().isEmpty()) {
                    for (Expression expr : annotation.getArguments()) {
                        if (expr instanceof J.Literal) {
                            return expr;
                        }
                        J.Assignment beanNameAssignment = asBeanNameAssignment(expr);
                        if (beanNameAssignment != null) {
                            return beanNameAssignment;
                        }
                    }
                }
            }
        }
        return null;
    }

    private TreeVisitor<J, ExecutionContext> renameBeanAnnotations(Set<String> types) {
        return new JavaIsoVisitor<ExecutionContext>() {
            private boolean annotationParentMatchesBeanType() {
                if (getCursor().getParent() != null) {
                    Object annotationParent = getCursor().getParent().getValue();

                    if (annotationParent instanceof J.MethodDeclaration) {
                        return isRelevantType(((J.MethodDeclaration) annotationParent).getMethodType().getReturnType());
                    } else if (annotationParent instanceof J.ClassDeclaration) {
                        return isRelevantType(((J.ClassDeclaration) annotationParent).getType());
                    } else if (annotationParent instanceof J.VariableDeclarations) {
                        return isRelevantType(((J.VariableDeclarations) annotationParent).getType());
                    }
                }
                return false;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                Expression beanNameExpression = getBeanNameExpression(Collections.singleton(annotation), types);

                if (beanNameExpression != null && annotationParentMatchesBeanType()) {
                    if (beanNameExpression instanceof J.Literal) {
                        if (oldName.equals(((J.Literal) beanNameExpression).getValue())) {
                            doAfterVisit(renameBeanAnnotationValue(annotation));
                        }
                    } else if (beanNameExpression instanceof J.Assignment) {
                        J.Assignment beanNameAssignment = (J.Assignment) beanNameExpression;
                        if (contains(beanNameAssignment.getAssignment(), oldName)) {
                            doAfterVisit(renameBeanAnnotationValue(annotation, beanNameAssignment));
                        }
                    }
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };
    }

    private static J.@Nullable Assignment asBeanNameAssignment(Expression argumentExpression) {
        if (argumentExpression instanceof J.Assignment) {
            Expression variable = ((J.Assignment) argumentExpression).getVariable();
            if (variable instanceof J.Identifier) {
                String variableName = ((J.Identifier) variable).getSimpleName();
                if (variableName.equals("name") || variableName.equals("value")) {
                    return (J.Assignment) argumentExpression;
                }
            }
        }
        return null;
    }

    private TreeVisitor<J, ExecutionContext> renameBeanAnnotationValue(
            J.Annotation beanAnnotation, J.Assignment beanNameAssignment) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (a == beanAnnotation) {
                    a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
                        if (arg == beanNameAssignment) {
                            return beanNameAssignment.withAssignment(
                                    replace(beanNameAssignment.getAssignment(), oldName, newName));
                        }
                        return arg;
                    }));
                }
                return a;
            }
        };
    }

    private TreeVisitor<J, ExecutionContext> renameBeanAnnotationValue(J.Annotation beanAnnotation) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (a == beanAnnotation) {
                    a = a.withArguments(ListUtils.map(a.getArguments(), arg -> replace(arg, oldName, newName)));
                }
                return a;
            }
        };
    }

    private Expression replace(Expression assignment, String oldName, String newName) {
        if (assignment instanceof J.Literal) {
            J.Literal literalAssignment = (J.Literal) assignment;
            if (oldName.equals(literalAssignment.getValue())) {
                return literalAssignment.withValue(newName).withValueSource("\"" + newName + "\"");
            }
        } else if (assignment instanceof J.NewArray) {
            J.NewArray newArrayAssignment = (J.NewArray) assignment;
            return newArrayAssignment.withInitializer(
                    ListUtils.map(newArrayAssignment.getInitializer(), expr -> replace(expr, oldName, newName)));
        }
        return assignment;
    }

    private static boolean contains(Expression assignment, String oldName) {
        if (assignment instanceof J.Literal) {
            return oldName.equals(((J.Literal) assignment).getValue());
        } else if (assignment instanceof J.NewArray) {
            J.NewArray newArrayAssignment = (J.NewArray) assignment;
            if (newArrayAssignment.getInitializer() == null) {
                return false;
            }
            for (Expression it : newArrayAssignment.getInitializer()) {
                if (contains(it, oldName)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean isRelevantType(@Nullable JavaType javaType) {
        return this.type == null || TypeUtils.isOfClassType(javaType, this.type);
    }
}
