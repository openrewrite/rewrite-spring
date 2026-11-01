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
package org.openrewrite.java.spring.boot4;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.ImplementInterface;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.search.SemanticallyEqual;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypedTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class MigrateOriginLookupMetadata extends Recipe {
    private static final String ORIGIN_LOOKUP = "org.springframework.boot.origin.OriginLookup";
    private static final String PROPERTY_SOURCE_INFO = "org.springframework.boot.env.PropertySourceInfo";
    private static final MethodMatcher IMMUTABLE = new MethodMatcher(ORIGIN_LOOKUP + " isImmutable()", true);
    private static final MethodMatcher PREFIX = new MethodMatcher(ORIGIN_LOOKUP + " getPrefix()", true);

    @Getter
    final String displayName = "Migrate `OriginLookup` metadata to `PropertySourceInfo`";

    @Getter
    final String description = "Add `PropertySourceInfo` to direct `OriginLookup` implementations that override metadata methods, " +
            "and migrate their metadata forwarding guards, casts and default-method calls. " +
            "Preserve `OriginLookup` and `getOrigin`. Standalone callers and unsupported forwarding shapes are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ORIGIN_LOOKUP, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (!TypeUtils.isAssignableTo(ORIGIN_LOOKUP, cd.getType()) ||
                        TypeUtils.isAssignableTo(PROPERTY_SOURCE_INFO, cd.getType()) || cd.getImplements() == null ||
                        cd.getImplements().stream().noneMatch(t -> TypeUtils.isOfClassType(t.getType(), ORIGIN_LOOKUP))) {
                    return cd;
                }
                boolean metadata = false;
                for (Statement statement : cd.getBody().getStatements()) {
                    if (statement instanceof J.MethodDeclaration && isMetadata((J.MethodDeclaration) statement)) {
                        metadata = true;
                        if (!supported((J.MethodDeclaration) statement)) {
                            return cd;
                        }
                    }
                }
                if (!metadata) {
                    return cd;
                }
                maybeAddImport(PROPERTY_SOURCE_INFO);
                cd = (J.ClassDeclaration) new ImplementInterface<ExecutionContext>(cd, PROPERTY_SOURCE_INFO)
                        .visitNonNull(cd, ctx, getCursor().getParentOrThrow());
                updateCursor(cd);
                Cursor bodyCursor = new Cursor(getCursor(), cd.getBody());
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                    if (!(statement instanceof J.MethodDeclaration) || !isMetadata((J.MethodDeclaration) statement)) {
                        return statement;
                    }
                    return (Statement) new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
                            J.InstanceOf i = super.visitInstanceOf(instanceOf, ctx);
                            if (i.getClazz() instanceof TypedTree && TypeUtils.isOfClassType(((TypedTree) i.getClazz()).getType(), ORIGIN_LOOKUP)) {
                                return JavaTemplate.builder("#{any()} instanceof PropertySourceInfo")
                                        .imports(PROPERTY_SOURCE_INFO)
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-boot-4", "spring-core-7"))
                                        .build().apply(getCursor(), i.getCoordinates().replace(), i.getExpression());
                            }
                            return i;
                        }

                        @Override
                        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                            if (!IMMUTABLE.matches(method) && !PREFIX.matches(method)) {
                                return m;
                            }
                            Expression select = m.getSelect();
                            if (select != null && select.unwrap() instanceof J.TypeCast) {
                                J.TypeCast cast = (J.TypeCast) select.unwrap();
                                return JavaTemplate.builder("((PropertySourceInfo) #{any()})." + m.getSimpleName() + "()")
                                        .imports(PROPERTY_SOURCE_INFO)
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-boot-4", "spring-core-7"))
                                        .build().apply(getCursor(), m.getCoordinates().replace(), cast.getExpression());
                            }
                            return JavaTemplate.builder("PropertySourceInfo.super." + m.getSimpleName() + "()")
                                    .contextSensitive()
                                    .imports(PROPERTY_SOURCE_INFO)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-boot-4", "spring-core-7"))
                                    .build().apply(getCursor(), m.getCoordinates().replace());
                        }
                    }.visitNonNull(statement, ctx, bodyCursor);
                })));
                return cd;
            }

            private boolean isMetadata(J.MethodDeclaration method) {
                JavaType.Method type = method.getMethodType();
                return type != null && !method.hasModifier(J.Modifier.Type.Static) &&
                        ((IMMUTABLE.matches(type) && type.getReturnType() == JavaType.Primitive.Boolean) ||
                         (PREFIX.matches(type) && TypeUtils.isString(type.getReturnType())));
            }

            private boolean supported(J.MethodDeclaration method) {
                // Keep mixed origin/metadata logic intact; only recognize the complete forwarding idiom.
                boolean[] usesOrigin = {false};
                int[] metadataCalls = {0};
                new JavaIsoVisitor<boolean[]>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation call, boolean[] found) {
                        if (IMMUTABLE.matches(call) || PREFIX.matches(call)) {
                            found[0] = true;
                            metadataCalls[0]++;
                        }
                        return super.visitMethodInvocation(call, found);
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier id, boolean[] found) {
                        if (TypeUtils.isOfClassType(id.getType(), ORIGIN_LOOKUP)) {
                            found[0] = true;
                        }
                        return super.visitIdentifier(id, found);
                    }
                }.visit(method, usesOrigin);
                if (!usesOrigin[0]) {
                    return true;
                }
                if (method.getBody() == null) {
                    return false;
                }
                List<Statement> statements = method.getBody().getStatements();
                if (statements.size() == 1) {
                    return metadataCalls[0] == 1 && isDefaultCall(statements.get(0));
                }
                if (metadataCalls[0] != 2 || statements.size() != 2 || !(statements.get(0) instanceof J.If) || !isDefaultCall(statements.get(1))) {
                    return false;
                }
                J.If guard = (J.If) statements.get(0);
                if (guard.getElsePart() != null || !(guard.getIfCondition().getTree() instanceof J.InstanceOf)) {
                    return false;
                }
                J.InstanceOf test = (J.InstanceOf) guard.getIfCondition().getTree();
                if (!(test.getClazz() instanceof TypedTree) ||
                        !TypeUtils.isOfClassType(((TypedTree) test.getClazz()).getType(), ORIGIN_LOOKUP) || test.getPattern() != null) {
                    return false;
                }
                Statement then = guard.getThenPart();
                if (then instanceof J.Block) {
                    if (((J.Block) then).getStatements().size() != 1) {
                        return false;
                    }
                    then = ((J.Block) then).getStatements().get(0);
                }
                if (!(then instanceof J.Return) || !(((J.Return) then).getExpression() instanceof J.MethodInvocation)) {
                    return false;
                }
                J.MethodInvocation call = (J.MethodInvocation) ((J.Return) then).getExpression();
                if ((!IMMUTABLE.matches(call) && !PREFIX.matches(call)) || call.getSelect() == null ||
                        !(call.getSelect().unwrap() instanceof J.TypeCast)) {
                    return false;
                }
                J.TypeCast cast = (J.TypeCast) call.getSelect().unwrap();
                return TypeUtils.isOfClassType(cast.getType(), ORIGIN_LOOKUP) &&
                        SemanticallyEqual.areEqual(test.getExpression(), cast.getExpression());
            }

            private boolean isDefaultCall(Statement statement) {
                if (!(statement instanceof J.Return) || !(((J.Return) statement).getExpression() instanceof J.MethodInvocation)) {
                    return false;
                }
                J.MethodInvocation call = (J.MethodInvocation) ((J.Return) statement).getExpression();
                return (IMMUTABLE.matches(call) || PREFIX.matches(call)) && call.getSelect() instanceof J.FieldAccess &&
                        "super".equals(((J.FieldAccess) call.getSelect()).getSimpleName()) &&
                        TypeUtils.isOfClassType(((J.FieldAccess) call.getSelect()).getTarget().getType(), ORIGIN_LOOKUP);
            }
        });
    }
}
