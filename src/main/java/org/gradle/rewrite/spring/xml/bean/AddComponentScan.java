package org.gradle.rewrite.spring.xml.bean;

import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinition;
import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.refactor.AddAnnotation;
import org.openrewrite.java.refactor.Formatter;
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
        if(isScope()) {
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

                andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.ComponentScan", arguments));
            }
        }

        return super.visitClassDecl(classDecl);
    }
}
