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
package org.openrewrite.java.spring.http;

import lombok.Getter;
import org.openrewrite.Recipe;
import org.openrewrite.java.ReplaceStringLiteralWithConstant;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class ReplaceStringLiteralsWithMediaTypeConstants extends Recipe {

    private static final String FULLY_QUALIFIED = "org.springframework.http.MediaType.";

    @SuppressWarnings("deprecation")
    private static final List<Recipe> recipeList = Stream.of(
                    r("*/*", "ALL_VALUE"),
                    r("application/atom+xml", "APPLICATION_ATOM_XML_VALUE"),
                    r("application/cbor", "APPLICATION_CBOR_VALUE"),
                    r("application/x-www-form-urlencoded", "APPLICATION_FORM_URLENCODED_VALUE"),
                    r("application/graphql+json", "APPLICATION_GRAPHQL_VALUE"),
                    r("application/graphql-response+json", "APPLICATION_GRAPHQL_RESPONSE_VALUE"),
                    r("application/json", "APPLICATION_JSON_VALUE"),
                    r("application/json;charset=UTF-8", "APPLICATION_JSON_UTF8_VALUE"),
                    r("application/octet-stream", "APPLICATION_OCTET_STREAM_VALUE"),
                    r("application/pdf", "APPLICATION_PDF_VALUE"),
                    r("application/problem+json", "APPLICATION_PROBLEM_JSON_VALUE"),
                    r("application/problem+json;charset=UTF-8", "APPLICATION_PROBLEM_JSON_UTF8_VALUE"),
                    r("application/problem+xml", "APPLICATION_PROBLEM_XML_VALUE"),
                    r("application/x-protobuf", "APPLICATION_PROTOBUF_VALUE"),
                    r("application/rss+xml", "APPLICATION_RSS_XML_VALUE"),
                    r("application/x-ndjson", "APPLICATION_NDJSON_VALUE"),
                    r("application/stream+json", "APPLICATION_STREAM_JSON_VALUE"),
                    r("application/xhtml+xml", "APPLICATION_XHTML_XML_VALUE"),
                    r("application/xml", "APPLICATION_XML_VALUE"),
                    r("application/yaml", "APPLICATION_YAML_VALUE"),
                    r("image/gif", "IMAGE_GIF_VALUE"),
                    r("image/jpeg", "IMAGE_JPEG_VALUE"),
                    r("image/png", "IMAGE_PNG_VALUE"),
                    r("multipart/form-data", "MULTIPART_FORM_DATA_VALUE"),
                    r("multipart/mixed", "MULTIPART_MIXED_VALUE"),
                    r("multipart/related", "MULTIPART_RELATED_VALUE"),
                    r("text/event-stream", "TEXT_EVENT_STREAM_VALUE"),
                    r("text/html", "TEXT_HTML_VALUE"),
                    r("text/markdown", "TEXT_MARKDOWN_VALUE"),
                    r("text/plain", "TEXT_PLAIN_VALUE"),
                    r("text/xml", "TEXT_XML_VALUE"))
            .collect(toList());

    private static Recipe r(String literal, String constantName) {
        return new ReplaceStringLiteralWithConstant(literal, FULLY_QUALIFIED + constantName);
    }

    @Getter
    final String displayName = "Replace String literals with `MediaType` constants";

    @Getter
    final String description = "Replace String literals with `org.springframework.http.MediaType` constants.";

    @Override
    public List<Recipe> getRecipeList() {
        return recipeList;
    }
}
