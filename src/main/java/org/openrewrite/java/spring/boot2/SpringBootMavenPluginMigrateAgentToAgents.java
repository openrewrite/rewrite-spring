/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.search.FindPlugin;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.search.FindTags;
import org.openrewrite.xml.tree.Xml;

public class SpringBootMavenPluginMigrateAgentToAgents extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use `spring-boot.run.agents` configuration key in `spring-boot-maven-plugin`";
    }

    @Override
    public String getDescription() {
        return "Migrate the `spring-boot.run.agent` Maven plugin configuration key to `spring-boot.run.agents`. Deprecated in 2.2.x.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new MavenVisitor<ExecutionContext>() {

            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext ctx) {
                if (FindPlugin.find(document, "org.springframework.boot", "spring-boot-maven-plugin").stream().noneMatch(plugin -> FindTags.find(plugin, "//configuration/agent").isEmpty())) {
                    document = document.withMarkers(document.getMarkers().searchResult());
                }
                return document;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenVisitor<ExecutionContext>() {
            @Override
            public Xml visitDocument(Xml.Document document, ExecutionContext executionContext) {
                FindPlugin.find(document, "org.springframework.boot", "spring-boot-maven-plugin").forEach(plugin ->
                        FindTags.find(plugin, "//configuration/agent").forEach(agentTag ->
                                doAfterVisit(new ChangeTagKeyVisitor<>(agentTag, "agents"))
                        )
                );
                return super.visitDocument(document, executionContext);
            }
        };
    }

    private static class ChangeTagKeyVisitor<P> extends XmlVisitor<P> {
        private final Xml.Tag scope;
        private final String newKeyName;

        public ChangeTagKeyVisitor(Xml.Tag scope, String newKeyName) {
            this.scope = scope;
            this.newKeyName = newKeyName;
        }

        @Override
        public Xml visitTag(Xml.Tag tag, P p) {
            Xml.Tag t = (Xml.Tag) super.visitTag(tag, p);
            if (scope.isScope(tag)) {
                t = t.withName(newKeyName);
            }
            return t;
        }
    }

}
