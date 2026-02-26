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

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class ReplaceStringLiteralsWithMediaTypeConstants extends Recipe {

    private static final String FQN = "org.springframework.http.MediaType.";

    @SuppressWarnings("deprecation")
    private static final List<Recipe> recipeList = Stream.<Map.Entry<String, String>>of(
                    e("ALL_VALUE", "*/*"),
                    e("APPLICATION_ATOM_XML_VALUE", "application/atom+xml"),
                    e("APPLICATION_CBOR_VALUE", "application/cbor"),
                    e("APPLICATION_FORM_URLENCODED_VALUE", "application/x-www-form-urlencoded"),
                    e("APPLICATION_GRAPHQL_VALUE", "application/graphql+json"),
                    e("APPLICATION_GRAPHQL_RESPONSE_VALUE", "application/graphql-response+json"),
                    e("APPLICATION_JSON_VALUE", "application/json"),
                    e("APPLICATION_JSON_UTF8_VALUE", "application/json;charset=UTF-8"),
                    e("APPLICATION_OCTET_STREAM_VALUE", "application/octet-stream"),
                    e("APPLICATION_PDF_VALUE", "application/pdf"),
                    e("APPLICATION_PROBLEM_JSON_VALUE", "application/problem+json"),
                    e("APPLICATION_PROBLEM_JSON_UTF8_VALUE", "application/problem+json;charset=UTF-8"),
                    e("APPLICATION_PROBLEM_XML_VALUE", "application/problem+xml"),
                    e("APPLICATION_PROTOBUF_VALUE", "application/x-protobuf"),
                    e("APPLICATION_RSS_XML_VALUE", "application/rss+xml"),
                    e("APPLICATION_NDJSON_VALUE", "application/x-ndjson"),
                    e("APPLICATION_STREAM_JSON_VALUE", "application/stream+json"),
                    e("APPLICATION_XHTML_XML_VALUE", "application/xhtml+xml"),
                    e("APPLICATION_XML_VALUE", "application/xml"),
                    e("APPLICATION_YAML_VALUE", "application/yaml"),
                    e("IMAGE_GIF_VALUE", "image/gif"),
                    e("IMAGE_JPEG_VALUE", "image/jpeg"),
                    e("IMAGE_PNG_VALUE", "image/png"),
                    e("MULTIPART_FORM_DATA_VALUE", "multipart/form-data"),
                    e("MULTIPART_MIXED_VALUE", "multipart/mixed"),
                    e("MULTIPART_RELATED_VALUE", "multipart/related"),
                    e("TEXT_EVENT_STREAM_VALUE", "text/event-stream"),
                    e("TEXT_HTML_VALUE", "text/html"),
                    e("TEXT_MARKDOWN_VALUE", "text/markdown"),
                    e("TEXT_PLAIN_VALUE", "text/plain"),
                    e("TEXT_XML_VALUE", "text/xml"))
            .map(entry -> new ReplaceStringLiteralWithConstant(entry.getValue(), FQN + entry.getKey()))
            .collect(toList());

    private static Map.Entry<String, String> e(String constant, String literal) {
        return new AbstractMap.SimpleImmutableEntry<>(constant, literal);
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
