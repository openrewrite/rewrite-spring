/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.trait;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

@Value
public class JaxRsRequestMapping implements Trait<J.Annotation> {

    private static final List<AnnotationMatcher> JAVAX_HTTP_METHODS = Stream.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
            .map(method -> new AnnotationMatcher("@*.ws.rs." + method))
            .collect(toList());

    private static final AnnotationMatcher JAVAX_PATH = new AnnotationMatcher("@*.ws.rs.Path");

    Cursor cursor;

    public String getHttpMethod() {
        J.Annotation annotation = getCursor().getValue();
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
        assert type != null;
        return type.getClassName();
    }

    public String getPath() {
        StringBuilder result = new StringBuilder();

        // Collect path prefixes from enclosing class @Path annotations
        List<String> pathPrefixes = cursor.getPathAsStream()
                .filter(J.ClassDeclaration.class::isInstance)
                .map(J.ClassDeclaration.class::cast)
                .flatMap(classDecl -> classDecl.getLeadingAnnotations().stream()
                        .filter(JAVAX_PATH::matches)
                        .findAny()
                        .flatMap(classMapping -> new Annotated(new Cursor(null, classMapping))
                                .getDefaultAttribute(null)
                                .map(lit -> Stream.of(lit.getString())))
                        .orElse(Stream.of("")))
                .collect(toList());

        // Get method-level @Path annotation if present
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        String methodPath = "";
        if (method != null) {
            for (J.Annotation ann : method.getLeadingAnnotations()) {
                if (JAVAX_PATH.matches(ann)) {
                    methodPath = new Annotated(new Cursor(null, ann))
                            .getDefaultAttribute(null)
                            .map(lit -> lit.getString() != null ? lit.getString() : "")
                            .orElse("");
                    break;
                }
            }
        }

        // Build the full path
        for (int j = 0; j < pathPrefixes.size(); j++) {
            String prefix = pathPrefixes.get(j);
            result.append(combinePaths(prefix, methodPath));
            if (j < pathPrefixes.size() - 1) {
                result.append(", ");
            }
        }

        if (pathPrefixes.isEmpty()) {
            result.append(methodPath.isEmpty() ? "/" : normalizePath(methodPath));
        }

        return result.toString();
    }

    private String combinePaths(String prefix, String suffix) {
        String normalizedPrefix = normalizePath(prefix);
        String normalizedSuffix = normalizePath(suffix);

        if (normalizedPrefix.isEmpty() && normalizedSuffix.isEmpty()) {
            return "/";
        }
        if (normalizedPrefix.isEmpty()) {
            return normalizedSuffix;
        }
        if (normalizedSuffix.isEmpty() || "/".equals(normalizedSuffix)) {
            return normalizedPrefix;
        }

        // Avoid double slashes
        if (normalizedPrefix.endsWith("/") && normalizedSuffix.startsWith("/")) {
            return normalizedPrefix + normalizedSuffix.substring(1);
        }
        if (!normalizedPrefix.endsWith("/") && !normalizedSuffix.startsWith("/")) {
            return normalizedPrefix + "/" + normalizedSuffix;
        }
        return normalizedPrefix + normalizedSuffix;
    }

    private String normalizePath(String path) {
        if (path.isEmpty()) {
            return "";
        }
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    public String getMethodSignature() {
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        if (method == null) {
            return "";
        }
        return method
                .withLeadingAnnotations(emptyList())
                .withBody(null)
                .printTrimmed(cursor);
    }

    public String getLeadingAnnotations() {
        J.MethodDeclaration method = cursor.firstEnclosing(J.MethodDeclaration.class);
        if (method == null) {
            return "";
        }
        return method.getLeadingAnnotations().stream()
                .map(J.Annotation::toString)
                .collect(joining("|"));
    }

    public static class Matcher extends SimpleTraitMatcher<JaxRsRequestMapping> {
        @Override
        protected @Nullable JaxRsRequestMapping test(Cursor cursor) {
            Object value = cursor.getValue();
            return value instanceof J.Annotation && hasHttpMethodAnnotation((J.Annotation) value) ?
                    new JaxRsRequestMapping(cursor) :
                    null;
        }
    }

    private static boolean hasHttpMethodAnnotation(J.Annotation ann) {
        for (AnnotationMatcher matcher : JAVAX_HTTP_METHODS) {
            if (matcher.matches(ann)) {
                return true;
            }
        }
        return false;
    }
}
