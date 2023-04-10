package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Applicability;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.DeclaresType;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openrewrite.java.MethodMatcher.methodPattern;

@EqualsAndHashCode(callSuper = true)
@Value
public class RenameBean extends Recipe {

    String type;

    String oldName;

    String newName;

    private static final String QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";

    private static final Set<String> JUST_QUALIFER = Collections.singleton(QUALIFIER);

    private static final Set<String> BEAN_METHOD_ANNOTATIONS = new HashSet<String>() {{
        add(QUALIFIER);
        add("org.springframework.context.annotation.Bean");
    }};

    private static final Set<String> BEAN_TYPE_ANNOTATIONS = new HashSet<String>() {{
        add(QUALIFIER);
        add("org.springframework.context.annotation.Configuration");
        add("org.springframework.stereotype.Component");
        add("org.springframework.stereotype.Service");
        add("org.springframework.stereotype.Repository");
        add("org.springframework.stereotype.Controller");
        add("org.springframework.web.bind.annotaiton.RestController");
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
    public TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return Applicability.or(new UsesType<>(type, false), new DeclaresType<>(type));
    }

    /**
     * @param methodDeclaration, which may or may not declare a bean
     * @param newName, for the potential bean
     * @return a recipe for this methodDeclaration if it declares a bean, or null if it does not declare a bean
     */
    @Nullable
    public static RenameBean fromDeclaration(J.MethodDeclaration methodDeclaration, String newName) {
        BeanSearchResult beanSearchResult = isBean(methodDeclaration.getAllAnnotations(), BEAN_METHOD_ANNOTATIONS);
        if (!beanSearchResult.isBean || methodDeclaration.getMethodType() == null) {
            return null;
        }
        String beanName =
                beanSearchResult.beanName != null ? beanSearchResult.beanName : methodDeclaration.getSimpleName();
        return beanName.equals(newName) ? null :
                new RenameBean(methodDeclaration.getMethodType().getReturnType().toString(), beanName, newName);
    }

    /**
     * @param classDeclaration, which may or may not declare a bean
     * @param newName, for the potential bean
     * @return a recipe for this classDeclaration if it declares a bean, or null if it does not declare a bean
     */
    @Nullable
    public static RenameBean fromDeclaration(J.ClassDeclaration classDeclaration, String newName) {
        BeanSearchResult beanSearchResult = isBean(classDeclaration.getAllAnnotations(), BEAN_TYPE_ANNOTATIONS);
        if (!beanSearchResult.isBean || classDeclaration.getType() == null) {
            return null;
        }
        String beanName =
                beanSearchResult.beanName != null ? beanSearchResult.beanName : classDeclaration.getSimpleName();
        return beanName.equals(newName) ? null :
                new RenameBean(classDeclaration.getType().toString(), beanName, newName);
    }

    private static BeanSearchResult isBean(Collection<J.Annotation> annotations, Set<String> types) {
        for (J.Annotation annotation : annotations) {
            if (annotation.getType() != null && types.contains(annotation.getType().toString())) {
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

    private static class BeanSearchResult {
        public boolean isBean;

        @Nullable
        public String beanName;

        public BeanSearchResult(boolean isBean, @Nullable String beanName) {
            this.isBean = isBean;
            this.beanName = beanName;
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                    ExecutionContext executionContext) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

                // handle bean declarations
                if (m.getMethodType() != null && TypeUtils.isOfClassType(m.getMethodType().getReturnType(), type)) {
                    boolean maybeRenameMethodDeclaration = maybeRenameBean(m.getAllAnnotations(),
                            BEAN_METHOD_ANNOTATIONS);
                    if (maybeRenameMethodDeclaration && m.getSimpleName().equals(oldName)) {
                        doNext(new ChangeMethodName(methodPattern(m), newName, false, false));
                    }
                }

                // handle bean references (method params)
                for (Statement statement : m.getParameters()) {
                    renameMatchingQualifierAnnotations(statement);
                }

                return m;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                    ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

                // handle bean declarations
                if (cd.getType() != null && TypeUtils.isOfClassType(cd.getType(), type)) {
                    boolean maybeRenameClass = maybeRenameBean(cd.getAllAnnotations(), BEAN_TYPE_ANNOTATIONS);
                    if (maybeRenameClass && StringUtils.uncapitalize(cd.getSimpleName()).equals(oldName)) {
                        String newFullyQualifiedTypeName = cd.getType().getFullyQualifiedName()
                                .replaceAll("^((.+\\.)*)[^.]+$", "$1" + StringUtils.capitalize(newName));
                        doNext(new ChangeType(cd.getType().getFullyQualifiedName(), newFullyQualifiedTypeName, false));
                    }
                }

                // handle bean references (fields)
                for (Statement statement : cd.getBody().getStatements()) {
                    renameMatchingQualifierAnnotations(statement);
                }

                return cd;
            }

            private void renameMatchingQualifierAnnotations(Statement statement) {
                if (statement instanceof J.VariableDeclarations) {
                    J.VariableDeclarations varDecls = (J.VariableDeclarations) statement;
                    for (J.VariableDeclarations.NamedVariable namedVar : varDecls.getVariables()) {
                        if (namedVar.getType() != null && TypeUtils.isOfClassType(namedVar.getType(), type)) {
                            maybeRenameBean(varDecls.getAllAnnotations(), JUST_QUALIFER);
                            break;
                        }
                    }
                }
            }

            /**
             * Checks for presence of a bean-like annotation in the list of annotations,
             * and queues up a visitor to change that annotation's arguments if they match the oldName.
             *
             * @return true in the specific case where there are bean-like annotations,
             * but they don't determine the name of the bean, and therefore the J element itself should be renamed
             */
            private boolean maybeRenameBean(Collection<J.Annotation> annotations, Set<String> types) {
                J.Annotation beanAnnotation = null;
                J.Literal literalBeanName = null;
                J.Assignment beanNameAssignment = null;
                outer:
                for (J.Annotation annotation : annotations) {
                    if (annotation.getType() != null && types.contains(annotation.getType().toString())) {
                        beanAnnotation = annotation;
                        if (beanAnnotation.getArguments() != null && !beanAnnotation.getArguments().isEmpty()) {
                            for (Expression expr : beanAnnotation.getArguments()) {
                                if (expr instanceof J.Literal) {
                                    literalBeanName = (J.Literal) expr;
                                    break outer;
                                }
                                beanNameAssignment = asBeanNameAssignment(expr);
                                if (beanNameAssignment != null) {
                                    break outer;
                                }
                            }
                        }
                    }
                }
                if (beanAnnotation != null) {
                    if (literalBeanName != null) {
                        if (oldName.equals(literalBeanName.getValue())) {
                            doAfterVisit(renameBeanAnnotationValue(beanAnnotation));
                        }
                    } else if (beanNameAssignment != null) {
                        if (contains(beanNameAssignment.getAssignment(), oldName)) {
                            doAfterVisit(renameBeanAnnotationValue(beanAnnotation, beanNameAssignment));
                        }
                    } else {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    @Nullable
    private static J.Assignment asBeanNameAssignment(Expression argumentExpression) {
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

    private TreeVisitor<J, ExecutionContext> renameBeanAnnotationValue(J.Annotation beanAnnotation,
            J.Assignment beanNameAssignment) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                J.Annotation a = super.visitAnnotation(annotation, executionContext);
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
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                J.Annotation a = super.visitAnnotation(annotation, executionContext);
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
}
