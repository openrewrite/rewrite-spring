package org.gradle.rewrite.spring.xml;

import com.netflix.rewrite.tree.*;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.visitor.refactor.op.AddAnnotation;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.stream.StreamSupport.stream;

public class AnnotationBasedBeanConfiguration extends RefactorVisitor {
    private final BeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();

    public AnnotationBasedBeanConfiguration(InputStream beansXml) {
        var reader = new XmlBeanDefinitionReader(registry);
        reader.setValidating(false);
        reader.loadBeanDefinitions(new InputSource(beansXml));
    }

    @Override
    public List<AstTransform> visitCompilationUnit(Tr.CompilationUnit cu) {
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
        public List<AstTransform> visitClassDecl(Tr.ClassDecl classDecl) {
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
        public List<AstTransform> visitMultiVariable(Tr.VariableDecls multiVariable) {
            Optional<PropertyValue> maybeBeanProperty = stream(beanDefinition.getPropertyValues().spliterator(), false)
                    .filter(prop -> prop.getName().equals(multiVariable.getVars().get(0).getSimpleName()))
                    .findAny();

            if (getCursor().getParentOrThrow().getParentOrThrow().getTree() instanceof Tr.ClassDecl && maybeBeanProperty.isPresent()) {
                PropertyValue beanProperty = maybeBeanProperty.get();
                if (beanProperty.getValue() instanceof BeanReference) {
                    andThen(new AddAnnotation(multiVariable.getId(), "org.springframework.beans.factory.annotation.Autowired"));
                } else if (beanProperty.getValue() instanceof TypedStringValue && multiVariable.getTypeExpr() != null) {
                    String value = ((TypedStringValue) beanProperty.getValue()).getValue();
                    if (value == null) {
                        return super.visitMultiVariable(multiVariable);
                    }

                    Type type = multiVariable.getTypeExpr().getType();
                    Type.Primitive primitive = TypeUtils.asPrimitive(type);

                    Expression valueTree;

                    if (TypeUtils.isString(type)) {
                        valueTree = new Tr.Literal(randomId(), value, "\"" + value + "\"", Type.Primitive.String, EMPTY);
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
                                return super.visitMultiVariable(multiVariable); // not reachable
                        }

                        valueTree = new Tr.Literal(randomId(), primitiveValue,
                                Type.Primitive.Char.equals(primitive) ? "'" + value + "'" : value,
                                primitive, EMPTY);
                    } else {
                        // not reachable?
                        return super.visitMultiVariable(multiVariable);
                    }

                    andThen(new AddAnnotation(multiVariable.getId(),
                            "org.springframework.beans.factory.annotation.Value", valueTree));
                }
            }

            return super.visitMultiVariable(multiVariable);
        }
    }
}
