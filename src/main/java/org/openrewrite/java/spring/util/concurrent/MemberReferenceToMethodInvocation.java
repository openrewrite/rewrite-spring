/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.util.concurrent;

import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

class MemberReferenceToMethodInvocation extends JavaVisitor<ExecutionContext> {
    @Override
    public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference mr = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
        if (mr.getMethodType() == null) {
            return mr;
        }

        List<Param> args = getLambdaArgNames(mr.getMethodType());
        String templateCode = String.format("(%s) -> %s.%s(%s)",
                args.stream().map(Param::toString).collect(joining(", ")),
                mr.getContaining(),
                mr.getReference().getSimpleName(),
                args.stream().map(Param::getName).collect(joining(", ")));
        return JavaTemplate.builder(templateCode)
                .contextSensitive()
                .build().apply(getCursor(), mr.getCoordinates().replace())
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
        return params;
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
}
