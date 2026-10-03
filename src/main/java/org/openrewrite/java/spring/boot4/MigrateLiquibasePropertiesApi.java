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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;

public class MigrateLiquibasePropertiesApi extends Recipe {
    private static final MethodMatcher SET_CONTEXTS = new MethodMatcher("liquibase.integration.spring.SpringLiquibase setContexts(String)", true);
    private static final MethodMatcher SET_LABELS = new MethodMatcher("liquibase.integration.spring.SpringLiquibase setLabels(String)", true);
    private static final MethodMatcher SET_LABEL_FILTER = new MethodMatcher("liquibase.integration.spring.SpringLiquibase setLabelFilter(String)", true);

    @Getter
    final String displayName = "Migrate Liquibase property values passed to SpringLiquibase";

    @Getter
    final String description = "Convert the former String contexts and labels properties to comma-delimited values when passed directly to SpringLiquibase setters, " +
            "and replace setLabels with setLabelFilter. Already collection-aware code is retained.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(new UsesMethod<>(SET_CONTEXTS), new UsesMethod<>(SET_LABELS), new UsesMethod<>(SET_LABEL_FILTER)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        boolean contexts = SET_CONTEXTS.matches(m);
                        boolean labels = SET_LABELS.matches(m);
                        if ((contexts || labels || SET_LABEL_FILTER.matches(m)) && m.getArguments().size() == 1 &&
                                m.getArguments().get(0) instanceof J.MethodInvocation) {
                            J.MethodInvocation getter = (J.MethodInvocation) m.getArguments().get(0);
                            JavaType.Method getterType = getter.getMethodType();
                            if (getterType != null && getterType.getParameterTypes().isEmpty() && TypeUtils.isString(getter.getType()) &&
                                    (TypeUtils.isAssignableTo("org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties", getterType.getDeclaringType()) ||
                                     TypeUtils.isAssignableTo("org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties", getterType.getDeclaringType())) &&
                                    (contexts ? "getContexts".equals(getter.getSimpleName()) :
                                     "getLabels".equals(getter.getSimpleName()) || "getLabelFilter".equals(getter.getSimpleName()))) {
                                String getterName = contexts ? "getContexts" : "getLabelFilter";
                                getterType = getterType.withName(getterName).withReturnType(JavaType.buildType("java.util.List"));
                                getter = getter.withName(getter.getName().withSimpleName(getterName).withType(getterType))
                                        .withMethodType(getterType);
                                maybeAddImport("org.springframework.util.StringUtils");
                                J.MethodInvocation converted = JavaTemplate.builder("StringUtils.collectionToCommaDelimitedString(#{any(java.util.Collection)})")
                                        .imports("org.springframework.util.StringUtils")
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-core-6"))
                                        .build()
                                        .apply(new Cursor(updateCursor(m), getter), getter.getCoordinates().replace(), getter);
                                m = m.withArguments(Collections.singletonList(converted));
                            }
                        }
                        if (labels && m.getMethodType() != null) {
                            JavaType.Method setterType = m.getMethodType().withName("setLabelFilter");
                            m = m.withName(m.getName().withSimpleName("setLabelFilter").withType(setterType))
                                    .withMethodType(setterType);
                        }
                        return m;
                    }
                });
    }
}
