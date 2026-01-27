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

public class ReplaceStringLiteralsWithHttpHeadersConstants extends Recipe {

    private static final List<Recipe> recipeList = Stream.of(
                    "ACCEPT",
                    "ACCEPT_CHARSET",
                    "ACCEPT_ENCODING",
                    "ACCEPT_LANGUAGE",
                    "ACCEPT_PATCH",
                    "ACCEPT_RANGES",
                    "ACCESS_CONTROL_ALLOW_CREDENTIALS",
                    "ACCESS_CONTROL_ALLOW_HEADERS",
                    "ACCESS_CONTROL_ALLOW_METHODS",
                    "ACCESS_CONTROL_ALLOW_ORIGIN",
                    "ACCESS_CONTROL_EXPOSE_HEADERS",
                    "ACCESS_CONTROL_MAX_AGE",
                    "ACCESS_CONTROL_REQUEST_HEADERS",
                    "ACCESS_CONTROL_REQUEST_METHOD",
                    "AGE",
                    "ALLOW",
                    "AUTHORIZATION",
                    "CACHE_CONTROL",
                    "CONNECTION",
                    "CONTENT_ENCODING",
                    "CONTENT_DISPOSITION",
                    "CONTENT_LANGUAGE",
                    "CONTENT_LENGTH",
                    "CONTENT_LOCATION",
                    "CONTENT_RANGE",
                    "CONTENT_TYPE",
                    "COOKIE",
                    "DATE",
                    "ETAG",
                    "EXPECT",
                    "EXPIRES",
                    "FROM",
                    "HOST",
                    "IF_MATCH",
                    "IF_MODIFIED_SINCE",
                    "IF_NONE_MATCH",
                    "IF_RANGE",
                    "IF_UNMODIFIED_SINCE",
                    "LAST_MODIFIED",
                    "LINK",
                    "LOCATION",
                    "MAX_FORWARDS",
                    "ORIGIN",
                    "PRAGMA",
                    "PROXY_AUTHENTICATE",
                    "PROXY_AUTHORIZATION",
                    "RANGE",
                    "REFERER",
                    "RETRY_AFTER",
                    "SERVER",
                    "SET_COOKIE",
                    "SET_COOKIE2",
                    "TE",
                    "TRAILER",
                    "TRANSFER_ENCODING",
                    "UPGRADE",
                    "USER_AGENT",
                    "VARY",
                    "VIA",
                    "WARNING",
                    "WWW_AUTHENTICATE")
            .map(header -> new ReplaceStringLiteralWithConstant(null, "org.springframework.http.HttpHeaders." + header))
            .collect(toList());

    @Getter
    final String displayName = "Replace String literals with `HttpHeaders` constants";

    @Getter
    final String description = "Replace String literals with `org.springframework.http.HttpHeaders` constants.";

    @Override
    public List<Recipe> getRecipeList() {
        return recipeList;
    }
}
