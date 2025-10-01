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

import org.openrewrite.Recipe;
import org.openrewrite.java.ReplaceStringLiteralWithConstant;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class ReplaceStringLiteralsWithMediaTypeConstants extends Recipe {

    private static final List<Recipe> recipeList = Stream.of(
                    "ALL_VALUE",
                    "APPLICATION_ATOM_XML_VALUE",
                    "APPLICATION_CBOR_VALUE",
                    "APPLICATION_FORM_URLENCODED_VALUE",
                    "APPLICATION_GRAPHQL_VALUE",
                    "APPLICATION_GRAPHQL_RESPONSE_VALUE",
                    "APPLICATION_JSON_VALUE",
                    "APPLICATION_JSON_UTF8_VALUE",
                    "APPLICATION_OCTET_STREAM_VALUE",
                    "APPLICATION_PDF_VALUE",
                    "APPLICATION_PROBLEM_JSON_VALUE",
                    "APPLICATION_PROBLEM_JSON_UTF8_VALUE",
                    "APPLICATION_PROBLEM_XML_VALUE",
                    "APPLICATION_PROTOBUF_VALUE",
                    "APPLICATION_RSS_XML_VALUE",
                    "APPLICATION_NDJSON_VALUE",
                    "APPLICATION_STREAM_JSON_VALUE",
                    "APPLICATION_XHTML_XML_VALUE",
                    "APPLICATION_XML_VALUE",
                    "IMAGE_GIF_VALUE",
                    "IMAGE_JPEG_VALUE",
                    "IMAGE_PNG_VALUE",
                    "MULTIPART_FORM_DATA_VALUE",
                    "MULTIPART_MIXED_VALUE",
                    "MULTIPART_RELATED_VALUE",
                    "TEXT_EVENT_STREAM_VALUE",
                    "TEXT_HTML_VALUE",
                    "TEXT_MARKDOWN_VALUE",
                    "TEXT_PLAIN_VALUE",
                    "TEXT_XML_VALUE")
            .map(mediaType -> new ReplaceStringLiteralWithConstant(null, "org.springframework.http.MediaType." + mediaType))
            .collect(toList());

    @Override
    public String getDisplayName() {
        return "Replace String literals with `MediaType` constants";
    }

    @Override
    public String getDescription() {
        return "Replace String literals with `org.springframework.http.MediaType` constants.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return recipeList;
    }
}
