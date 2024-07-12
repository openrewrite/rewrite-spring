/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.trait;

import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.trait.Annotated;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Value
public class SpringRequestMapping implements Trait<J.Annotation> {
    private static final List<AnnotationMatcher> REST_ENDPOINTS = Stream.of("Request", "Get", "Post", "Put", "Delete", "Patch")
            .map(method -> new AnnotationMatcher("@org.springframework.web.bind.annotation." + method + "Mapping"))
            .collect(toList());

    Cursor cursor;

    public String getHttpMethod() {
        J.Annotation annotation = getCursor().getValue();
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
        assert type != null;
        return type.getClassName().startsWith("Request") ?
                new Annotated(cursor).getDefaultAttribute("method").map(Literal::toString).orElse("GET") :
                type.getClassName().replace("Mapping", "").toUpperCase();
    }

    public String getPath() {
        String path =
                cursor.getPathAsStream()
                        .filter(J.ClassDeclaration.class::isInstance)
                        .map(classDecl -> ((J.ClassDeclaration) classDecl).getAllAnnotations().stream()
                                .filter(SpringRequestMapping::hasRequestMapping)
                                .findAny()
                                .flatMap(classMapping -> new Annotated(new Cursor(null, classMapping))
                                        .getDefaultAttribute(null)
                                        .map(Literal::getString))
                                .orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("/")) +
                new Annotated(cursor)
                        .getDefaultAttribute(null)
                        .map(Literal::getString)
                        .orElse("");
        return path.replace("//", "/");
    }

    public static class Matcher extends SimpleTraitMatcher<SpringRequestMapping> {
        @Override
        protected @Nullable SpringRequestMapping test(Cursor cursor) {
            Object value = cursor.getValue();
            return value instanceof J.Annotation && hasRequestMapping((J.Annotation) value) ?
                    new SpringRequestMapping(cursor) :
                    null;
        }
    }

    private static boolean hasRequestMapping(J.Annotation ann) {
        for (AnnotationMatcher restEndpoint : REST_ENDPOINTS) {
            if (restEndpoint.matches(ann)) {
                return true;
            }
        }
        return false;
    }
}
