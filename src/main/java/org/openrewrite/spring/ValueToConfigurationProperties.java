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
package org.openrewrite.spring;

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.MultiSourceVisitor;
import org.openrewrite.RefactorVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.AddField;
import org.openrewrite.java.AutoFormat;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.DeleteStatement;
import org.openrewrite.java.GenerateGetter;
import org.openrewrite.java.GenerateSetter;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TreeBuilder;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.internal.StringUtils.capitalize;
import static org.openrewrite.internal.StringUtils.uncapitalize;

@AutoConfigure
public class ValueToConfigurationProperties extends JavaRefactorVisitor implements MultiSourceVisitor {
    private final List<RefactorVisitor<J>> multiSourceVisitors = new ArrayList<>();

    private static final String valueAnnotationSignature = "@org.springframework.beans.factory.annotation.Value";

    public ValueToConfigurationProperties() {
        setCursoringOn();
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        andThen().addAll(multiSourceVisitors);
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);

        List<J.VariableDecls> valueAnnotatedFields = classDecl.getFields().stream()
                .filter(it -> it.findAnnotations(valueAnnotationSignature).size() > 0)
                .collect(Collectors.toList());

        String commonPrefix = valueAnnotatedFields.stream()
                .flatMap(it -> it.findAnnotations(valueAnnotationSignature).stream())
                .map(ValueToConfigurationProperties::getValueValue)
                .reduce(ValueToConfigurationProperties::longestCommonPrefix)
                .orElse(null);

        // The strange looking check for an enclosing class declaration is to avoid slapping this annotation onto inner classes
        assert getCursor().getParent() != null;
        if(commonPrefix != null && getCursor().getParent().firstEnclosing(J.ClassDecl.class) == null) {
            andThen(new AddAnnotation.Scoped(cd, "org.springframework.boot.context.properties.ConfigurationProperties",
                    new J.Literal(randomId(), commonPrefix, '"' + commonPrefix + '"', JavaType.Primitive.String, Formatting.EMPTY)));
        }

//        valueAnnotatedFields.forEach(field -> andThen(new ProcessMultiVariable(commonPrefix, field)));

        return cd;
    }

    /**
     * Extracts, de-dashes, and camelCases the value string from a @Value annotation
     * Given:   @Value("${app.screen.refresh-rate}")
     * Returns: app.screen.refreshRate
     */
    private static String getValueValue(J.Annotation value) {
        String valueValue = (String) ((J.Literal) value.getArgs().getArgs().get(0)).getValue();
        assert valueValue != null;
        valueValue = valueValue.replace("${", "")
                .replace("}", "");
        valueValue = Arrays.stream(valueValue.split("-"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(""));
        return Character.toLowerCase(valueValue.charAt(0)) + valueValue.substring(1);
    }

    private class ProcessMultiVariable extends JavaRefactorVisitor {
        private final String commonPrefix;
        private final J.VariableDecls scope;

        public ProcessMultiVariable(String commonPrefix, J.VariableDecls scope) {
            this.commonPrefix = commonPrefix;
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public J visitMultiVariable(J.VariableDecls multiVariable) {
            J.VariableDecls v = refactor(multiVariable, super::visitMultiVariable);
            if(scope.isScope(multiVariable)) {
                List<J.Annotation> valueAnnotations = v.findAnnotations("@org.springframework.beans.factory.annotation.Value");
                J.Annotation valueAnnotation = valueAnnotations.get(0);

                List<J.Annotation> annotations = new ArrayList<>(v.getAnnotations());
                annotations.removeAll(valueAnnotations);

                // If the @Value annotation we're removing is the first or only annotation then do a bit of extra work
                // with the whitespace to alter the resulting formatting as little as possible
                maybeRemoveImport("org.springframework.beans.factory.annotation.Value");
                String valueAnnotationWhitespace = valueAnnotation.getPrefix();
                if (v.getAnnotations().get(0) == valueAnnotation) {
                    annotations = formatFirstPrefix(annotations, valueAnnotationWhitespace);
                }
                v = v.withAnnotations(annotations);

                if (annotations.isEmpty()) {
                    if (!v.getModifiers().isEmpty()) {
                        v = v.withModifiers(formatFirstPrefix(v.getModifiers(), valueAnnotationWhitespace));
                    } else if (v.getTypeExpr() != null) {
                        v = v.withTypeExpr(v.getTypeExpr().withPrefix(valueAnnotationWhitespace));
                    }
                }

                String valueValue = getValueValue(valueAnnotation);
                String withoutCommonPrefix = valueValue.replaceAll(Pattern.quote(commonPrefix + "."), "");

                int dotIndex = withoutCommonPrefix.indexOf('.');
                J.ClassDecl enclosingClass = getCursor().firstEnclosing(J.ClassDecl.class);
                if(dotIndex == -1) {
                    // If the removal of the common prefix means no '.'-delineated segments remain, proceed to directly process the field
                    String existingFieldName = v.getVars().get(0).getSimpleName();
                    assert enclosingClass != null;
                    if (!existingFieldName.equals(withoutCommonPrefix)) {
                        JavaType.Class classType = TypeUtils.asClass(enclosingClass.getType());
                        assert classType != null;

                        ChangeFieldName.Scoped renameValueFields = new ChangeFieldName.Scoped(classType,
                                existingFieldName, withoutCommonPrefix);

                        String expectedFieldNameCapitalized = Character.toUpperCase(withoutCommonPrefix.charAt(0)) +
                                withoutCommonPrefix.substring(1);
                        String existingFieldNameCapitalized = Character.toUpperCase(existingFieldName.charAt(0)) +
                                existingFieldName.substring(1);

                        ChangeMethodName renameGetter = new ChangeMethodName();
                        renameGetter.setMethod(classType.getFullyQualifiedName() + " get" + existingFieldNameCapitalized + "()");
                        renameGetter.setName("get" + expectedFieldNameCapitalized);

                        ChangeMethodName renameSetter = new ChangeMethodName();
                        renameSetter.setMethod(classType.getFullyQualifiedName() + " set" + existingFieldNameCapitalized + "(..)");
                        renameSetter.setName("set" + expectedFieldNameCapitalized);

                        multiSourceVisitors.add(renameValueFields);
                        multiSourceVisitors.add(renameGetter);
                        multiSourceVisitors.add(renameSetter);
                    }
                } else {
                    // If there are still "." delineated segments, it's time to generate inner classes
                    String newClassName = capitalize(withoutCommonPrefix.substring(0, withoutCommonPrefix.indexOf('.')));

                    // TODO: Remove existing getters/setters
                    // TODO: Replace existing references to defunct field/getter/setter with references to their successors across all sources
                    // TODO: Recursively handle stuff that's still not sufficiently un-nested

                    andThen(new DeleteStatement(v));
                    andThen(new CreateOrUpdateInnerClass(enclosingClass, v, newClassName));
                }
            }
            return v;
        }
    }

    /**
     * Create an inner class within the enclosingClass with the specified name.
     * Adds a private field based on the originalField with getter and setter methods.
     * Generated inner classes are always public and static
     */
    private static class CreateOrUpdateInnerClass extends JavaRefactorVisitor {
        final J.ClassDecl enclosingClass;
        final J.VariableDecls originalField;
        final String className;

        public CreateOrUpdateInnerClass(J.ClassDecl enclosingClass, J.VariableDecls field, String name) {
            this.enclosingClass = enclosingClass;
            this.originalField = field;
            this.className = name;
            setCursoringOn();
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl c = refactor(classDecl, super::visitClassDecl);
            if(enclosingClass.isScope(c)) {
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                assert cu != null;
                JavaParser jp = JavaParser.fromJavaVersion()
                        .styles(cu.getStyles())
                        .build();

                // Look through the body to see if the class we're about to create has already been defined
                assert originalField.getTypeExpr() != null;
                J.ClassDecl innerClassDecl = c.getBody().getStatements().stream()
                        .filter(it -> it instanceof J.ClassDecl)
                        .map(it -> (J.ClassDecl) it)
                        .filter(it -> it.getSimpleName().equals(className))
                        .findAny()
                        .orElse(
                                TreeBuilder.buildInnerClassDeclaration(jp,
                                        enclosingClass,
                                        "public static class " + className + " {\n}\n"));

                J.VariableDecls fieldDeclaration = TreeBuilder.buildFieldDeclaration(jp,
                        innerClassDecl,
                        "private " + originalField.getTypeExpr().print().trim() + " " +  originalField.getVars().get(0).getSimpleName() + ";\n",
                        originalField.getTypeAsClass());
                List<J> innerStatements = innerClassDecl.getBody().getStatements();
                innerStatements.add(fieldDeclaration);
                innerClassDecl = innerClassDecl.withBody(innerClassDecl.getBody().withStatements(innerStatements));
                andThen(new GenerateGetter.Scoped(innerClassDecl, fieldDeclaration));
                andThen(new GenerateSetter.Scoped(innerClassDecl, fieldDeclaration));
                andThen(new AutoFormat(innerClassDecl));
                String fieldName = uncapitalize(className);
                assert innerClassDecl.getType() != null;
                andThen(new AddField.Scoped(enclosingClass,
                        Collections.singletonList(new J.Modifier.Private(randomId(), EMPTY)),
                        innerClassDecl.getType().getFullyQualifiedName(),
                        fieldName,
                        null));
                andThen(new RemoveGettersAndSetters(originalField, enclosingClass));
                andThen(new GenerateGettersAndSetters(enclosingClass, fieldName));

                // Filter any existing declaration of the inner class so we don't end up with multiple declarations
                List<J> statements = c.getBody().getStatements().stream()
                        .filter(it -> !(it instanceof J.ClassDecl && ((J.ClassDecl) it).getSimpleName().equals(className)))
                        .collect(Collectors.toList());
                statements.add(innerClassDecl);

                c = c.withBody(c.getBody().withStatements(statements));
            }
            return c;
        }
    }

    private static class GenerateGettersAndSetters extends JavaRefactorVisitor {
        final J.ClassDecl enclosingClass;
        final String fieldName;

        private GenerateGettersAndSetters(J.ClassDecl enclosingClass, String fieldName) {
            this.enclosingClass = enclosingClass;
            this.fieldName = fieldName;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);
            if(enclosingClass.isScope(cd)) {
                cd.getFields().stream()
                        .filter(it -> it.getVars().get(0).getSimpleName().equals(fieldName))
                        .findAny()
                        .ifPresent(field -> {
                            andThen(new GenerateGetter.Scoped(cd, field));
                            andThen(new GenerateSetter.Scoped(cd, field));
                        });
            }
            return cd;
        }
    }

    /**
     * Remove getters and setters named after the specified field from the provided class.
     * Does not do anything to any references to those fields that may exist.
     */
    private static class RemoveGettersAndSetters extends JavaRefactorVisitor {
        final J.VariableDecls field;
        final J.ClassDecl scope;

        private RemoveGettersAndSetters(J.VariableDecls field, J.ClassDecl scope) {
            this.field = field;
            this.scope = scope;
        }

        @Override
        public J visitClassDecl(J.ClassDecl classDecl) {
            J.ClassDecl cd = refactor(classDecl, super::visitClassDecl);
            if(scope.isScope(classDecl)) {

                assert cd.getType() != null;
                String classFullyQualifiedType = cd.getType().getFullyQualifiedName();
                assert field.getVars().get(0).getType() != null;
                assert field.getTypeExpr() != null;
                String fieldType = field.getTypeExpr().printTrimmed();
                String fieldName = field.getVars().get(0).getSimpleName();
                MethodMatcher getterMatcher = new MethodMatcher(classFullyQualifiedType + " get" + capitalize(fieldName) + "()");
                MethodMatcher setterMatcher = new MethodMatcher(
                        classFullyQualifiedType + " set" + capitalize(fieldName) + "(" + fieldType + ")");

                final J.ClassDecl finalCd = cd;
                List<J> filteredStatements = cd.getBody().getStatements().stream()
                        .filter(it -> {
                            if(it instanceof J.MethodDecl) {
                                return !(getterMatcher.matches((J.MethodDecl)it, finalCd) || setterMatcher.matches((J.MethodDecl)it, finalCd));
                            } else {
                                return true;
                        }}).collect(Collectors.toList());

                cd = cd.withBody(cd.getBody().withStatements(filteredStatements));
            }
            return cd;
        }
    }

    // VisibleForTesting
    static String longestCommonPrefix(@Nullable String s1, String s2) {
        if (s1 == null || s1.equals(s2)) {
            return s2;
        }

        String[] s1Parts = s1.split("\\.");
        String[] s2Parts = s2.split("\\.");

        StringBuilder commonPrefix = new StringBuilder();
        for (int i = 0; i < Math.min(s1Parts.length, s2Parts.length); i++) {
            if (!s1Parts[i].equals(s2Parts[i])) {
                return commonPrefix.toString();
            }
            if (i > 0) {
                commonPrefix.append('.');
            }
            commonPrefix.append(s1Parts[i]);
        }

        return commonPrefix.toString();
    }
}
