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
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

import static org.openrewrite.java.trait.Traits.annotated;

@Value
public class SpringBean implements Trait<Tree> {
    Cursor cursor;

    public @Nullable String getName() {
        if (getTree() instanceof Xml.Tag) {
            Xml.Tag tag = (Xml.Tag) getTree();
            return tag.getAttributes().stream()
                    .filter(a -> "id".equals(a.getKey().getName()))
                    .findFirst()
                    .map(Xml.Attribute::getValueAsString)
                    .orElse(null);
        } else if (getTree() instanceof J.Annotation) {
            return annotated("org.springframework.context.annotation.Bean")
                    .get(cursor)
                    .flatMap(a -> a.getDefaultAttribute("name"))
                    .map(name -> name.getValue(String.class))
                    .orElse(null);
        }
        return null;
    }

    public Optional<J.MethodDeclaration> getBeanMethod() {
        if (getTree() instanceof J.MethodDeclaration) {
            return Optional.of((J.MethodDeclaration) getTree());
        }
        return Optional.empty();
    }

    public Optional<Xml.Tag> getXmlBeanConfiguration() {
        if (getTree() instanceof Xml.Tag) {
            return Optional.of((Xml.Tag) getTree());
        }
        return Optional.empty();
    }

    public static class Matcher extends SimpleTraitMatcher<SpringBean> {
        @Override
        protected @Nullable SpringBean test(Cursor cursor) {
            Object value = cursor.getValue();
            if (value instanceof Xml.Tag) {
                XPathMatcher springBean = new XPathMatcher("/beans/bean");
                if (springBean.matches(cursor)) {
                    return new SpringBean(cursor);
                }
            } else if (value instanceof J.Annotation) {
                AnnotationMatcher beanAnnotation = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
                if (beanAnnotation.matches((J.Annotation) value)) {
                    return new SpringBean(cursor.getParentTreeCursor());
                }
            }
            return null;
        }
    }
}
