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
package org.openrewrite.java.spring.boot2;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class DeclareGroovyAllAsPomWithoutTestNg extends Recipe {
    private static final String GROOVY_GROUP = "org.codehaus.groovy";
    private static final String GROOVY_TESTNG = "groovy-testng";

    String displayName = "Declare `groovy-all` as a POM without TestNG";

    String description = "From Groovy 2.5 on `org.codehaus.groovy:groovy-all` is published as a POM without a JAR, so " +
            "Maven fails to resolve a `groovy-all` declared with the default `jar` type. It also brings " +
            "`groovy-testng`, and with TestNG on the classpath Surefire before 3.0 picks its TestNG provider, which " +
            "finds no Spock 2 specifications: the build passes without running any of them. Every declaration gets " +
            "`<type>pom</type>` and excludes `groovy-testng`, the managed dependency and the dependencies it manages " +
            "alike, because Maven only matches the two when their types are the same and a dependency with " +
            "exclusions of its own does not inherit the managed ones.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if ("dependency".equals(t.getName()) &&
                    GROOVY_GROUP.equals(t.getChildValue("groupId").orElse(null)) &&
                    "groovy-all".equals(t.getChildValue("artifactId").orElse(null))) {
                    return excludeGroovyTestNg(addPomType(t), ctx);
                }
                return t;
            }

            private Xml.Tag addPomType(Xml.Tag dependency) {
                if (dependency.getChild("type").isPresent()) {
                    return dependency;
                }
                Xml.Tag previous = dependency.getChild("version").orElseGet(() -> dependency.getChild("artifactId")
                        .orElseThrow(() -> new IllegalStateException("Dependency must have artifactId")));
                List<Content> content = new ArrayList<>(requireNonNull(dependency.getContent()));
                content.add(content.indexOf(previous) + 1, Xml.Tag.build("<type>pom</type>").withPrefix(previous.getPrefix()));
                return dependency.withContent(content);
            }

            private Xml.Tag excludeGroovyTestNg(Xml.Tag dependency, ExecutionContext ctx) {
                String exclusion = "<exclusion>\n" +
                        "<groupId>" + GROOVY_GROUP + "</groupId>\n" +
                        "<artifactId>" + GROOVY_TESTNG + "</artifactId>\n" +
                        "</exclusion>";
                Xml.Tag exclusions = dependency.getChild("exclusions").orElse(null);
                if (exclusions == null) {
                    return addTo(dependency, dependency, "<exclusions>\n" + exclusion + "\n</exclusions>", ctx);
                }
                boolean excluded = exclusions.getChildren("exclusion").stream().anyMatch(it ->
                        GROOVY_GROUP.equals(it.getChildValue("groupId").orElse(null)) &&
                        GROOVY_TESTNG.equals(it.getChildValue("artifactId").orElse(null)));
                return excluded ? dependency : addTo(dependency, exclusions, exclusion, ctx);
            }

            private Xml.Tag addTo(Xml.Tag dependency, Xml.Tag parent, String tag, ExecutionContext ctx) {
                return (Xml.Tag) new AddToTagVisitor<ExecutionContext>(parent, Xml.Tag.build(tag))
                        .visitNonNull(dependency, ctx, getCursor().getParentOrThrow());
            }
        };
    }
}
