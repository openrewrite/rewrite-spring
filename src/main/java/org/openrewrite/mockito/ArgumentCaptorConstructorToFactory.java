package org.openrewrite.mockito;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.InsertMethodArgument;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;


/**
 * org.mockito.ArgumentCaptor deprecated its public constructor in favor of a static factory method on the same class.
 * The constructor accepts no arguments, the factory method accepts a single nullable argument.
 */
@AutoConfigure
public class ArgumentCaptorConstructorToFactory extends JavaRefactorVisitor {
    private final ChangeMethodTargetToStatic changeMethodTargetToStatic;
    private final InsertMethodArgument insertMethodArgument;

    public ArgumentCaptorConstructorToFactory() {
        changeMethodTargetToStatic = new ChangeMethodTargetToStatic();
        changeMethodTargetToStatic.setMethod("org.mockito.ArgumentCaptor new()");
        changeMethodTargetToStatic.setTargetType("org.mockito.ArgumentCaptor forClass()");

        insertMethodArgument = new InsertMethodArgument();
        insertMethodArgument.setIndex(0);
        insertMethodArgument.setSource("null");
        insertMethodArgument.setMethod("org.mockito.ArgumentCaptor forClass()");

        changeMethodTargetToStatic.andThen(insertMethodArgument);
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        return changeMethodTargetToStatic.visitCompilationUnit(cu);
    }
}
