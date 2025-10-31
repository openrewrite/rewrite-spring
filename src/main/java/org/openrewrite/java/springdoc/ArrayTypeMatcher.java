package org.openrewrite.java.springdoc;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Objects;

class ArrayTypeMatcher<P> extends JavaIsoVisitor<P> {

    final String fqn;

    ArrayTypeMatcher(String fullyQualifiedName) {
        this.fqn = fullyQualifiedName;
    }

    @Override
    public J visit(@Nullable Tree tree, P p) {
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
