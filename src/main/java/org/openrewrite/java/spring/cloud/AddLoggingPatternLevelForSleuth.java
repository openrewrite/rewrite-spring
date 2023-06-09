/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.cloud;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.AddSpringProperty;

public class AddLoggingPatternLevelForSleuth extends Recipe {
    @Override
    public String getDisplayName() {
        return "Add logging.pattern.level for traceId and spanId";
    }

    @Override
    public String getDescription() {
        return "Add `logging.pattern.level` for traceId and spanId which was previously set by default, if not already set.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        boolean TODO = true; // Limit to projects using Sleuth
        AddSpringProperty addSpringProperty = new AddSpringProperty(
                "logging.pattern.level",
                // The ${spring.application.name:} could not be escaped in yaml so far
                "\"%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]\"",
                "Logging pattern containing traceId and spanId; no longer provided through Sleuth by default",
                null);
        return Preconditions.check(TODO, addSpringProperty.getVisitor());
    }
}
