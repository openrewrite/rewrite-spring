/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.springdoc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

class MethodDeclArrayTypeParamMatcher<P> extends JavaIsoVisitor<P> {

    final String fqn;

    MethodDeclArrayTypeParamMatcher(String fullyQualifiedName) {
        this.fqn = fullyQualifiedName;
    }

    @Override
    public @Nullable J visit(@Nullable Tree tree, P p) {
        if (tree instanceof JavaSourceFile) {
            JavaSourceFile cu = (JavaSourceFile) tree;
            for (JavaType.Method method : cu.getTypesInUse().getDeclaredMethods()) {
                if (method.getParameterTypes().stream().anyMatch(this::matches)) {
                    return SearchResult.found(cu);
                }
            }
        }
        return (J) tree;
    }

    private boolean matches(JavaType type) {
        if (type instanceof JavaType.Array) {
            JavaType.Array arrayType = (JavaType.Array) type;
            return TypeUtils.isOfClassType(arrayType.getElemType(), fqn);
        }
        return false;
    }
}
