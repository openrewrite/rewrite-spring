/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.util;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

public class MemberReferenceToMethodInvocation extends JavaVisitor<ExecutionContext> {
    @Override
    public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference mr = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
        if (mr.getMethodType() == null) {
            return mr;
        }

        List<Param> args = getLambdaArgNames(mr.getMethodType());
        String templateCode;
        if (!mr.getMethodType().getParameterTypes().isEmpty()) {
            templateCode = String.format("(%s) -> %s.%s(%s)",
                    args.stream().map(Param::toString).collect(joining(", ")),
                    mr.getContaining(),
                    mr.getReference().getSimpleName(),
                    args.stream().map(Param::getName).collect(joining(", ")));
        } else if (mr.getContaining() instanceof J.Identifier && ("this".equals(((J.Identifier) mr.getContaining()).getSimpleName()) || "super".equals(((J.Identifier) mr.getContaining()).getSimpleName()))) {
            templateCode = String.format("() -> %s.%s()",
                    mr.getContaining(),
                    mr.getReference().getSimpleName());
        } else {
            templateCode = String.format("%1$s -> %1$s.%2$s()",
                    args.get(0).getName(),
                    mr.getReference().getSimpleName());
        }
        J.Lambda lambda = JavaTemplate.builder(templateCode)
                .contextSensitive()
                .build()
                .apply(getCursor(), mr.getCoordinates().replace());

        return new AddLambdaTypeInformation(mr, args).visitNonNull(lambda, ctx)
                .withPrefix(mr.getPrefix());
    }

    private List<Param> getLambdaArgNames(JavaType.Method methodType) {
        List<Param> params = new ArrayList<>();
        for (int i = 0; i < methodType.getParameterTypes().size(); i++) {
            JavaType type = methodType.getParameterTypes().get(i);
            String name = methodType.getParameterNames().get(i);
            if (name.startsWith("arg")) {
                String typeString = type.toString();
                name = typeString.substring(typeString.lastIndexOf('.') + 1).toLowerCase();
            }
            String uniqueVariableName = VariableNameUtils.generateVariableName(name, getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);

            params.add(new Param(type, uniqueVariableName));
        }
        if (params.isEmpty()) {
            JavaType.FullyQualified type = methodType.getDeclaringType();
            String uniqueVariableName = VariableNameUtils.generateVariableName(StringUtils.uncapitalize(keepFromLastCapitalLetter(type.getClassName())), getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);

            params.add(new Param(type, uniqueVariableName));
        }
        return params;
    }

    @RequiredArgsConstructor
    private static class AddLambdaTypeInformation extends JavaIsoVisitor<ExecutionContext> {

        private final J.MemberReference mr;
        private final List<Param> args;

        @Override
        public J.Identifier visitIdentifier(J.Identifier ident, ExecutionContext ctx) {
            Optional<Param> arg = args.stream().filter(a -> a.getName().equals(ident.getSimpleName())).findFirst();
            if (arg.isPresent()) {
                JavaType type = arg.get().getType();
                return ident.withType(type).withFieldType(ident.getFieldType() == null ? null : ident.getFieldType().withType(type));
            }
            return super.visitIdentifier(ident, ctx);
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations mv, ExecutionContext ctx) {
            return mv.withVariables(ListUtils.map(mv.getVariables(), (index, variable) -> variable.withType(args.get(index).getType())));
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            return mi.withMethodType(mr.getMethodType());
        }

    }

    @Value
    class Param {
        JavaType type;
        String name;

        @Override
        public String toString() {
            return String.format("%s %s", type.toString().replaceFirst("^java.lang.", ""), name);
        }
    }

    private static String keepFromLastCapitalLetter(String input) {
        for (int i = input.length() - 1; i >= 0; i--) {
            if (Character.isUpperCase(input.charAt(i))) {
                return input.substring(i);
            }
        }
        return input;
    }
}
