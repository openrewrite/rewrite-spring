package org.gradle.rewrite.spring.xml.bean;

import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinition;
import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TreeBuilder;

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

public class AddPropertySourcesPlaceholderConfigurer extends BeanDefinitionVisitor {
    static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^:}]+):?[^}]*}");

    public AddPropertySourcesPlaceholderConfigurer(J.ClassDecl profileConfigurationClass, RewriteBeanDefinitionRegistry beanDefinitionRegistry) {
        super(profileConfigurationClass, beanDefinitionRegistry);
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        if(isScope()) {
            AtomicInteger seq = new AtomicInteger();

            List<RewriteBeanDefinition> propertyPlaceholders = registry.getBeanDefinitions(RewriteBeanDefinition.Type.PropertyPlaceholder).values().stream()
                    .sorted(Comparator.comparingInt(bd -> bd.getIntegerProperty("order").orElseGet(seq::incrementAndGet)))
                    .collect(toList());

            Map<String, String> propertyNamesByReference = propertyPlaceholders.stream()
                    .flatMap(pp -> pp.getStringProperty("location").stream().flatMap(loc -> stream(loc.split(","))))
                    .flatMap(location -> PROPERTY_PATTERN.matcher(location).results())
                    .collect(Collectors.toMap(res -> res.group(0), res -> res.group(1), (m1, m2) -> m1, LinkedHashMap::new));

            if (!propertyPlaceholders.isEmpty()) {
                AddBeanMethod addBeanMethod = new AddBeanMethod(classDecl,
                        "properties",
                        JavaType.Class.build("org.springframework.context.support.PropertySourcesPlaceholderConfigurer"),
                        true,
                        formatFirstPrefix(propertyNamesByReference.entrySet().stream()
                                .map(nameByRef -> (Statement) valueArgument(nameByRef.getValue(), nameByRef.getKey()).withPrefix(" "))
                                .collect(toList()), "")
                );

                andThen(addBeanMethod);

                boolean ignoreResourceNotFound = propertyPlaceholders.stream()
                        .anyMatch(pp -> pp.getBooleanProperty("ignoreResourceNotFound").orElse(false));

                boolean ignoreUnresolvablePlaceholders = propertyPlaceholders.stream()
                        .anyMatch(pp -> pp.getBooleanProperty("ignoreUnresolvable").orElse(false));

                andThen(new AddBeanMethodBody(addBeanMethod.getMethodId(),
                        "PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();\n" +
                                "Resource[] resources = new Resource[]{\n" +
                                propertyPlaceholders.stream()
                                        .flatMap(pp -> pp.getStringProperty("location").stream().flatMap(loc -> stream(loc.split(","))))
                                        .map(location -> {
                                            location = "\"" + propertyNamesByReference.entrySet().stream()
                                                    .reduce(location, (acc, nameByRef) -> acc.replace(nameByRef.getKey(), "\" + " + nameByRef.getValue() + " + \""), (r1, r2) -> r1) + "\"";
                                            location = location.replace("\"\" + ", "");
                                            location = location.replace(" + \"\"", "");

                                            if (location.startsWith("\"classpath:")) {
                                                addImport("org.springframework.core.io.ClassPathResource");
                                                return "new ClassPathResource(\"" + location.substring("\"classpath:".length()) + ")";
                                            }

                                            addImport("org.springframework.core.io.FileSystemResource");
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

                addImport("org.springframework.core.io.Resource");
                maybeAddImport("org.springframework.beans.factory.annotation.Value");
            }
        }

        return super.visitClassDecl(classDecl);
    }

    private Statement valueArgument(String name, String value) {
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
