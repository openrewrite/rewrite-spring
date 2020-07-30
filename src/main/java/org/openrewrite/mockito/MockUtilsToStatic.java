package org.openrewrite.mockito;

import org.openrewrite.AutoConfigure;
import org.openrewrite.Cursor;
import org.openrewrite.Formatting;
import org.openrewrite.Tree;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.Optional;

/**
 * In Mockito 1 you use a code snippet like:
 *
 * new MockUtil().isMock(foo);
 *
 * In Mockito 2+ this class now has a private constructor and only exposes static methods:
 *
 * MockUtil.isMock(foo);
 *
 * This recipe makes a best-effort attempt to remove MockUtil instances, but if someone did something unexpected like
 * subclassing MockUtils that will not be handled and will have to be hand-remediated.
 */
@AutoConfigure
public class MockUtilsToStatic extends JavaRefactorVisitor {
    private MethodMatcher methodMatcher = new MethodMatcher("org.mockito.internal.util.MockUtil MockUtil()");
    private ChangeMethodTargetToStatic changeMethodTargetToStatic = new ChangeMethodTargetToStatic();

    public MockUtilsToStatic() {
        setCursoringOn();
        changeMethodTargetToStatic.setMethod("org.mockito.internal.util.MockUtil *(..)");
        changeMethodTargetToStatic.setTargetType("org.mockito.internal.util.MockUtil");
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        andThen(changeMethodTargetToStatic);
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J visitNewClass(J.NewClass newClass) {
        if(methodMatcher.matches(newClass)) {
            // Check to see if the new MockUtil() is being assigned to a variable, like
            // MockUtil util = new MockUtil();
            // If it is, then we'll get rid of it
             Optional.ofNullable(getCursor().getParent())
                    .filter(it -> it.getTree() instanceof J.VariableDecls.NamedVar)
                    .map(Cursor::getParent)
                    .map(Cursor::getTree)
                    .filter(it -> it instanceof J.VariableDecls.VariableDecls)
                    .ifPresent(namedVar -> andThen(new NamedVarScope((J.VariableDecls.VariableDecls)namedVar)));
        }
        return super.visitNewClass(newClass);
    }

    private static class NamedVarScope extends JavaRefactorVisitor {
        public final J.VariableDecls.VariableDecls namedVar;

        private NamedVarScope(J.VariableDecls.VariableDecls namedVar) {
            this.namedVar = namedVar;
            setCursoringOn();
        }

        @Override
        public J visitMultiVariable(J.VariableDecls multiVariable) {
            if(namedVar.isScope(multiVariable)) {
                return new J.Empty(Tree.randomId(), Formatting.EMPTY);
            }
            return super.visitMultiVariable(multiVariable);
        }
    }
}
