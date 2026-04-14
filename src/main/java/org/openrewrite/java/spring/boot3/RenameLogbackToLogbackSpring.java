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
package org.openrewrite.java.spring.boot3;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.xml.search.FindTags;

@Value
@EqualsAndHashCode(callSuper = false)
public class RenameLogbackToLogbackSpring extends Recipe {

    @Override
    public String getDisplayName() {
        return "Rename `logback.xml` to `logback-spring.xml`";
    }

    @Override
    public String getDescription() {
        return "Spring Boot only processes Spring-specific logback extensions (`<springProfile>`, `<springProperty>`) " +
               "when the configuration file is named `logback-spring.xml`. " +
               "A plain `logback.xml` is loaded too early by logback itself, before Spring's `Environment` is ready, " +
               "so these extensions are silently ignored. " +
               "This recipe renames `logback.xml` to `logback-spring.xml` when Spring extensions are detected.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.and(
                        new FindSourceFiles("**/logback.xml").getVisitor(),
                        Preconditions.or(
                                new FindTags("//springProfile").getVisitor(),
                                new FindTags("//springProperty").getVisitor()
                        )
                ),
                new RenameFile("**/logback.xml", "logback-spring.xml").getVisitor()
        );
    }
}
