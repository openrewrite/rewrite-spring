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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;


@RequiredArgsConstructor
public class AutowiredFieldIntoConstructorParameterVisitor extends JavaVisitor<ExecutionContext> {
    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private final String classFqName;
    private final String fieldName;

    @Override
    public J visitClassDeclaration(ClassDeclaration classDecl, ExecutionContext ctx) {
        if (TypeUtils.isOfClassType(classDecl.getType(), classFqName)) {
            List<MethodDeclaration> constructors = classDecl.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(MethodDeclaration::isConstructor)
                    .collect(toList());
            boolean applicable = false;
            if (constructors.isEmpty()) {
                applicable = true;
            } else if (constructors.size() == 1) {
                MethodDeclaration c = constructors.get(0);
                getCursor().putMessage("applicableConstructor", c);
                applicable = isNotConstructorInitializingField(c, fieldName);
            } else {
                List<MethodDeclaration> autowiredConstructors = constructors.stream()
                        .filter(constr -> service(AnnotationService.class).isAnnotatedWith(constr, AUTOWIRED))
                        .limit(2)
                        .collect(toList());
                if (autowiredConstructors.size() == 1) {
                    MethodDeclaration c = autowiredConstructors.get(0);
                    getCursor().putMessage("applicableConstructor", autowiredConstructors.get(0));
                    applicable = isNotConstructorInitializingField(c, fieldName);
                }
            }
            if (applicable) {
                // visit contents if there is applicable constructor
                return super.visitClassDeclaration(classDecl, ctx);
            }
        }
        return classDecl;
    }

    public static boolean isNotConstructorInitializingField(MethodDeclaration c, String fieldName) {
        return c.getBody() == null || c.getBody().getStatements().stream().filter(J.Assignment.class::isInstance).map(J.Assignment.class::cast).noneMatch(a -> {
            Expression expr = a.getVariable();
            if (expr instanceof J.FieldAccess) {
                J.FieldAccess fa = (J.FieldAccess) expr;
                if (fieldName.equals(fa.getSimpleName()) && fa.getTarget() instanceof J.Identifier) {
                    J.Identifier target = (J.Identifier) fa.getTarget();
                    if ("this".equals(target.getSimpleName())) {
                        return true;
                    }
                }
            }
            if (expr instanceof J.Identifier) {
                JavaType.Variable fieldType = c.getMethodType().getDeclaringType().getMembers().stream().filter(v -> fieldName.equals(v.getName())).findFirst().orElse(null);
                if (fieldType != null) {
                    J.Identifier identifier = (J.Identifier) expr;
                    return fieldType.equals(identifier.getFieldType());
                }
            }
            return false;
        });
    }

    @Override
    public J visitVariableDeclarations(VariableDeclarations multiVariable, ExecutionContext p) {
        Cursor blockCursor = getCursor().dropParentUntil(it -> it instanceof J.Block || it == Cursor.ROOT_VALUE);
        if (!(blockCursor.getValue() instanceof J.Block)) {
            return multiVariable;
        }
        VariableDeclarations mv = multiVariable;
        if (blockCursor.getParent() != null && blockCursor.getParent().getValue() instanceof ClassDeclaration &&
                multiVariable.getVariables().size() == 1 &&
                fieldName.equals(multiVariable.getVariables().get(0).getName().getSimpleName())) {

            mv = (VariableDeclarations) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + AUTOWIRED)).visit(multiVariable, p, getCursor().getParentOrThrow());
            if (mv != multiVariable && multiVariable.getTypeExpression() != null) {
                if (mv.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
                    Space prefix = Space.firstPrefix(mv.getVariables());
                    J.Modifier m = new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList());
                    if (mv.getModifiers().isEmpty()) {
                        mv = mv.withTypeExpression(mv.getTypeExpression().withPrefix(prefix));
                    } else {
                        m = m.withPrefix(prefix);
                    }
                    mv = mv.withModifiers(ListUtils.concat(mv.getModifiers(), m));
                }
                maybeRemoveImport(AUTOWIRED);
                MethodDeclaration constructor = blockCursor.getParent().getMessage("applicableConstructor");
                ClassDeclaration c = blockCursor.getParent().getValue();
                TypeTree parameterType = mv.getTypeExpression();
                if (constructor == null) {
                    doAfterVisit(new AddConstructorVisitor(c.getSimpleName(), fieldName, parameterType));
                } else {
                    doAfterVisit(new AddConstructorParameterAndAssignment(constructor, fieldName, parameterType));
                }
            }
        }
        return mv;
    }


    @RequiredArgsConstructor
    private static class AddConstructorVisitor extends JavaVisitor<ExecutionContext> {
        private final String className;
        private final String fieldName;
        private final TypeTree type;

        @Override
        public J visitBlock(Block block, ExecutionContext p) {
            if (getCursor().getParent() != null) {
                Object n = getCursor().getParent().getValue();
                if (n instanceof ClassDeclaration) {
                    ClassDeclaration classDecl = (ClassDeclaration) n;
                    JavaType fieldType = type.getType();
                    if (fieldType != null && !(fieldType instanceof JavaType.Primitive) && classDecl.getKind() == ClassDeclaration.Kind.Type.Class && className.equals(classDecl.getSimpleName())) {
                        JavaTemplate.Builder template = JavaTemplate.builder("" +
                                classDecl.getSimpleName() + "(" + type + " " + fieldName + ") {\n" +
                                "this." + fieldName + " = " + fieldName + ";\n" +
                                "}\n"
                        ).contextSensitive();
                        FullyQualified fq = TypeUtils.asFullyQualified(type.getType());
                        if (fq != null) {
                            template.imports(fq.getFullyQualifiedName());
                            maybeAddImport(fq);
                        }
                        Optional<Statement> firstMethod = block.getStatements().stream().filter(MethodDeclaration.class::isInstance).findFirst();

                        Block applied = firstMethod
                                .map(statement -> (Block) template.build()
                                        .apply(getCursor(), statement.getCoordinates().before()))
                                .orElseGet(() -> (Block) template.build()
                                        .apply(getCursor(), block.getCoordinates().lastStatement()));
                        return applied.withStatements(ListUtils.map(applied.getStatements(), s -> {
                            if (s instanceof MethodDeclaration && ((MethodDeclaration) s).isConstructor()) {
                                MethodDeclaration ctor = typeAddedParameter((MethodDeclaration) s, fieldName, type);
                                return typeAssignmentReference(ctor, fieldName, fieldType);
                            }
                            return s;
                        }));
                    }
                }
            }
            return block;
        }
    }

    private static class AddConstructorParameterAndAssignment extends JavaIsoVisitor<ExecutionContext> {
        private final MethodDeclaration constructor;
        private final String fieldName;
        private final TypeTree type;
        private final String methodType;

        public AddConstructorParameterAndAssignment(MethodDeclaration constructor, String fieldName, TypeTree type) {
            this.constructor = constructor;
            this.fieldName = fieldName;
            this.type = type;
            // Render the parameter type exactly as the field declares it: simple names stay valid through the
            // file's existing imports, and deliberately fully qualified references stay fully qualified.
            this.methodType = type.toString();
        }

        @Override
        public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext p) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, p);
            if (md == this.constructor && md.getBody() != null) {
                List<J> params = md.getParameters().stream().filter(s -> !(s instanceof J.Empty)).collect(toList());
                String paramsStr = Stream.concat(params.stream()
                        .map(s -> "#{}"), Stream.of(methodType + " " + fieldName)).collect(joining(", "));

                md = JavaTemplate.builder(paramsStr)
                        .contextSensitive()
                        .build()
                        .apply(
                                getCursor(),
                                md.getCoordinates().replaceParameters(),
                                params.toArray()
                        );
                // A type the template parser could not resolve (a third-party or project-local type absent from the
                // parser's classpath) comes back unattributed. Graft the field's own resolved type onto the parameter
                // so the constructor is well typed regardless of what the isolated parser could see.
                md = typeAddedParameter(md, fieldName, type);
                updateCursor(md);

                //noinspection ConstantConditions
                md = JavaTemplate.builder("this." + fieldName + " = " + fieldName + ";")
                        .contextSensitive()
                        .build()
                        .apply(
                                getCursor(),
                                md.getBody().getCoordinates().lastStatement()
                        );
                md = typeAssignmentReference(md, fieldName, type.getType());
            }
            return md;
        }
    }

    /**
     * Replace the generated parameter's type expression with the field's own (already resolved) {@link TypeTree} when
     * the template parser left it unattributed, and rebuild the constructor's {@link JavaType.Method} from the
     * resulting parameter types. This makes the constructor well typed for types the isolated parser cannot resolve
     * (third-party types or project-local sibling types not on its classpath).
     */
    private static MethodDeclaration typeAddedParameter(MethodDeclaration md, String fieldName, TypeTree fieldType) {
        JavaType resolved = fieldType.getType();
        if (resolved == null) {
            return md;
        }
        List<Statement> parameters = ListUtils.map(md.getParameters(), parameter -> {
            if (parameter instanceof J.VariableDeclarations) {
                J.VariableDeclarations vd = (J.VariableDeclarations) parameter;
                if (vd.getVariables().size() == 1 &&
                        fieldName.equals(vd.getVariables().get(0).getSimpleName()) &&
                        !TypeUtils.isWellFormedType(vd.getTypeExpression() == null ? null : vd.getTypeExpression().getType())) {
                    Space prefix = vd.getTypeExpression() == null ? Space.EMPTY : vd.getTypeExpression().getPrefix();
                    J.VariableDeclarations.NamedVariable namedVariable = vd.getVariables().get(0);
                    namedVariable = namedVariable.withName(namedVariable.getName().withType(resolved));
                    if (namedVariable.getVariableType() != null) {
                        namedVariable = namedVariable.withVariableType(namedVariable.getVariableType().withType(resolved));
                    }
                    return vd.withTypeExpression(fieldType.withPrefix(prefix)).withVariables(singletonList(namedVariable));
                }
            }
            return parameter;
        });
        md = md.withParameters(parameters);

        JavaType.Method methodType = md.getMethodType();
        if (methodType != null) {
            List<JavaType> parameterTypes = new ArrayList<>(parameters.size());
            for (Statement parameter : parameters) {
                if (parameter instanceof J.VariableDeclarations) {
                    TypeTree typeExpression = ((J.VariableDeclarations) parameter).getTypeExpression();
                    parameterTypes.add(typeExpression == null ? null : typeExpression.getType());
                }
            }
            if (!parameterTypes.contains(null)) {
                methodType = methodType.withParameterTypes(parameterTypes);
                md = md.withMethodType(methodType).withName(md.getName().withType(methodType));
            }
        }
        return md;
    }

    /**
     * Attribute the right-hand side of the generated {@code this.<field> = <field>} assignment. The template parser
     * leaves the reference untyped for array-typed parameters, so set the identifier's type from the field's own
     * (already resolved) type; the reference and the parameter share it.
     */
    private static MethodDeclaration typeAssignmentReference(MethodDeclaration md, String fieldName, @Nullable JavaType fieldType) {
        if (fieldType == null || md.getBody() == null) {
            return md;
        }
        return md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(), statement -> {
            if (statement instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) statement;
                Expression rhs = assignment.getAssignment();
                if (rhs instanceof J.Identifier &&
                        fieldName.equals(((J.Identifier) rhs).getSimpleName()) &&
                        !TypeUtils.isWellFormedType(rhs.getType())) {
                    return assignment.withAssignment(((J.Identifier) rhs).withType(fieldType));
                }
            }
            return statement;
        })));
    }
}
