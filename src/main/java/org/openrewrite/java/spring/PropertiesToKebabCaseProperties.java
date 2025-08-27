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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import org.openrewrite.*;
import org.openrewrite.internal.NameCaseConvention;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;

@EqualsAndHashCode(callSuper = false)
class PropertiesToKebabCaseProperties extends Recipe {
    @Override
    public String getDisplayName() {
        return "Normalize Spring `application*.properties` properties to kebab-case";
    }

    @Override
    public String getDescription() {
        return "Normalize Spring `application*.properties` properties to kebab-case.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles("**/application*.properties"), new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                Properties.Entry e = super.visitEntry(entry, ctx);
                String key = e.getKey();
                String asKebabCase = NameCaseConvention.LOWER_HYPHEN.format(key);
                if (!key.equals(asKebabCase)) {
                    return e.withKey(asKebabCase);
                }
                return e;
            }
        });
    }
}
