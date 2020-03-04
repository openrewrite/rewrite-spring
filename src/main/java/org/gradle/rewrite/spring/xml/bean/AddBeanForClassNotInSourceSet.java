package org.gradle.rewrite.spring.xml.bean;

import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.nio.file.Path;

import static java.util.Collections.emptyList;

public class AddBeanForClassNotInSourceSet extends BeanDefinitionVisitor {
    private final Path mainSourceSet;

    public AddBeanForClassNotInSourceSet(J.ClassDecl profileConfigurationClass, RewriteBeanDefinitionRegistry registry, Path mainSourceSet) {
        super(profileConfigurationClass, registry);
        this.mainSourceSet = mainSourceSet;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        if (isScope()) {
            registry.getBeanDefinitions(null).entrySet().stream()
                    .filter(bdByName -> !mainSourceSet.resolve(bdByName.getValue().getBeanClassName().replace(".", "/"))
                            .toFile().exists())
                    .forEach(bean -> {
                        JavaType.Class beanType = JavaType.Class.build(bean.getValue().getBeanClassName());
                        andThen(new AddBeanMethod(classDecl, bean.getKey(), beanType, false, emptyList()));
                    });
        }

        return super.visitClassDecl(classDecl);
    }
}
