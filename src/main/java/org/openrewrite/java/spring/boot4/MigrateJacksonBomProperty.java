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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.RenamePropertyKey;
import org.openrewrite.xml.tree.Xml;

public class MigrateJacksonBomProperty extends Recipe {

    private static final String OLD_KEY = "jackson-bom.version";
    private static final String NEW_KEY = "jackson-2-bom.version";

    @Getter
    final String displayName = "Migrate a Spring Boot 3 `jackson-bom.version` override to `jackson-2-bom.version`";

    @Getter
    final String description = "In Spring Boot 4 `jackson-bom.version` controls the Jackson 3 (`tools.jackson`) BOM, " +
            "while the Jackson 2 BOM is controlled by `jackson-2-bom.version`. A Spring Boot 3 override pins a " +
            "Jackson 2 version, so rename it to keep managing Jackson 2. Only renames when the value is a Jackson 2.x " +
            "version, leaving a deliberate Jackson 3 override on an already-Spring-Boot-4 project untouched.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (isPropertyTag() && OLD_KEY.equals(t.getName()) &&
                        t.getValue().map(value -> value.startsWith("2.")).orElse(false)) {
                    doAfterVisit(new RenamePropertyKey(OLD_KEY, NEW_KEY).getVisitor());
                }
                return t;
            }
        };
    }
}
