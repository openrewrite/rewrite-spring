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

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

class MemberReferenceToMethodInvocation extends JavaVisitor<ExecutionContext> {
    @Override
    public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference mr = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
        if (mr.getMethodType() == null) {
            return mr;
        }

        List<String> args = getLambdaArgNames(mr.getMethodType());
        String commaSeparatedArgs = String.join(", ", args);
        String templateCode = String.format("%s -> %s.%s(%s)",
                args.isEmpty() ? "()" : args.size() == 1 ? args.get(0) : String.format("(%s)", commaSeparatedArgs),
                mr.getContaining(),
                mr.getReference().getSimpleName(),
                args.isEmpty() ? "" : commaSeparatedArgs);
        return JavaTemplate.builder(templateCode)
                .contextSensitive()
                .build().apply(getCursor(), mr.getCoordinates().replace())
                .withPrefix(mr.getPrefix());
    }

    private List<String> getLambdaArgNames(JavaType.Method methodType) {
        List<String> parameterNames = methodType.getParameterNames();
        if (parameterNames.isEmpty()) {
            return Collections.emptyList();
        }
        if (!"arg0".equals(parameterNames.get(0))) {
            return parameterNames.stream()
                    .map(this::getUniqueVariableName)
                    .collect(toList());
        }
        return methodType.getParameterTypes().stream()
                .map(JavaType::toString)
                .map(type -> type.substring(type.lastIndexOf('.') + 1).toLowerCase())
                .map(this::getUniqueVariableName)
                .collect(Collectors.toList());
    }

    private String getUniqueVariableName(String name) {
        return VariableNameUtils.generateVariableName(name, getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
    }
}
