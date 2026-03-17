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
package org.openrewrite.java.spring.mvc;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;

import java.util.ArrayList;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class JaxrsToSpringmvcMediaType extends Recipe {

    String displayName = "Migrate JAX-RS MediaType to Spring MVC MediaType";
    String description = "Replaces all JAX-RS MediaType with Spring MVC MediaType.";

    private static final String SPRING_MEDIA_TYPE = "org.springframework.http.MediaType";

    private static final String[] STRING_CONSTANTS = {
            "APPLICATION_ATOM_XML",
            "APPLICATION_FORM_URLENCODED",
            "APPLICATION_JSON",
            "APPLICATION_OCTET_STREAM",
            "APPLICATION_XHTML_XML",
            "APPLICATION_XML",
            "MULTIPART_FORM_DATA",
            "TEXT_HTML",
            "TEXT_PLAIN",
            "TEXT_XML"
    };

    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        for (String jaxRsType : new String[]{"javax.ws.rs.core.MediaType", "jakarta.ws.rs.core.MediaType"}) {
            for (String c : STRING_CONSTANTS) {
                // String constants: APPLICATION_JSON → APPLICATION_JSON_VALUE
                recipes.add(new ReplaceConstantWithAnotherConstant(
                        jaxRsType + "." + c, SPRING_MEDIA_TYPE + "." + c + "_VALUE"));
                // MediaType constants: APPLICATION_JSON_TYPE → APPLICATION_JSON
                recipes.add(new ReplaceConstantWithAnotherConstant(
                        jaxRsType + "." + c + "_TYPE", SPRING_MEDIA_TYPE + "." + c));
            }
            // Special: WILDCARD → ALL
            recipes.add(new ReplaceConstantWithAnotherConstant(
                    jaxRsType + ".WILDCARD", SPRING_MEDIA_TYPE + ".ALL_VALUE"));
            recipes.add(new ReplaceConstantWithAnotherConstant(
                    jaxRsType + ".WILDCARD_TYPE", SPRING_MEDIA_TYPE + ".ALL"));
            // Change the type itself for any remaining references (variable declarations, parameters, etc.)
            recipes.add(new ChangeType(jaxRsType, SPRING_MEDIA_TYPE, true));
        }
        return recipes;
    }
}
