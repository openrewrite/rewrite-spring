package org.gradle.rewrite.spring.xml;

import org.gradle.rewrite.spring.xml.bean.AddPropertySourcesPlaceholderConfigurer;
import org.gradle.rewrite.spring.xml.bean.BeanDefinitionHandler;
import org.openrewrite.Formatting;
import org.openrewrite.java.refactor.AddAnnotation;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.List;
import java.util.Objects;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

public class AddConfigurationClass extends JavaRefactorVisitor {
    private static final BeanDefinitionHandler[] GENERATORS = {
            new AddPropertySourcesPlaceholderConfigurer()
    };

    private final BeanDefinitionRegistry beanDefinitionRegistry;

    public AddConfigurationClass(BeanDefinitionRegistry beanDefinitionRegistry) {
        this.beanDefinitionRegistry = beanDefinitionRegistry;
    }

    @Override
    public String getName() {
        return "spring.beans.AddBeansToConfiguration";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        if (cu.getMetadata()
                .getOrDefault("spring.beans.fileType", "unknown")
                .equals("ConfigurationClass")) {
            return super.visitCompilationUnit(cu);
        }
        return cu;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.Configuration"));
        maybeAddComponentScan(classDecl);

        for (BeanDefinitionHandler generator : GENERATORS) {
            generator.maybeGenerate(beanDefinitionRegistry, this, classDecl);
        }

        return super.visitClassDecl(classDecl);
    }

    private void maybeAddComponentScan(J.ClassDecl classDecl) {
        List<String> basePackagesToComponentScan = stream(beanDefinitionRegistry.getBeanDefinitionNames())
                .filter(n -> n.startsWith("org.springframework.context.annotation.ComponentScan"))
                .map(beanDefinitionRegistry::getBeanDefinition)
                .map(bd -> bd.getPropertyValues().getPropertyValue("basePackage"))
                .filter(Objects::nonNull)
                .map(pv -> (String) pv.getValue())
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
            if(basePackagesToComponentScan.size() == 1) {
                String bp = basePackagesToComponentScan.get(0);
                arguments = new J.Literal(randomId(), bp, "\"" + bp + "\"",
                        JavaType.Primitive.String, EMPTY);
            } else {
                String prefix = formatter.findIndent(0, classDecl).getPrefix();

                List<Expression> argExpressions = basePackagesToComponentScan.stream()
                        .map(bp -> (Expression) new J.Literal(randomId(), bp, "\"" + bp + "\"",
                                JavaType.Primitive.String, format(prefix)))
                        .collect(toList());
                argExpressions = formatLastSuffix(argExpressions, "\n");

                arguments = new J.NewArray(randomId(), null, emptyList(),
                        new J.NewArray.Initializer(randomId(), argExpressions, EMPTY),
                        JavaType.Class.build("java.lang.String"), EMPTY);
            }

            andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.ComponentScan", arguments));
        }
    }
}
