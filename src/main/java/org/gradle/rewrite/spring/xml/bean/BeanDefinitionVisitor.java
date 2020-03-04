package org.gradle.rewrite.spring.xml.bean;

import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.tree.J;

public abstract class BeanDefinitionVisitor extends ScopedJavaRefactorVisitor {
    protected final RewriteBeanDefinitionRegistry registry;

    public BeanDefinitionVisitor(J.ClassDecl profileConfigurationClass, RewriteBeanDefinitionRegistry registry) {
        super(profileConfigurationClass.getId());
        this.registry = registry;
    }
}
