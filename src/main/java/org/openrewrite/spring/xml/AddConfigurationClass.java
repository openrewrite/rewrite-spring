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
package org.openrewrite.spring.xml;

import org.openrewrite.spring.xml.bean.AddBeanForClassNotInSourceSet;
import org.openrewrite.spring.xml.bean.AddComponentScan;
import org.openrewrite.spring.xml.bean.AddPropertySourcesPlaceholderConfigurer;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.JavaRefactorVisitor;
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
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        if (cu.getMetadata()
                .getOrDefault(SpringMetadata.FILE_TYPE, "unknown")
                .equals("ConfigurationClass")) {
            return super.visitCompilationUnit(cu);
        }
        return cu;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        andThen(new AddAnnotation.Scoped(classDecl, "org.springframework.context.annotation.Configuration"));
        andThen(new AddComponentScan(classDecl, beanDefinitionRegistry));
        andThen(new AddBeanForClassNotInSourceSet(classDecl, beanDefinitionRegistry, mainSourceSet));
        andThen(new AddPropertySourcesPlaceholderConfigurer(classDecl, beanDefinitionRegistry));

        return super.visitClassDecl(classDecl);
    }
}
