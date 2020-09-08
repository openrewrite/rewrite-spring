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
package org.openrewrite.mockito;

import org.openrewrite.AutoConfigure;
import org.openrewrite.Cursor;
import org.openrewrite.Formatting;
import org.openrewrite.Tree;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.DeleteStatement;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In Mockito 1 you use a code snippet like:
 * <p>
 * new MockUtil().isMock(foo);
 * <p>
 * In Mockito 2+ this class now has a private constructor and only exposes static methods:
 * <p>
 * MockUtil.isMock(foo);
 * <p>
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
        if (methodMatcher.matches(newClass)) {
            // Check to see if the new MockUtil() is being assigned to a variable or field, like
            // MockUtil util = new MockUtil();
            // If it is, then we'll get rid of it
            Optional.ofNullable(getCursor().getParent())
                    .filter(it -> it.getTree() instanceof J.VariableDecls.NamedVar)
                    .map(Cursor::getParent)
                    .map(Cursor::getTree)
                    .filter(it -> it instanceof J.VariableDecls.VariableDecls)
                    .ifPresent(namedVar -> andThen(new DeleteStatement.Scoped((J.VariableDecls.VariableDecls) namedVar)));
        }
        return super.visitNewClass(newClass);
    }
}
