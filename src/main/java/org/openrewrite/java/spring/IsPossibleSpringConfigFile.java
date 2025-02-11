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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.marker.SourceSet;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

/**
 * A precondition to determine if a file might be a
 * <a href="https://docs.spring.io/spring-boot/docs/2.1.7.RELEASE/reference/html/boot-features-external-config.html#boot-features-external-config-application-property-files">Spring configuration file</a>.
 * This does not make positive identification of files which are spring configuration files, as there are few hard limits.
 * Instead, this tries to rule out files which, due to their type or location, cannot be spring properties.
 */
public class IsPossibleSpringConfigFile extends TreeVisitor<Tree, ExecutionContext> {

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (((tree instanceof Yaml.Documents) || (tree instanceof Properties.File)) && tree.getMarkers().findFirst(SourceSet.class).isPresent()) {
            return SearchResult.found(tree);
        }
        return tree;
    }
}
