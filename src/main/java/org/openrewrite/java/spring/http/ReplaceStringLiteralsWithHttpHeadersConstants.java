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

public class ReplaceStringLiteralsWithHttpHeadersConstants extends Recipe {

    private static final String FQN = "org.springframework.http.HttpHeaders.";

    private static final List<Recipe> recipeList = Stream.<Map.Entry<String, String>>of(
                    e("ACCEPT", "Accept"),
                    e("ACCEPT_CHARSET", "Accept-Charset"),
                    e("ACCEPT_ENCODING", "Accept-Encoding"),
                    e("ACCEPT_LANGUAGE", "Accept-Language"),
                    e("ACCEPT_PATCH", "Accept-Patch"),
                    e("ACCEPT_RANGES", "Accept-Ranges"),
                    e("ACCESS_CONTROL_ALLOW_CREDENTIALS", "Access-Control-Allow-Credentials"),
                    e("ACCESS_CONTROL_ALLOW_HEADERS", "Access-Control-Allow-Headers"),
                    e("ACCESS_CONTROL_ALLOW_METHODS", "Access-Control-Allow-Methods"),
                    e("ACCESS_CONTROL_ALLOW_ORIGIN", "Access-Control-Allow-Origin"),
                    e("ACCESS_CONTROL_EXPOSE_HEADERS", "Access-Control-Expose-Headers"),
                    e("ACCESS_CONTROL_MAX_AGE", "Access-Control-Max-Age"),
                    e("ACCESS_CONTROL_REQUEST_HEADERS", "Access-Control-Request-Headers"),
                    e("ACCESS_CONTROL_REQUEST_METHOD", "Access-Control-Request-Method"),
                    e("AGE", "Age"),
                    e("ALLOW", "Allow"),
                    e("AUTHORIZATION", "Authorization"),
                    e("CACHE_CONTROL", "Cache-Control"),
                    e("CONNECTION", "Connection"),
                    e("CONTENT_ENCODING", "Content-Encoding"),
                    e("CONTENT_DISPOSITION", "Content-Disposition"),
                    e("CONTENT_LANGUAGE", "Content-Language"),
                    e("CONTENT_LENGTH", "Content-Length"),
                    e("CONTENT_LOCATION", "Content-Location"),
                    e("CONTENT_RANGE", "Content-Range"),
                    e("CONTENT_TYPE", "Content-Type"),
                    e("COOKIE", "Cookie"),
                    e("DATE", "Date"),
                    e("ETAG", "ETag"),
                    e("EXPECT", "Expect"),
                    e("EXPIRES", "Expires"),
                    e("FROM", "From"),
                    e("HOST", "Host"),
                    e("IF_MATCH", "If-Match"),
                    e("IF_MODIFIED_SINCE", "If-Modified-Since"),
                    e("IF_NONE_MATCH", "If-None-Match"),
                    e("IF_RANGE", "If-Range"),
                    e("IF_UNMODIFIED_SINCE", "If-Unmodified-Since"),
                    e("LAST_MODIFIED", "Last-Modified"),
                    e("LINK", "Link"),
                    e("LOCATION", "Location"),
                    e("MAX_FORWARDS", "Max-Forwards"),
                    e("ORIGIN", "Origin"),
                    e("PRAGMA", "Pragma"),
                    e("PROXY_AUTHENTICATE", "Proxy-Authenticate"),
                    e("PROXY_AUTHORIZATION", "Proxy-Authorization"),
                    e("RANGE", "Range"),
                    e("REFERER", "Referer"),
                    e("RETRY_AFTER", "Retry-After"),
                    e("SERVER", "Server"),
                    e("SET_COOKIE", "Set-Cookie"),
                    e("SET_COOKIE2", "Set-Cookie2"),
                    e("TE", "TE"),
                    e("TRAILER", "Trailer"),
                    e("TRANSFER_ENCODING", "Transfer-Encoding"),
                    e("UPGRADE", "Upgrade"),
                    e("USER_AGENT", "User-Agent"),
                    e("VARY", "Vary"),
                    e("VIA", "Via"),
                    e("WARNING", "Warning"),
                    e("WWW_AUTHENTICATE", "WWW-Authenticate"))
            .map(entry -> new ReplaceStringLiteralWithConstant(entry.getValue(), FQN + entry.getKey()))
            .collect(toList());

    private static Map.Entry<String, String> e(String constant, String literal) {
        return new AbstractMap.SimpleImmutableEntry<>(constant, literal);
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
