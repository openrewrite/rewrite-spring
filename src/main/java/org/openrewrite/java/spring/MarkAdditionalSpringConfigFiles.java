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
package org.openrewrite.java.spring;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Option;
import org.openrewrite.Recipe;

import java.util.List;

/**
 * @deprecated The Spring property recipes discover Spring configuration files outside source sets themselves.
 */
@Deprecated
@Value
@EqualsAndHashCode(callSuper = false)
public class MarkAdditionalSpringConfigFiles extends Recipe {

    @Option(displayName = "File path patterns",
            description = "Glob patterns (relative to the repository root) identifying YAML or properties files " +
                    "that should be treated as Spring configuration even though they live outside a standard " +
                    "`src/main/resources` source set. Files already carrying a `SourceSet` marker are left alone.",
            example = "**/properties/*.properties")
    List<String> pathPatterns;

    String displayName = "Mark additional files as Spring configuration";

    String description = "Deprecated, and makes no changes. Spring property recipes such as " +
            "`ChangeSpringPropertyKey`, `DeleteSpringProperty`, `ChangeSpringPropertyValue`, and " +
            "`CommentOutSpringPropertyKey` now discover the Spring configuration files outside source sets themselves.";
}
