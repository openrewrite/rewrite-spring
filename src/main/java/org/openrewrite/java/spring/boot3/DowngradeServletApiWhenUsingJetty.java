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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.AddProperty;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.search.FindDependency;
import org.openrewrite.xml.tree.Xml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DowngradeServletApiWhenUsingJetty extends Recipe {

    @Override
    public String getDisplayName() {
        return "Downgrade Jakarta Servlet API to 5.0 when using Jetty";
    }

    @Override
    public String getDescription() {
        return "Jetty does not yet support Servlet 6.0. This recipe will detect the presence of the `spring-boot-starter-jetty` as a "
                + "first-order dependency and will add the maven property `jakarta-servlet.version` setting it's value to `5.0.0`. This "
                + "will downgrade the `jakarta-servlet` artifact if the pom's parent extends from the spring-boot-parent.";
    }

    @Override
    public Set<String> getTags() {
        return new HashSet<>(Arrays.asList("spring", "boot", "jetty"));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindDependency("org.springframework.boot", "spring-boot-starter-jetty"), new MavenVisitor<ExecutionContext>() {
            @Override
            public @Nullable Xml visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree == null) {
                    return null;
                }
                return (Xml) new AddProperty("jakarta-servlet.version", "5.0.0", false, false).getVisitor().visit(tree, ctx);
            }
        });
    }
}
