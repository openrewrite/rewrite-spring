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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.intellij.lang.annotations.Language;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.AddProperty;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.search.FindProperties;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.CoalesceProperties;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.search.FindProperty;
import org.openrewrite.yaml.tree.Yaml;

public class MigrateDatabaseCredentials extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate flyway and liquibase credentials";
    }

    @Override
    public String getDescription() {
        return "If you currently define a `spring.flyway.url` or `spring.liquibase.url` you may need to provide " +
                "additional username and password properties. In earlier versions of Spring Boot, these settings were " +
                "derived from `spring.datasource` properties but this turned out to be problematic for people that " +
                "provided their own `DataSource` beans.";
    }

    public MigrateDatabaseCredentials() {
        doNext(new MigrateDatabaseCredentialsForToolYaml("flyway"));
        doNext(new MigrateDatabaseCredentialsForToolProperties("flyway"));
        doNext(new MigrateDatabaseCredentialsForToolYaml("liquibase"));
        doNext(new MigrateDatabaseCredentialsForToolProperties("liquibase"));
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class MigrateDatabaseCredentialsForToolYaml extends Recipe {
        @Language("markdown")
        String tool;

        @Override
        public String getDisplayName() {
            return "Migrate " + tool + " credentials";
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
            return new YamlVisitor<ExecutionContext>() {
                @Override
                public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext executionContext) {
                    if (FindProperty.find(documents, "spring." + tool + ".username", true).isEmpty() &&
                            FindProperty.find(documents, "spring." + tool + ".password", true).isEmpty()) {
                        doAfterVisit(new FindProperty("spring." + tool + ".url", true));
                    }
                    return documents;
                }
            };
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new YamlVisitor<ExecutionContext>() {
                @Override
                public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext executionContext) {
                    doAfterVisit(new MergeYaml("$.spring." + tool,"username: ${spring.datasource.username}", true, null, null));
                    doAfterVisit(new MergeYaml("$.spring." + tool,"password: ${spring.datasource.password}", true, null, null));
                    doAfterVisit(new CoalesceProperties());
                    return documents;
                }
            };
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    static class MigrateDatabaseCredentialsForToolProperties extends Recipe {
        @Language("markdown")
        String tool;

        @Override
        public String getDisplayName() {
            return "Migrate " + tool + " credentials";
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
            return new PropertiesVisitor<ExecutionContext>() {
                @Override
                public Properties visitFile(Properties.File file, ExecutionContext executionContext) {
                    if (FindProperties.find(file, "spring." + tool + ".username", true).isEmpty() &&
                            FindProperties.find(file, "spring." + tool + ".password", true).isEmpty()) {
                        doAfterVisit(new FindProperties("spring." + tool + ".url", true));
                    }
                    return file;
                }
            };
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new PropertiesVisitor<ExecutionContext>() {
                @Override
                public Properties visitFile(Properties.File file, ExecutionContext executionContext) {
                    doAfterVisit(new AddProperty("spring." + tool + ".username", "${spring.datasource.username}", null));
                    doAfterVisit(new AddProperty("spring." + tool + ".password", "${spring.datasource.password}", null));
                    return file;
                }
            };
        }
    }
}
