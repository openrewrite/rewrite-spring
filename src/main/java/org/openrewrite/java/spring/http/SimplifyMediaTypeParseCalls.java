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
package org.openrewrite.java.spring.http;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SimplifyMediaTypeParseCalls extends Recipe {
    @Override
    public String getDisplayName() {
        return "Simplify Unnecessary `MediaType.parseMediaType` and `MediaType.valueOf` calls";
    }

    @Override
    public String getDescription() {
        return "Replaces `MediaType.parseMediaType('application/json')` and `MediaType.valueOf('application/json') with `MediaType.APPLICATION_JSON`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new SimplifyParseCallsVisitor();
    }

    private static final class SimplifyParseCallsVisitor extends JavaVisitor<ExecutionContext> {
        private final MethodMatcher MEDIATYPE_PARSE_MEDIA_TYPE_MATCHER = new MethodMatcher("org.springframework.http.MediaType parseMediaType(String)");
        private final MethodMatcher MEDIATYPE_VALUE_OF_MATCHER = new MethodMatcher("org.springframework.http.MediaType valueOf(String)");
        private final List<String> MEDIATYPE_VALUES_ARRAY = Arrays.asList(
                "APPLICATION_ATOM_XML_VALUE", "APPLICATION_CBOR_VALUE",
                "APPLICATION_FORM_URLENCODED_VALUE", "APPLICATION_GRAPHQL_RESPONSE_VALUE",
                "APPLICATION_JSON_VALUE", "APPLICATION_NDJSON_VALUE",
                "APPLICATION_OCTET_STREAM_VALUE", "APPLICATION_PDF_VALUE",
                "APPLICATION_PROBLEM_JSON_VALUE", "APPLICATION_PROBLEM_XML_VALUE",
                "APPLICATION_PROTOBUF_VALUE", "APPLICATION_RSS_XML_VALUE",
                "APPLICATION_XHTML_XML_VALUE", "APPLICATION_XML_VALUE",
                "IMAGE_GIF_VALUE", "IMAGE_JPEG_VALUE", "IMAGE_PNG_VALUE",
                "MULTIPART_FORM_DATA_VALUE", "MULTIPART_MIXED_VALUE",
                "MULTIPART_RELATED_VALUE", "TEXT_EVENT_STREAM_VALUE",
                "TEXT_HTML_VALUE", "TEXT_MARKDOWN_VALUE", "TEXT_PLAIN_VALUE",
                "TEXT_XML_VALUE"
        );

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (MEDIATYPE_PARSE_MEDIA_TYPE_MATCHER.matches(mi) || MEDIATYPE_VALUE_OF_MATCHER.matches(mi)) {
                Expression methodArg = mi.getArguments().get(0);
                if (methodArg instanceof J.FieldAccess && TypeUtils.isOfType(methodArg.getType(), JavaType.buildType("java.lang.String"))) {
                    J.FieldAccess access = (J.FieldAccess) methodArg;
                    if (MEDIATYPE_VALUES_ARRAY.contains(access.getSimpleName())) {
                        String replacement = "MediaType." + access.getSimpleName().replace("_VALUE", "");
                        return JavaTemplate.builder(replacement)
                                .build()
                                .apply(getCursor(), mi.getCoordinates().replace());
                    }
                }
            }

            return super.visitMethodInvocation(mi, ctx);
        }
    }
}