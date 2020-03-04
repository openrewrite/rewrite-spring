package org.gradle.rewrite.spring.xml;

import org.gradle.rewrite.spring.xml.bean.AddBeanForClassNotInSourceSet;
import org.gradle.rewrite.spring.xml.bean.AddComponentScan;
import org.gradle.rewrite.spring.xml.bean.AddPropertySourcesPlaceholderConfigurer;
import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.refactor.AddAnnotation;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.nio.file.Path;

public class AddConfigurationClass extends JavaRefactorVisitor {
    private final RewriteBeanDefinitionRegistry beanDefinitionRegistry;
    private final Path mainSourceSet;

    public AddConfigurationClass(RewriteBeanDefinitionRegistry beanDefinitionRegistry, Path mainSourceSet) {
        this.beanDefinitionRegistry = beanDefinitionRegistry;
        this.mainSourceSet = mainSourceSet;
    }

    @Override
    public String getName() {
        return "spring.beans.AddBeansToConfiguration";
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
        andThen(new AddComponentScan(classDecl, beanDefinitionRegistry));
        andThen(new AddBeanForClassNotInSourceSet(classDecl, beanDefinitionRegistry, mainSourceSet));
        andThen(new AddPropertySourcesPlaceholderConfigurer(classDecl, beanDefinitionRegistry));

        return super.visitClassDecl(classDecl);
    }
}
