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

    private static final String FULLY_QUALIFIED = "org.springframework.http.HttpHeaders.";

    private static final List<Recipe> recipeList = Stream.of(
                    r("Accept", "ACCEPT"),
                    r("Accept-Charset", "ACCEPT_CHARSET"),
                    r("Accept-Encoding", "ACCEPT_ENCODING"),
                    r("Accept-Language", "ACCEPT_LANGUAGE"),
                    r("Accept-Patch", "ACCEPT_PATCH"),
                    r("Accept-Ranges", "ACCEPT_RANGES"),
                    r("Access-Control-Allow-Credentials", "ACCESS_CONTROL_ALLOW_CREDENTIALS"),
                    r("Access-Control-Allow-Headers", "ACCESS_CONTROL_ALLOW_HEADERS"),
                    r("Access-Control-Allow-Methods", "ACCESS_CONTROL_ALLOW_METHODS"),
                    r("Access-Control-Allow-Origin", "ACCESS_CONTROL_ALLOW_ORIGIN"),
                    r("Access-Control-Expose-Headers", "ACCESS_CONTROL_EXPOSE_HEADERS"),
                    r("Access-Control-Max-Age", "ACCESS_CONTROL_MAX_AGE"),
                    r("Access-Control-Request-Headers", "ACCESS_CONTROL_REQUEST_HEADERS"),
                    r("Access-Control-Request-Method", "ACCESS_CONTROL_REQUEST_METHOD"),
                    r("Age", "AGE"),
                    r("Allow", "ALLOW"),
                    r("Authorization", "AUTHORIZATION"),
                    r("Cache-Control", "CACHE_CONTROL"),
                    r("Connection", "CONNECTION"),
                    r("Content-Encoding", "CONTENT_ENCODING"),
                    r("Content-Disposition", "CONTENT_DISPOSITION"),
                    r("Content-Language", "CONTENT_LANGUAGE"),
                    r("Content-Length", "CONTENT_LENGTH"),
                    r("Content-Location", "CONTENT_LOCATION"),
                    r("Content-Range", "CONTENT_RANGE"),
                    r("Content-Type", "CONTENT_TYPE"),
                    r("Cookie", "COOKIE"),
                    r("Date", "DATE"),
                    r("ETag", "ETAG"),
                    r("Expect", "EXPECT"),
                    r("Expires", "EXPIRES"),
                    r("From", "FROM"),
                    r("Host", "HOST"),
                    r("If-Match", "IF_MATCH"),
                    r("If-Modified-Since", "IF_MODIFIED_SINCE"),
                    r("If-None-Match", "IF_NONE_MATCH"),
                    r("If-Range", "IF_RANGE"),
                    r("If-Unmodified-Since", "IF_UNMODIFIED_SINCE"),
                    r("Last-Modified", "LAST_MODIFIED"),
                    r("Link", "LINK"),
                    r("Location", "LOCATION"),
                    r("Max-Forwards", "MAX_FORWARDS"),
                    r("Origin", "ORIGIN"),
                    r("Pragma", "PRAGMA"),
                    r("Proxy-Authenticate", "PROXY_AUTHENTICATE"),
                    r("Proxy-Authorization", "PROXY_AUTHORIZATION"),
                    r("Range", "RANGE"),
                    r("Referer", "REFERER"),
                    r("Retry-After", "RETRY_AFTER"),
                    r("Server", "SERVER"),
                    r("Set-Cookie", "SET_COOKIE"),
                    r("Set-Cookie2", "SET_COOKIE2"),
                    r("TE", "TE"),
                    r("Trailer", "TRAILER"),
                    r("Transfer-Encoding", "TRANSFER_ENCODING"),
                    r("Upgrade", "UPGRADE"),
                    r("User-Agent", "USER_AGENT"),
                    r("Vary", "VARY"),
                    r("Via", "VIA"),
                    r("Warning", "WARNING"),
                    r("WWW-Authenticate", "WWW_AUTHENTICATE"))
            .collect(toList());

    private static Recipe r(String literal, String constantName) {
        return new ReplaceStringLiteralWithConstant(literal, FULLY_QUALIFIED + constantName);
    }

    @Getter
    final String displayName = "Replace String literals with `HttpHeaders` constants";

    @Getter
    final String description = "Replace String literals with `org.springframework.http.HttpHeaders` constants.";

    @Override
    public List<Recipe> getRecipeList() {
        return recipeList;
    }
}
