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
package org.openrewrite.java.spring.boot3;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.search.FindTags;
import org.openrewrite.xml.tree.Xml;

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
        return new XmlVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (!"logback.xml".equals(document.getSourcePath().getFileName().toString())) {
                    return document;
                }

                if (!FindTags.find(document, "//springProfile").isEmpty() ||
                    !FindTags.find(document, "//springProperty").isEmpty()) {
                    return document.withSourcePath(
                            document.getSourcePath().resolveSibling("logback-spring.xml")
                    );
                }

                return document;
            }
        };
    }
}
