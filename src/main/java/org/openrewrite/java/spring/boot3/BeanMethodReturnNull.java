/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class BeanMethodReturnNull extends Recipe {
    protected static final String BEAN_ANNOTATION_FQN = "org.springframework.context.annotation.Bean";
    protected static final AnnotationMatcher BEAN_ANNOTATION_MATCHER = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
    private static final String MSG_RETURN_VOID = "RETURN_VOID";

    @Override
    public String getDisplayName() {
        return "Make all @Bean method to return non-void (null)";
    }

    @Override
    public String getDescription() {
        return "Make all @Bean method to return non-void (null).";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(BEAN_ANNOTATION_FQN, false),
          new BeanMethodReturnVoidVisitor());
    }

    private static class BeanMethodReturnVoidVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            // When the method is annotated with @Bean and it is not an override method
            if (md.getAllAnnotations().stream().anyMatch(BEAN_ANNOTATION_MATCHER::matches) &&
              !TypeUtils.isOverride(md.getMethodType()) &&
              md.getReturnTypeExpression() != null && md.getReturnTypeExpression().getType() == JavaType.Primitive.Void
            ) {
                md = md.withReturnTypeExpression(TypeTree.build(" Object"));
                // Add `return null;` if the method does not have a return statement
                List<Statement> statements = md.getBody().getStatements();
                if (statements.isEmpty() || !(statements.get(statements.size() - 1) instanceof J.Return)) {
                    md = JavaTemplate.builder("return null;")
                      .contextSensitive()
                      .build()
                      .apply(updateCursor(md), md.getBody().getCoordinates().lastStatement());
                }

                getCursor().putMessage(MSG_RETURN_VOID, true);
            }

            return super.visitMethodDeclaration(md, ctx);
        }

        // Change `return;` to `return null;`
        @Override
        public J.Return visitReturn(J.Return _return, ExecutionContext ctx) {
            if (_return.getExpression() == null) {
                boolean isReturnVoid = Boolean.TRUE.equals(getCursor().getNearestMessage(MSG_RETURN_VOID));
                if (isReturnVoid) {
                    _return = JavaTemplate.builder("return null;")
                      .contextSensitive()
                      .build()
                      .apply(updateCursor(_return), _return.getCoordinates().replace());
                }
            }
            return super.visitReturn(_return, ctx);
        }
    }
}

