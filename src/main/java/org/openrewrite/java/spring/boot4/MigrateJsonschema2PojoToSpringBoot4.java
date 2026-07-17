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
package org.openrewrite.java.spring.boot4;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.UpgradePluginVersion;
import org.openrewrite.maven.search.FindPlugin;
import org.openrewrite.xml.AddOrUpdateChild;
import org.openrewrite.xml.XmlVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.singletonList;

public class MigrateJsonschema2PojoToSpringBoot4 extends Recipe {

    private static final String GROUP_ID = "org.jsonschema2pojo";
    private static final String ARTIFACT_ID = "jsonschema2pojo-maven-plugin";
    private static final Set<String> JACKSON_STYLES =
            new HashSet<>(Arrays.asList("jackson", "jackson2", "jackson3"));

    @Getter
    final String displayName = "Migrate jsonschema2pojo configuration to Spring Boot 4";

    @Getter
    final String description = "Update `jsonschema2pojo-maven-plugin` to generate Jackson 3 and Jakarta Validation " +
            "annotations compatible with Spring Boot 4. The `jackson3` annotation style was introduced in " +
            "jsonschema2pojo 1.3.0, so the plugin is upgraded to at least that version first.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new UpgradePluginVersion(GROUP_ID, ARTIFACT_ID, "1.3.0", null, null, null));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext ctx) {
                for (Xml.Tag plugin : FindPlugin.find(document, GROUP_ID, ARTIFACT_ID)) {
                    Optional<Xml.Tag> configuration = plugin.getChild("configuration");
                    if (configuration.isPresent()) {
                        if (needsMigration(configuration.get())) {
                            doAfterVisit(new MigrateConfiguration<>(configuration.get()));
                        }
                    } else {
                        doAfterVisit(new AddConfiguration<>(plugin));
                    }
                }
                return super.visitDocument(document, ctx);
            }

            private boolean needsMigration(Xml.Tag configuration) {
                String style = configuration.getChildValue("annotationStyle").orElse("jackson2");
                boolean migrateJackson = JACKSON_STYLES.contains(style) && !"jackson3".equals(style);
                boolean migrateValidation =
                        "true".equals(configuration.getChildValue("includeJsr303Annotations").orElse(null)) &&
                        !"true".equals(configuration.getChildValue("useJakartaValidation").orElse(null));
                return migrateJackson || migrateValidation;
            }
        };
    }

    @RequiredArgsConstructor
    private static class MigrateConfiguration<P> extends XmlVisitor<P> {
        private final Xml.Tag scope;

        @Override
        public Xml visitTag(Xml.Tag tag, P p) {
            Xml.Tag t = (Xml.Tag) super.visitTag(tag, p);
            if (scope.isScope(tag)) {
                String style = t.getChildValue("annotationStyle").orElse("jackson2");
                if (JACKSON_STYLES.contains(style)) {
                    t = AddOrUpdateChild.addOrUpdateChild(t,
                            Xml.Tag.build("<annotationStyle>jackson3</annotationStyle>"),
                            getCursor().getParentOrThrow());
                }
                if ("true".equals(t.getChildValue("includeJsr303Annotations").orElse(null))) {
                    t = AddOrUpdateChild.addOrUpdateChild(t,
                            Xml.Tag.build("<useJakartaValidation>true</useJakartaValidation>"),
                            getCursor().getParentOrThrow());
                }
            }
            return t;
        }
    }

    @RequiredArgsConstructor
    private static class AddConfiguration<P> extends XmlVisitor<P> {
        private final Xml.Tag scope;

        @Override
        public Xml visitTag(Xml.Tag tag, P p) {
            Xml.Tag t = (Xml.Tag) super.visitTag(tag, p);
            if (scope.isScope(tag)) {
                t = AddOrUpdateChild.addOrUpdateChild(t,
                        Xml.Tag.build("<configuration><annotationStyle>jackson3</annotationStyle></configuration>"),
                        getCursor().getParentOrThrow());
            }
            return t;
        }
    }
}
