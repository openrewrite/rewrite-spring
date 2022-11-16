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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateActuatorMediaTypeToApiVersion extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deprecated ActuatorMediaType to ApiVersion#getProducedMimeType";
    }

    @Override
    public String getDescription() {
        return "Spring-Boot-Actuator `ActuatorMediaType` was deprecated in 2.5 in favor of `ApiVersion#getProducedMimeType()`. Replace `MediaType.parseMediaType(ActuatorMediaType.Vx_JSON)` with `MediaType.asMediaType(ApiVersion.Vx.getProducedMimeType())`.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>(){
            private final MethodMatcher mediaTypeMatcher = new MethodMatcher("org.springframework.http.MediaType parseMediaType(java.lang.String)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
                if (mediaTypeMatcher.matches(mi)) {
                    Expression arg0 = mi.getArguments().get(0);
                    if (arg0 instanceof J.FieldAccess) {
                        J.FieldAccess expFa = (J.FieldAccess)arg0;
                        if (TypeUtils.isOfClassType(expFa.getTarget().getType(), "org.springframework.boot.actuate.endpoint.http.ActuatorMediaType")) {
                            String apiVersion = null;
                            if ("V2_JSON".equals(expFa.getSimpleName())){
                                apiVersion = "V2";
                            } else if ("V3_JSON".equals(expFa.getSimpleName())) {
                                apiVersion = "V3";
                            }
                            if (apiVersion != null) {
                                maybeAddImport("org.springframework.boot.actuate.endpoint.ApiVersion");
                                maybeRemoveImport("org.springframework.boot.actuate.endpoint.http.ActuatorMediaType");
                                mi = mi.withTemplate(JavaTemplate.builder(this::getCursor, "MediaType.asMediaType(ApiVersion." + apiVersion + ".getProducedMimeType())")
                                                .javaParser(() -> JavaParser.fromJavaVersion()
                                                        .dependsOn(
                                                                "package org.springframework.util;" +
                                                                "public class MimeType {}",

                                                                "package org.springframework.http;" +
                                                                "import org.springframework.util.MimeType;"+
                                                                "public class MediaType extends MimeType implements Serializable {" +
                                                                "  public static MediaType asMediaType(MimeType mimeType) {return null;}" +
                                                                "}",

                                                                "package org.springframework.boot.actuate.endpoint;" +
                                                                "import org.springframework.util.MimeType;" +
                                                                "public enum ApiVersion {" +
                                                                "  V2(\"application/vnd.spring-boot.actuator.v2+json\")," +
                                                                "  V3(\"application/vnd.spring-boot.actuator.v3+json\");" +
                                                                "  public MimeType getProducedMimeType() {return null;}" +
                                                                "}"
                                                                )
                                                        .build())
                                        .imports("package org.springframework.util.MimeType", "org.springframework.http.MediaType", "org.springframework.boot.actuate.endpoint.ApiVersion").build(),
                                        mi.getCoordinates().replace());
                            }
                        }
                    }
                }
                return mi;
            }
        };
    }
}
