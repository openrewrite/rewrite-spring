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
package org.openrewrite.spring.boot2;

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.MultiSourceVisitor;
import org.openrewrite.RefactorVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AddAnnotation;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class ValueToConfigurationProperties extends JavaRefactorVisitor implements MultiSourceVisitor {
    private final List<RefactorVisitor<J>> renameValueFields = new ArrayList<>();

    @Nullable
    private String commonPrefix;

    public ValueToConfigurationProperties() {
        setCursoringOn();
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        andThen().addAll(renameValueFields);
        return super.visitCompilationUnit(cu);
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        if (commonPrefix != null) {
            andThen(new AddAnnotation.Scoped(c, "org.springframework.boot.context.properties.ConfigurationProperties",
                    new J.Literal(randomId(), commonPrefix, '"' + commonPrefix + '"', JavaType.Primitive.String, Formatting.EMPTY)));
        }

        return c;
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {
        J.VariableDecls v = refactor(multiVariable, super::visitMultiVariable);

        List<J.Annotation> value = v.findAnnotations("@org.springframework.beans.factory.annotation.Value");
        if (!value.isEmpty()) {
            String valueFormatPrefix = value.get(0).getPrefix();

            List<J.Annotation> annotations = new ArrayList<>(v.getAnnotations());
            annotations.removeAll(value);

            maybeRemoveImport("org.springframework.beans.factory.annotation.Value");

            if (v.getAnnotations().get(0) == value.get(0)) {
                annotations = formatFirstPrefix(annotations, valueFormatPrefix);
            }
            v = v.withAnnotations(annotations);

            if (annotations.isEmpty()) {
                if (!v.getModifiers().isEmpty()) {
                    v = v.withModifiers(formatFirstPrefix(v.getModifiers(), valueFormatPrefix));
                } else if (v.getTypeExpr() != null) {
                    v = v.withTypeExpr(v.getTypeExpr().withPrefix(valueFormatPrefix));
                }
            }

            String valueValue = (String) ((J.Literal) value.get(0).getArgs().getArgs().get(0)).getValue();
            valueValue = valueValue.replace("${", "")
                    .replace("}", "");
            valueValue = Arrays.stream(valueValue.split("-"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(""));
            valueValue = Character.toLowerCase(valueValue.charAt(0)) + valueValue.substring(1);

            int lastDot = valueValue.lastIndexOf('.');

            commonPrefix = longestCommonPrefix(commonPrefix, lastDot == -1 ? valueValue : valueValue.substring(0, lastDot));

            String expectedFieldName = valueValue.substring(lastDot + 1);
            String existingFieldName = v.getVars().get(0).getSimpleName();
            if (!existingFieldName.equals(expectedFieldName)) {
                J.ClassDecl enclosingClass = getCursor().firstEnclosing(J.ClassDecl.class);
                assert enclosingClass != null;
                JavaType.Class classType = TypeUtils.asClass(enclosingClass.getType());
                assert classType != null;

                ChangeFieldName.Scoped renameValueFields = new ChangeFieldName.Scoped(classType,
                        existingFieldName, expectedFieldName);

                String expectedFieldNameCapitalized = Character.toUpperCase(expectedFieldName.charAt(0)) +
                        expectedFieldName.substring(1);
                String existingFieldNameCapitalized = Character.toUpperCase(existingFieldName.charAt(0)) +
                        existingFieldName.substring(1);

                ChangeMethodName renameGetter = new ChangeMethodName();
                renameGetter.setMethod(classType.getFullyQualifiedName() + " get" + existingFieldNameCapitalized + "()");
                renameGetter.setName("get" + expectedFieldNameCapitalized);

                ChangeMethodName renameSetter = new ChangeMethodName();
                renameSetter.setMethod(classType.getFullyQualifiedName() + " set" + existingFieldNameCapitalized + "(..)");
                renameSetter.setName("set" + expectedFieldNameCapitalized);

                andThen(renameValueFields);

                this.renameValueFields.add(renameValueFields);
                this.renameValueFields.add(renameGetter);
                this.renameValueFields.add(renameSetter);
            }
        }

        return v;
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
