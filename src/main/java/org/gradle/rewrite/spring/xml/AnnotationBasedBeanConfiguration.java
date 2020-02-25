package org.gradle.rewrite.spring.xml;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.TreeBuilder;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.TypeUtils;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.visitor.refactor.ScopedRefactorVisitor;
import com.netflix.rewrite.visitor.refactor.op.AddAnnotation;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.singletonList;
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
                    andThen(new AddAnnotation(classDecl.getId(), "org.springframework.context.annotation.Scope"));
                    andThen(new SetScopeAnnotationToPrototype(classDecl.getId()));
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
            if (getCursor().getParentOrThrow().getParentOrThrow().getTree() instanceof Tr.ClassDecl &&
                    stream(beanDefinition.getPropertyValues().spliterator(), false)
                            .anyMatch(prop -> prop.getName().equals(multiVariable.getVars().get(0).getSimpleName()))) {
                andThen(new AddAnnotation(multiVariable.getId(), "org.springframework.beans.factory.annotation.Autowired"));
            }

            return super.visitMultiVariable(multiVariable);
        }
    }

    private static class SetScopeAnnotationToPrototype extends ScopedRefactorVisitor {
        public SetScopeAnnotationToPrototype(UUID scope) {
            super(scope);
        }

        @Override
        public List<AstTransform> visitAnnotation(Tr.Annotation annotation) {
            return maybeTransform(annotation,
                    isScope(getCursor().getParentOrThrow().getTree()) &&
                            TypeUtils.isOfClassType(annotation.getType(), "org.springframework.context.annotation.Scope"),
                    super::visitAnnotation,
                    ann -> {
                        Type.Class cbf = Type.Class.build("org.springframework.beans.factory.config.ConfigurableBeanFactory");
                        maybeAddImport(cbf.getFullyQualifiedName());
                        return ann.withArgs(
                                new Tr.Annotation.Arguments(randomId(),
                                        singletonList(TreeBuilder.buildName("ConfigurableBeanFactory.SCOPE_PROTOTYPE")
                                                .withType(cbf)),
                                        EMPTY)
                        );
                    }
            );
        }
    }
}
