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
package org.openrewrite.java.spring.security5;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Objects;

import static org.openrewrite.java.spring.internal.LocalVariableUtils.resolveExpression;

@Value
@EqualsAndHashCode(callSuper = false)
public class UpdateSCryptPasswordEncoder extends Recipe {

    private static final String SCRYPT_PASSWORD_ENCODER_CLASS = "org.springframework.security.crypto.scrypt.SCryptPasswordEncoder";

    private static final MethodMatcher DEFAULT_CONSTRUCTOR_MATCHER = new MethodMatcher(SCRYPT_PASSWORD_ENCODER_CLASS + " <constructor>()");
    private static final MethodMatcher FULL_CONSTRUCTOR_MATCHER = new MethodMatcher(SCRYPT_PASSWORD_ENCODER_CLASS + " <constructor>(int, int, int, int, int)");

    private static final Integer DEFAULT_CPU_COST = 65_536;
    private static final Integer DEFAULT_MEMORY_COST = 8;
    private static final Integer DEFAULT_PARALLELIZATION = 1;
    private static final Integer DEFAULT_KEY_LENGTH = 32;
    private static final Integer DEFAULT_SALT_LENGTH = 16;

    private static final Integer DEFAULT_V41_CPU_COST = 16_384;
    private static final Integer DEFAULT_V41_MEMORY_COST = 8;
    private static final Integer DEFAULT_V41_PARALLELIZATION = 1;
    private static final Integer DEFAULT_V41_KEY_LENGTH = 32;
    private static final Integer DEFAULT_V41_SALT_LENGTH = 64;

    @Override
    public String getDisplayName() {
        return "Use new `SCryptPasswordEncoder` factory methods";
    }

    @Override
    public String getDescription() {
        return "In Spring Security 5.8 some `SCryptPasswordEncoder` constructors have been deprecated in favor of factory methods. " +
                "Refer to the [ Spring Security migration docs](https://docs.spring.io/spring-security/reference/5.8/migration/index.html#_update_scryptpasswordencoder) for more information.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(SCRYPT_PASSWORD_ENCODER_CLASS, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J j = super.visitNewClass(newClass, ctx);
                if (j instanceof J.NewClass && TypeUtils.isOfClassType(((J.NewClass) j).getType(), SCRYPT_PASSWORD_ENCODER_CLASS)) {
                    newClass = (J.NewClass) j;
                    if (DEFAULT_CONSTRUCTOR_MATCHER.matches(newClass)) {
                        maybeAddImport(SCRYPT_PASSWORD_ENCODER_CLASS);
                        return newV41FactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                    } else {
                        List<Expression> arguments = newClass.getArguments();
                        if (FULL_CONSTRUCTOR_MATCHER.matches(newClass)) {
                            Expression cpuCost = arguments.get(0);
                            Expression memoryCost = arguments.get(1);
                            Expression parallelization = arguments.get(2);
                            Expression keyLength = arguments.get(3);
                            Expression saltLength = arguments.get(4);
                            maybeAddImport(SCRYPT_PASSWORD_ENCODER_CLASS);
                            if (resolvedValueMatchesLiteral(cpuCost, DEFAULT_CPU_COST) &&
                                    resolvedValueMatchesLiteral(memoryCost, DEFAULT_MEMORY_COST) &&
                                    resolvedValueMatchesLiteral(parallelization, DEFAULT_PARALLELIZATION) &&
                                    resolvedValueMatchesLiteral(keyLength, DEFAULT_KEY_LENGTH) &&
                                    resolvedValueMatchesLiteral(saltLength, DEFAULT_SALT_LENGTH)) {
                                return newV58FactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                            } else if (resolvedValueMatchesLiteral(cpuCost, DEFAULT_V41_CPU_COST) &&
                                    resolvedValueMatchesLiteral(memoryCost, DEFAULT_V41_MEMORY_COST) &&
                                    resolvedValueMatchesLiteral(parallelization, DEFAULT_V41_PARALLELIZATION) &&
                                    resolvedValueMatchesLiteral(keyLength, DEFAULT_V41_KEY_LENGTH) &&
                                    resolvedValueMatchesLiteral(saltLength, DEFAULT_V41_SALT_LENGTH)) {
                                return newV41FactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                            }
                        }
                    }
                }
                return j;
            }

            boolean resolvedValueMatchesLiteral(Expression expression, Object value) {
                Expression resolvedExpression = resolveExpression(expression, getCursor());
                return resolvedExpression instanceof J.Literal && Objects.equals(((J.Literal) resolvedExpression).getValue(), value);
            }

            private JavaTemplate newV41FactoryMethodTemplate(ExecutionContext ctx) {
                return JavaTemplate.builder("SCryptPasswordEncoder.defaultsForSpringSecurity_v4_1()")
                        .imports(SCRYPT_PASSWORD_ENCODER_CLASS)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-security-crypto-5.8.+"))
                        .build();
            }

            private JavaTemplate newV58FactoryMethodTemplate(ExecutionContext ctx) {
                return JavaTemplate.builder("SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8()")
                        .imports(SCRYPT_PASSWORD_ENCODER_CLASS)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-security-crypto-5.8.+"))
                        .build();
            }
        });
    }
}
