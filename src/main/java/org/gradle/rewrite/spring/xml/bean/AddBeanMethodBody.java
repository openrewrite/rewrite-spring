package org.gradle.rewrite.spring.xml.bean;

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
