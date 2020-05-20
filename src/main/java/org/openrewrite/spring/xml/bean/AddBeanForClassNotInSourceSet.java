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

import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
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
