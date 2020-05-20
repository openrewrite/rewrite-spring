package org.openrewrite.spring.xml;

import org.openrewrite.java.refactor.AddAnnotation;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.tree.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;

/**
 * Any changes necessary to make a component defined in XML configuration configurable via
 * component scanning instead.
 */
class MakeComponentScannable extends JavaRefactorVisitor {
    private final BeanDefinitionRegistry registry;

    @Override
    public String getName() {
        return "spring.beans.AnnotationBasedBeanConfiguration";
    }

    public MakeComponentScannable(BeanDefinitionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        andThen(new AnnotateBeanClass(registry));
        return super.visitCompilationUnit(cu);
    }

    private static class AnnotateBeanClass extends JavaRefactorVisitor {
        private final BeanDefinitionRegistry beanDefinitionRegistry;

        private AnnotateBeanClass(BeanDefinitionRegistry beanDefinitionRegistry) {
            this.beanDefinitionRegistry = beanDefinitionRegistry;
        }

        @Override
        public boolean isCursored() {
            return true;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            Arrays.stream(beanDefinitionRegistry.getBeanDefinitionNames())
                    .map(beanDefinitionRegistry::getBeanDefinition)
                    .filter(bd -> TypeUtils.isOfClassType(classDecl.getType(), bd.getBeanClassName()))
                    .findAny()
                    .ifPresent(beanDefinition -> {
                        andThen(new AddAnnotation(classDecl.getId(), "org.springframework.stereotype.Component"));
                        andThen(new AutowireFields(classDecl, beanDefinition));

                        if (beanDefinition.isLazyInit()) {
                            andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.Lazy"));
                        }

                        if (beanDefinition.isPrototype()) {
                            JavaType.Class cbf = JavaType.Class.build("org.springframework.beans.factory.config.ConfigurableBeanFactory");
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
                    });

            return super.visitClassDecl(classDecl);
        }
    }

    private static class AutowireFields extends ScopedJavaRefactorVisitor {
        private final BeanDefinition beanDefinition;

        public AutowireFields(J.ClassDecl classDecl, BeanDefinition beanDefinition) {
            super(classDecl.getId());
            this.beanDefinition = beanDefinition;
        }

        @Override
        public J visitMultiVariable(J.VariableDecls multiVariable) {
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
                                                        JavaType genericValueType = Optional.ofNullable((JavaType) JavaType.Primitive.fromKeyword(genericValue.getType()))
                                                                .orElseGet(() -> JavaType.Class.build(genericValue.getType()));

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

            JavaType type = multiVariable.getTypeExpr().getType();
            JavaType.Primitive primitive = TypeUtils.asPrimitive(type);

            if (TypeUtils.isString(type) || value.contains("${") || value.contains("#{")) {
                return Optional.of(new J.Literal(randomId(), value, "\"" + value + "\"", JavaType.Primitive.String, EMPTY));
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
                        JavaType.Primitive.Char.equals(primitive) ? "'" + value + "'" : value,
                        primitive, EMPTY));
            }

            return Optional.empty();
        }
    }
}
