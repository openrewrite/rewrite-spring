package org.gradle.rewrite.spring.xml.bean;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

public interface BeanDefinitionHandler {
    void maybeGenerate(BeanDefinitionRegistry beanDefinitionRegistry, JavaRefactorVisitor visitor, J.ClassDecl classDecl);
}
