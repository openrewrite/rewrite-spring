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
package org.openrewrite.java.spring.internal;

import org.openrewrite.Cursor;
import org.openrewrite.java.trait.Scope;
import org.openrewrite.java.trait.VariableAccess;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class LocalVariableUtils {

    /**
     * The initializer of the final variable an expression names, or the expression itself where it names no
     * variable declared final with an initializer in this file.
     *
     * @param cursor the cursor of the tree holding the expression
     */
    public static Expression resolveExpression(Expression expression, Cursor cursor) {
        Cursor name;
        if (expression instanceof J.Identifier) {
            name = new Cursor(cursor, expression);
        } else if (expression instanceof J.FieldAccess) {
            name = new Cursor(new Cursor(cursor, expression), ((J.FieldAccess) expression).getName());
        } else {
            return expression;
        }
        if (name.<J.Identifier>getValue().getFieldType() == null) {
            return expression;
        }
        return new VariableAccess(name).getBinding()
                .map(Scope.Binding::getCursor)
                .map(Cursor::getParentTreeCursor)
                .filter(declaring -> declaring.getValue() instanceof J.VariableDeclarations.NamedVariable &&
                                     declaring.getParentTreeCursor().<J.VariableDeclarations>getValue().hasModifier(J.Modifier.Type.Final))
                .map(declaring -> declaring.<J.VariableDeclarations.NamedVariable>getValue().getInitializer())
                .orElse(expression);
    }
}
