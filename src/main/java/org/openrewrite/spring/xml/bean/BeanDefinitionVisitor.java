package org.openrewrite.spring.xml.bean;

import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.tree.J;

public abstract class BeanDefinitionVisitor extends ScopedJavaRefactorVisitor {
    protected final RewriteBeanDefinitionRegistry registry;

    public BeanDefinitionVisitor(J.ClassDecl profileConfigurationClass, RewriteBeanDefinitionRegistry registry) {
        super(profileConfigurationClass.getId());
        this.registry = registry;
    }
}
