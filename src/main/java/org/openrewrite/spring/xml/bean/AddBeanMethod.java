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

import org.openrewrite.Formatting;
import org.openrewrite.java.refactor.AddAnnotation;
import org.openrewrite.java.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinition;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

class AddBeanMethod extends ScopedJavaRefactorVisitor {
    private static final JavaType.Class BEAN_TYPE = JavaType.Class.build("org.springframework.context.annotation.Bean");

    private final String name;
    private final JavaType.Class returnType;
    private final boolean statik;
    private final List<Statement> arguments;

    @Nullable
    private final String initMethod;

    @Nullable
    private final String destroyMethod;

    private final UUID methodId = randomId();

    public AddBeanMethod(J.ClassDecl scope,
                         String beanName,
                         RewriteBeanDefinition bean,
                         RewriteBeanDefinitionRegistry registry) {
        this(scope, beanName, bean.getType(), false,
                Formatting.formatFirstPrefix(bean.getPropertyValues().stream()
                        .map(pv -> {
                            RewriteBeanDefinition propertyBean = registry.getBeanDefinition(pv.getName());
                            JavaType.Class propertyBeanType = JavaType.Class.build(propertyBean.getBeanClassName());

                            return new J.VariableDecls(
                                    randomId(),
                                    emptyList(),
                                    emptyList(),
                                    J.Ident.build(
                                            randomId(),
                                            propertyBeanType.getClassName(),
                                            propertyBeanType,
                                            Formatting.EMPTY
                                    ),
                                    null,
                                    emptyList(),
                                    singletonList(new J.VariableDecls.NamedVar(
                                            randomId(),
                                            J.Ident.build(
                                                    randomId(),
                                                    pv.getName(),
                                                    propertyBeanType,
                                                    format(" ")),
                                            emptyList(),
                                            null,
                                            propertyBeanType,
                                            EMPTY)
                                    ),
                                    format(" "));
                        })
                        .collect(Collectors.toList()), ""),
                bean.isLazyInit(),
                bean.isPrototype(),
                bean.getInitMethodName(),
                bean.getDestroyMethodName());
    }

    public AddBeanMethod(J.ClassDecl scope,
                         String name,
                         JavaType.Class returnType,
                         boolean statik,
                         List<Statement> arguments) {
        this(scope, name, returnType, statik, arguments, false, false, null, null);
    }

    public AddBeanMethod(J.ClassDecl scope,
                         String name,
                         JavaType.Class returnType,
                         boolean statik,
                         List<Statement> arguments,
                         boolean lazy,
                         boolean prototype,
                         @Nullable String initMethod,
                         @Nullable String destroyMethod) {
        super(scope.getId());
        this.name = name;
        this.returnType = returnType;
        this.statik = statik;
        this.arguments = arguments;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;

        if (lazy) {
            andThen(new AddAnnotation(methodId, "org.springframework.context.annotation.Lazy"));
        }

        if (prototype) {
            JavaType.Class cbf = JavaType.Class.build("org.springframework.beans.factory.config.ConfigurableBeanFactory");
            maybeAddImport(cbf.getFullyQualifiedName());
            andThen(new AddAnnotation(methodId, "org.springframework.context.annotation.Scope",
                    TreeBuilder.buildName("ConfigurableBeanFactory.SCOPE_PROTOTYPE").withType(cbf)));
        }
    }

    public UUID getMethodId() {
        return methodId;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        if (isScope()) {

            List<J> statements = new ArrayList<>(c.getBody().getStatements());

            int insertionIndex = statements.size();
            for (int i = 0; i < statements.size(); i++) {
                J statement = statements.get(i);
                if (statement.whenType(J.MethodDecl.class)
                        .map(m -> !m.isConstructor() && m.hasModifier("public") && m.getSimpleName().compareTo(name) > 0)
                        .orElse(false)) {
                    insertionIndex = i;
                    break;
                }
            }

            maybeAddImport(BEAN_TYPE.getFullyQualifiedName());
            maybeAddImport(returnType);

            Formatting format = formatter.format(classDecl.getBody());

            J.MethodDecl beanMethod = new J.MethodDecl(methodId,
                    singletonList(buildBeanAnnotation()),
                    emptyList(),
                    null,
                    TreeBuilder.buildName(returnType.getClassName(), EMPTY).withType(returnType),
                    TreeBuilder.buildName(name, format(" ")),
                    new J.MethodDecl.Parameters(randomId(), arguments, EMPTY),
                    null,
                    new J.Block<>(randomId(), null,
                            emptyList(), format(" "), format.getPrefix()),
                    null,
                    EMPTY).withModifiers("public");

            if (statik) {
                beanMethod = beanMethod.withModifiers("static");
            }

            beanMethod = beanMethod.withFormatting(format.withPrefix("\n" + format.getPrefix()));
            beanMethod = beanMethod.withModifiers(formatFirstPrefix(beanMethod.getModifiers(), format.getPrefix()));

            statements.add(insertionIndex, beanMethod);

            c = c.withBody(c.getBody().withStatements(statements));
        }

        return c;
    }

    private J.Annotation buildBeanAnnotation() {
        List<Expression> arguments = new ArrayList<>();

        if(initMethod != null) {
            arguments.add(new J.Assign(
                    randomId(),
                    J.Ident.build(randomId(), "initMethod", JavaType.Primitive.String, EMPTY),
                    new J.Literal(randomId(), initMethod, "\"" + initMethod + "\"", JavaType.Primitive.String, EMPTY),
                    JavaType.Primitive.String,
                    EMPTY
            ));
        }

        if(destroyMethod != null) {
            arguments.add(new J.Assign(
                    randomId(),
                    J.Ident.build(randomId(), "destroyMethod", JavaType.Primitive.String, EMPTY),
                    new J.Literal(randomId(), destroyMethod, "\"" + destroyMethod + "\"", JavaType.Primitive.String, EMPTY),
                    JavaType.Primitive.String,
                    initMethod != null ? format(" ") : EMPTY
            ));
        }

        return new J.Annotation(randomId(),
                J.Ident.build(randomId(), BEAN_TYPE.getClassName(), BEAN_TYPE, EMPTY),
                arguments.isEmpty() ? null : new J.Annotation.Arguments(randomId(), arguments, EMPTY),
                EMPTY);
    }
}
