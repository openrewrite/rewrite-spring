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
package org.openrewrite.java.spring.security5;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PasswordEncoderUtils {

    /**
     * Check if the cursor is inside a class declaration for the given fully-qualified class name.
     * This prevents replacing constructor calls inside factory method implementations with calls
     * to those same factory methods, which would cause infinite recursion.
     */
    static boolean isInsideTargetClass(Cursor cursor, String fqn) {
        return cursor.getPathAsStream()
                .filter(J.ClassDeclaration.class::isInstance)
                .map(J.ClassDeclaration.class::cast)
                .anyMatch(cd -> TypeUtils.isOfClassType(cd.getType(), fqn));
    }
}
