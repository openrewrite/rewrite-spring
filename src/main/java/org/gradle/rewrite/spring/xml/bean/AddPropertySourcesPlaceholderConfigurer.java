package org.gradle.rewrite.spring.xml.bean;

import org.openrewrite.java.refactor.AddImport;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TreeBuilder;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

public class AddPropertySourcesPlaceholderConfigurer implements BeanDefinitionHandler {
    static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^:}]+):?[^}]*}");

    @Override
    public void maybeGenerate(BeanDefinitionRegistry beanDefinitionRegistry, JavaRefactorVisitor visitor, J.ClassDecl classDecl) {
        AtomicInteger seq = new AtomicInteger();
        List<BeanDefinition> propertyPlaceholders = stream(beanDefinitionRegistry.getBeanDefinitionNames())
                .filter(name -> name.startsWith("org.springframework.beans.factory.config.PropertyPlaceholderConfigurer"))
                .map(beanDefinitionRegistry::getBeanDefinition)
                .sorted(Comparator.comparingInt(bd -> Optional.ofNullable(bd.getPropertyValues().getPropertyValue("order"))
                    .map(PropertyValue::getValue)
                    .map(Integer.class::cast)
                    .orElseGet(seq::incrementAndGet)))
                .collect(toList());

        Map<String, String> propertyNamesByReference = propertyPlaceholders.stream()
                .map(pp -> pp.getPropertyValues().getPropertyValue("locations"))
                .filter(Objects::nonNull)
                .flatMap(pv -> pv.getValue() == null ? Stream.empty() : stream((String[]) pv.getValue()))
                .flatMap(location -> PROPERTY_PATTERN.matcher(location).results())
                .collect(Collectors.toMap(res -> res.group(0), res -> res.group(1), (m1, m2) -> m1, LinkedHashMap::new));

        if (!propertyPlaceholders.isEmpty()) {
            AddBeanMethod addBeanMethod = new AddBeanMethod(classDecl,
                    "properties",
                    JavaType.Class.build("org.springframework.context.support.PropertySourcesPlaceholderConfigurer"),
                    true,
                    formatFirstPrefix(propertyNamesByReference.entrySet().stream()
                        .map(nameByRef -> (Statement) valueArgument(visitor, nameByRef.getValue(), nameByRef.getKey()).withPrefix(" "))
                        .collect(toList()), "")
            );

            visitor.andThen(addBeanMethod);

            boolean ignoreResourceNotFound = propertyPlaceholders.stream()
                    .anyMatch(pp -> Optional.ofNullable(pp.getPropertyValues().getPropertyValue("ignoreResourceNotFound"))
                            .map(v -> (Boolean) v.getValue()).orElse(false));

            boolean ignoreUnresolvablePlaceholders = propertyPlaceholders.stream()
                    .anyMatch(pp -> Optional.ofNullable(pp.getPropertyValues().getPropertyValue("ignoreUnresolvablePlaceholders"))
                            .map(v -> (Boolean) v.getValue()).orElse(false));

            visitor.andThen(new AddBeanMethodBody(addBeanMethod.getMethodId(),
                    "PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();\n" +
                            "Resource[] resources = new Resource[]{\n" +
                                propertyPlaceholders.stream()
                                    .map(pp -> pp.getPropertyValues().getPropertyValue("locations"))
                                    .filter(Objects::nonNull)
                                    .flatMap(pv -> pv.getValue() == null ? Stream.empty() : stream((String[]) pv.getValue()))
                                    .map(location -> {
                                        location = "\"" + propertyNamesByReference.entrySet().stream()
                                                .reduce(location, (acc, nameByRef) -> acc.replace(nameByRef.getKey(), "\" + " + nameByRef.getValue() + " + \""), (r1, r2) -> r1) + "\"";
                                        location = location.replace("\"\" + ", "");
                                        location = location.replace(" + \"\"", "");

                                        if(location.startsWith("\"classpath:")) {
                                            AddImport addImportClassPathResource = new AddImport("org.springframework.core.io.ClassPathResource", null, false);
                                            if(!visitor.andThen().contains(addImportClassPathResource)) {
                                                visitor.andThen(addImportClassPathResource);
                                            }
                                            return "new ClassPathResource(\"" + location.substring("\"classpath:".length()) + ")";
                                        }

                                        AddImport addImportFileSystemResource = new AddImport("org.springframework.core.io.FileSystemResource", null, false);
                                        if(!visitor.andThen().contains(addImportFileSystemResource)) {
                                            visitor.andThen(addImportFileSystemResource);
                                        }
                                        return "new FileSystemResource(" + location + ")";
                                    })
                                    .collect(joining(",\n    ", "    ", "\n")) +
                            "};\n" +
                            "pspc.setLocations(resources);\n" +
                            (ignoreResourceNotFound ? "pspc.setIgnoreResourceNotFound(true);\n" : "") +
                            (ignoreUnresolvablePlaceholders ? "pspc.setIgnoreUnresolvablePlaceholders(true);\n" : "") +
                            "return pspc;",
                    JavaType.Class.build("org.springframework.core.io.ClassPathResource"),
                    JavaType.Class.build("org.springframework.core.io.FileSystemResource"),
                    JavaType.Class.build("org.springframework.core.io.Resource"),
                    JavaType.Class.build("org.springframework.context.support.PropertySourcesPlaceholderConfigurer")));

            visitor.andThen(new AddImport("org.springframework.core.io.Resource", null, false));
            visitor.maybeAddImport("org.springframework.beans.factory.annotation.Value");
        }
    }

    private Statement valueArgument(JavaRefactorVisitor visitor, String name, String value) {
        JavaType.Class valueType = JavaType.Class.build("org.springframework.beans.factory.annotation.Value");

        J.Annotation valueAnnotation = new J.Annotation(randomId(),
                J.Ident.build(randomId(), valueType.getClassName(), valueType, EMPTY),
                new J.Annotation.Arguments(
                        randomId(),
                        singletonList(new J.Literal(randomId(), value, "\"" + value + "\"", JavaType.Primitive.String, EMPTY)),
                        EMPTY),
                EMPTY);

        return new J.VariableDecls(randomId(), singletonList(valueAnnotation), emptyList(),
                TreeBuilder.buildName("String").withType(JavaType.Class.build("java.lang.String")).withPrefix(" "),
                null,
                emptyList(),
                singletonList(new J.VariableDecls.NamedVar(randomId(),
                        TreeBuilder.buildName(name),
                        emptyList(), null, JavaType.Class.build("java.lang.String"), format(" "))),
                EMPTY);
    }
}
