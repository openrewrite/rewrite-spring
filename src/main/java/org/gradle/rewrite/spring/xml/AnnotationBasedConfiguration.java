package org.gradle.rewrite.spring.xml;

import org.openrewrite.tree.*;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.op.AddAnnotation;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.J.randomId;

public class AnnotationBasedConfiguration extends RefactorVisitor {
    private final BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

    public AnnotationBasedConfiguration(InputStream beansXml) {
        var reader = new XmlBeanDefinitionReader(registry);
        reader.setValidating(false);
        reader.loadBeanDefinitions(new InputSource(beansXml));
    }

    @Override
    public List<AstTransform> visitCompilationUnit(J.CompilationUnit cu) {
        List<AstTransform> changes = super.visitCompilationUnit(cu);
        for (String beanDefinitionName : registry.getBeanDefinitionNames()) {
            andThen(new AnnotateBeanClass(registry.getBeanDefinition(beanDefinitionName)));
        }
        return changes;
    }

    @Override
    public String getRuleName() {
        return "spring.beans.AnnotationBasedBeanConfiguration";
    }

    private static class AnnotateBeanClass extends RefactorVisitor {
        private final BeanDefinition beanDefinition;

        private AnnotateBeanClass(BeanDefinition beanDefinition) {
            this.beanDefinition = beanDefinition;
        }

        @Override
        public List<AstTransform> visitClassDecl(J.ClassDecl classDecl) {
            if (TypeUtils.isOfClassType(classDecl.getType(), beanDefinition.getBeanClassName())) {
                andThen(new AddAnnotation(classDecl.getId(), "org.springframework.stereotype.Component"));

                if (beanDefinition.isLazyInit()) {
                    andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.Lazy"));
                }

                if (beanDefinition.isPrototype()) {
                    Type.Class cbf = Type.Class.build("org.springframework.beans.factory.config.ConfigurableBeanFactory");
                    maybeAddImport(cbf.getFullyQualifiedName());
                    andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.Scope",
                            TreeBuilder.buildName("ConfigurableBeanFactory.SCOPE_PROTOTYPE").withType(cbf)));
                }

                if (beanDefinition.getInitMethodName() != null) {
                    classDecl.getMethods().stream()
                            .filter(m -> m.getSimpleName().equals(beanDefinition.getInitMethodName()))
                            .findAny()
                            .ifPresent(m -> andThen(new AddAnnotation(m.getId(), "javax.annotation.PostConstruct")));
                }

                if (beanDefinition.getDestroyMethodName() != null) {
                    classDecl.getMethods().stream()
                            .filter(m -> m.getSimpleName().equals(beanDefinition.getDestroyMethodName()))
                            .findAny()
                            .ifPresent(m -> andThen(new AddAnnotation(m.getId(), "javax.annotation.PreDestroy")));
                }
            }

            return super.visitClassDecl(classDecl);
        }

        @Override
        public List<AstTransform> visitMultiVariable(J.VariableDecls multiVariable) {
            getCursor().getParentOrThrow().getParentOrThrow().getTree().whenType(J.ClassDecl.class).ifPresent(classDecl -> {
                stream(beanDefinition.getPropertyValues().spliterator(), false)
                        .filter(prop -> prop.getName().equals(multiVariable.getVars().get(0).getSimpleName()))
                        .findAny()
                        .ifPresent(beanProperty -> {
                            if (beanProperty.getValue() instanceof BeanReference) {
                                andThen(new AddAnnotation(multiVariable.getId(), "org.springframework.beans.factory.annotation.Autowired"));
                            } else if (beanProperty.getValue() instanceof TypedStringValue) {
                                valueExpression(beanProperty.getValue(), multiVariable).ifPresent(valueTree ->
                                        andThen(new AddAnnotation(multiVariable.getId(),
                                                "org.springframework.beans.factory.annotation.Value", valueTree)));
                            }
                        });

                ConstructorArgumentValues constructorArgs = beanDefinition.getConstructorArgumentValues();
                if (constructorArgs.getArgumentCount() > 0) {
                    classDecl.getMethods().stream()
                            .filter(m -> m.isConstructor() && m.getParams().getParams().size() == constructorArgs.getArgumentCount())
                            .findAny()
                            .ifPresent(injectableConstructor -> {
                                List<ConstructorArgumentValues.ValueHolder> indexedValues = Stream.concat(
                                                constructorArgs.getIndexedArgumentValues().entrySet().stream()
                                                        .sorted(Map.Entry.comparingByKey())
                                                        .map(Map.Entry::getValue),
                                                constructorArgs.getGenericArgumentValues().stream()
                                                        .filter(valueHolder -> valueHolder.getType() == null && valueHolder.getName() == null)
                                        ).collect(toList());

                                for (int i = 0; i < indexedValues.size(); i++) {
                                    int param = i;
                                    valueExpression(indexedValues.get(i).getValue(), multiVariable).ifPresent(valueTree ->
                                            andThen(new AddAnnotation(injectableConstructor.getParams().getParams().get(param).getId(),
                                                    "org.springframework.beans.factory.annotation.Value", valueTree)));
                                }

                                List<ConstructorArgumentValues.ValueHolder> genericValues = constructorArgs.getGenericArgumentValues().stream()
                                        .filter(valueHolder -> valueHolder.getType() != null || valueHolder.getName() != null)
                                        .collect(toList());

                                for (ConstructorArgumentValues.ValueHolder genericValue : genericValues) {
                                    valueExpression(genericValue.getValue(), multiVariable).ifPresent(valueTree -> {
                                        Statement param;
                                        if(genericValue.getType() != null) {
                                            param = injectableConstructor.getParams().getParams().stream()
                                                    .filter(methodParam -> {
                                                        Type genericValueType = Optional.ofNullable((Type) Type.Primitive.fromKeyword(genericValue.getType()))
                                                                .orElseGet(() -> Type.Class.build(genericValue.getType()));

                                                        //noinspection ConstantConditions
                                                        return methodParam.whenType(J.VariableDecls.class)
                                                                .map(methodParamVar -> methodParamVar.getTypeExpr().getType().equals(genericValueType))
                                                                .orElse(false);
                                                    })
                                                    .findAny()
                                                    .orElse(null);
                                        } else {
                                            param = injectableConstructor.getParams().getParams().stream()
                                                    .filter(methodParam -> methodParam.whenType(J.VariableDecls.class)
                                                        .map(methodParamVar -> methodParamVar.getVars().get(0).getSimpleName().equals(genericValue.getName()))
                                                        .orElse(false))
                                                    .findAny()
                                                    .orElse(null);
                                        }

                                        if(param != null) {
                                            andThen(new AddAnnotation(param.getId(),
                                                    "org.springframework.beans.factory.annotation.Value", valueTree));
                                        }
                                    });
                                }
                            });
                }
            });

            return super.visitMultiVariable(multiVariable);
        }

        private Optional<Expression> valueExpression(Object typedStringValue, J.VariableDecls multiVariable) {
            if (!(typedStringValue instanceof TypedStringValue) || multiVariable.getTypeExpr() == null) {
                return Optional.empty();
            }

            String value = ((TypedStringValue) typedStringValue).getValue();
            if (value == null) {
                return Optional.empty();
            }

            Type type = multiVariable.getTypeExpr().getType();
            Type.Primitive primitive = TypeUtils.asPrimitive(type);

            if (TypeUtils.isString(type) || value.contains("${") || value.contains("#{")) {
                return Optional.of(new J.Literal(randomId(), value, "\"" + value + "\"", Type.Primitive.String, EMPTY));
            } else if (primitive != null) {
                Object primitiveValue;

                switch (primitive) {
                    case Int:
                        primitiveValue = Integer.parseInt(value);
                        break;
                    case Boolean:
                        primitiveValue = Boolean.parseBoolean(value);
                        break;
                    case Byte:
                    case Char:
                        primitiveValue = value.length() > 0 ? value.charAt(0) : 0;
                        break;
                    case Double:
                        primitiveValue = Double.parseDouble(value);
                        break;
                    case Float:
                        primitiveValue = Float.parseFloat(value);
                        break;
                    case Long:
                        primitiveValue = Long.parseLong(value);
                        break;
                    case Short:
                        primitiveValue = Short.parseShort(value);
                        break;
                    case Null:
                        primitiveValue = null;
                        break;
                    case Void:
                    case String:
                    case None:
                    case Wildcard:
                    default:
                        return Optional.empty(); // not reachable
                }

                return Optional.of(new J.Literal(randomId(), primitiveValue,
                        Type.Primitive.Char.equals(primitive) ? "'" + value + "'" : value,
                        primitive, EMPTY));
            }

            return Optional.empty();
        }
    }
}
