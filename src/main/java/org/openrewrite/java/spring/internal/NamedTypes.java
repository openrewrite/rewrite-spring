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
package org.openrewrite.java.spring.internal;

import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The types that code names by their simple names, for a template that prints the code to declare with
 * {@code imports(..)}.
 */
public class NamedTypes {

    private NamedTypes() {
    }

    /**
     * @return The fully qualified names of the top-level types outside {@code java.lang} that the trees name.
     */
    public static String[] in(J... trees) {
        Set<String> names = new LinkedHashSet<>();
        JavaIsoVisitor<Set<String>> visitor = new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> n) {
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(identifier.getType());
                Object parent = getCursor().getParentTreeCursor().getValue();
                // a qualified name needs no import, and a nested type resolves through its owner
                if (type != null && !(type instanceof JavaType.Unknown) && identifier.getFieldType() == null &&
                        type.getOwningClass() == null && identifier.getSimpleName().equals(type.getClassName()) &&
                        !(parent instanceof J.FieldAccess && ((J.FieldAccess) parent).getName() == identifier) &&
                        !type.getPackageName().isEmpty() && !"java.lang".equals(type.getPackageName())) {
                    n.add(type.getFullyQualifiedName());
                }
                return identifier;
            }
        };
        for (J tree : trees) {
            visitor.visit(tree, names);
        }
        return names.toArray(new String[0]);
    }
}
