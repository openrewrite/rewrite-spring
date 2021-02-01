package org.openrewrite.java.spring;

import org.openrewrite.java.tree.J;

import java.util.Optional;

public class TreeUtils {
    static <T extends J> Optional<T> whenType(Class<T> treeType, J j) {
        return treeType.isAssignableFrom(j.getClass()) ? Optional.of((T) j) : Optional.empty();
    }
}
