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
package org.openrewrite.java.spring.boot2;

import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

public class SamlRelyingPartyPropertyApplicationPropertiesMove extends Recipe {
    private static final String REGEX_PATTERN = "(spring\\.security\\.saml2\\.relyingparty\\.registration\\..*)(\\.identityprovider)(.*)";

    @Override
    public String getDisplayName() {
        return "Move SAML relying party identity provider property to asserting party";
    }

    @Override
    public String getDescription() {
        return "Renames spring.security.saml2.relyingparty.registration.(any).identityprovider to " +
                "spring.security.saml2.relyingparty.registration.(any).assertingparty.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new PropertiesVisitor<ExecutionContext>() {

            @Override
            public Properties visitEntry(Properties.Entry entry, ExecutionContext ctx) {

                if (entry.getKey().matches(REGEX_PATTERN)) {
                    return super.visitEntry(updateEntry(entry), ctx);
                }

                return super.visitEntry(entry, ctx);
            }


            private Properties.@NonNull Entry updateEntry(Properties.Entry entry) {
                return entry.withKey(entry.getKey().replaceAll(REGEX_PATTERN, "$1.assertingparty$3"));
            }
        };
    }
}
