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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.util.List;

/**
 * A recipe to preserve whitespace between parameters in method invocations and method declarations.
 * This is a workaround for a bug in the OpenRewrite library where whitespace between parameters
 * is not preserved when applying transformations.
 */
public class PreserveParameterWhitespace extends Recipe {
    @Override
    public String getDisplayName() {
        return "Preserve whitespace between parameters";
    }

    @Override
    public String getDescription() {
        return "Preserves whitespace between parameters in method invocations and method declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PreserveParameterWhitespaceVisitor();
    }

    private static class PreserveParameterWhitespaceVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            
            // Preserve whitespace between parameters
            if (m.getArguments() != null && m.getArguments().size() > 1) {
                m = m.withArguments(ListUtils.map(m.getArguments(), (i, arg) -> {
                    if (i > 0) {
                        // Ensure there's whitespace after the comma
                        if (arg.getPrefix().getWhitespace().isEmpty()) {
                            return arg.withPrefix(arg.getPrefix().withWhitespace(" "));
                        }
                    }
                    return arg;
                }));
            }
            
            return m;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            
            // Preserve whitespace between parameters
            if (m.getParameters() != null && m.getParameters().size() > 1) {
                m = m.withParameters(ListUtils.map(m.getParameters(), (i, param) -> {
                    if (i > 0) {
                        // Ensure there's whitespace after the comma
                        if (param.getPrefix().getWhitespace().isEmpty()) {
                            return param.withPrefix(param.getPrefix().withWhitespace(" "));
                        }
                    }
                    return param;
                }));
            }
            
            return m;
        }
    }
}