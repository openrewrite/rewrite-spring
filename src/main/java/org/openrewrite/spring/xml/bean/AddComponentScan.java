/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring.xml.bean;

import org.openrewrite.refactor.Formatter;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinition;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

public class AddComponentScan extends BeanDefinitionVisitor {

    public AddComponentScan(J.ClassDecl profileConfigurationClass, RewriteBeanDefinitionRegistry registry) {
        super(profileConfigurationClass, registry);
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        if(configurationClass.isScope(classDecl)) {
            List<String> basePackagesToComponentScan = registry.getBeanDefinitions(RewriteBeanDefinition.Type.ComponentScan).values().stream()
                    .map(bd -> bd.<String>getProperty("basePackage").orElse(null))
                    .filter(Objects::nonNull)
                    .sorted((p1, p2) -> {
                        var p1s = p1.split("\\.");
                        var p2s = p2.split("\\.");

                        for (int i = 0; i < p1s.length; i++) {
                            String s = p1s[i];
                            if (p2s.length < i + 1) {
                                return 1;
                            }
                            if (!s.equals(p2s[i])) {
                                return s.compareTo(p2s[i]);
                            }
                        }

                        return p1s.length < p2s.length ? -1 : 0;
                    })
                    .collect(toList());

            if (!basePackagesToComponentScan.isEmpty()) {
                Expression arguments;
                if (basePackagesToComponentScan.size() == 1) {
                    String bp = basePackagesToComponentScan.get(0);
                    arguments = new J.Literal(randomId(), bp, "\"" + bp + "\"",
                            JavaType.Primitive.String, EMPTY);
                } else {
                    Formatter.Result classDeclIndent = formatter.findIndent(0, classDecl);
                    String prefix = classDeclIndent.getPrefix();

                    List<Expression> argExpressions = basePackagesToComponentScan.stream()
                            .map(bp -> (Expression) new J.Literal(randomId(), bp, "\"" + bp + "\"",
                                    JavaType.Primitive.String, format(prefix)))
                            .collect(toList());
                    argExpressions = formatLastSuffix(argExpressions, classDeclIndent.getPrefix(-1));

                    arguments = new J.NewArray(randomId(), null, emptyList(),
                            new J.NewArray.Initializer(randomId(), argExpressions, EMPTY),
                            JavaType.Class.build("java.lang.String"), EMPTY);
                }

                andThen(new AddAnnotation.Scoped(classDecl, "org.springframework.context.annotation.ComponentScan", arguments));
            }
        }

        return super.visitClassDecl(classDecl);
    }
}
