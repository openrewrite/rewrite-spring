package org.openrewrite.mockito;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

@AutoConfigure
public class ReplaceMatchersWithArgumentMatchers extends JavaRefactorVisitor {
    private final ChangeType changeAny = new ChangeType();

    public ReplaceMatchersWithArgumentMatchers() {
        changeAny.setType("org.mockito.Matchers");
        changeAny.setTargetType("org.mockito.ArgumentMatchers");
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        return changeAny.visitCompilationUnit(cu);
    }
}
