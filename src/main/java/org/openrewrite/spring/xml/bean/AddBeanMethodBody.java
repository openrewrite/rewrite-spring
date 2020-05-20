/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring.xml.bean;

import org.openrewrite.java.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TreeBuilder;

import java.util.UUID;

class AddBeanMethodBody extends ScopedJavaRefactorVisitor {
    private final String snippet;
    private final JavaType.Class[] imports;

    public AddBeanMethodBody(UUID scope, String snippet, JavaType.Class... imports) {
        super(scope);
        this.snippet = snippet;
        this.imports = imports;
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitMethod(J.MethodDecl method) {
        J.MethodDecl m = refactor(method, super::visitMethod);

        if (isScope() && m.getBody() != null) {
            m = m.withBody(m.getBody().withStatements(TreeBuilder.buildSnippet(enclosingCompilationUnit(), getCursor(), snippet, imports)));
        }

        return m;
    }
}
