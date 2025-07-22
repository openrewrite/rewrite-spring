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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static org.openrewrite.Tree.randomId;
import static org.openrewrite.java.spring.internal.LocalVariableUtils.resolveExpression;

@EqualsAndHashCode(callSuper = false)
@Value
public class UpdatePbkdf2PasswordEncoder extends Recipe {

    private static final String PBKDF2_PASSWORD_ENCODER_CLASS = "org.springframework.security.crypto.password.Pbkdf2PasswordEncoder";

    private static final MethodMatcher DEFAULT_CONSTRUCTOR_MATCHER = new MethodMatcher(PBKDF2_PASSWORD_ENCODER_CLASS + " <constructor>()");
    private static final MethodMatcher ONE_ARG_CONSTRUCTOR_MATCHER = new MethodMatcher(PBKDF2_PASSWORD_ENCODER_CLASS + " <constructor>(java.lang.CharSequence)");
    private static final MethodMatcher TWO_ARG_CONSTRUCTOR_MATCHER = new MethodMatcher(PBKDF2_PASSWORD_ENCODER_CLASS + " <constructor>(java.lang.CharSequence, int)");
    private static final MethodMatcher THREE_ARG_CONSTRUCTOR_MATCHER = new MethodMatcher(PBKDF2_PASSWORD_ENCODER_CLASS + " <constructor>(java.lang.CharSequence, int, int)");
    private static final MethodMatcher VERSION5_5_FACTORY_MATCHER = new MethodMatcher(PBKDF2_PASSWORD_ENCODER_CLASS + " defaultsForSpringSecurity_v5_5(..)");

    private static final String DEFAULT_SECRET = "";
    private static final Integer DEFAULT_SALT_LENGTH = 8;
    private static final Integer DEFAULT_HASH_WIDTH = 256;
    private static final Integer DEFAULT_ITERATIONS = 185000;

    private static final Map<Integer, String> HASH_WIDTH_TO_ALGORITHM_MAP;

    static {
        Map<Integer, String> map = new HashMap<>();
        map.put(160, "PBKDF2WithHmacSHA1");
        map.put(DEFAULT_HASH_WIDTH, "PBKDF2WithHmacSHA256");
        map.put(512, "PBKDF2WithHmacSHA512");
        HASH_WIDTH_TO_ALGORITHM_MAP = Collections.unmodifiableMap(map);
    }

    @Override
    public String getDisplayName() {
        return "Use new `Pbkdf2PasswordEncoder` factory methods";
    }

    @Override
    public String getDescription() {
        return "In Spring Security 5.8 some `Pbkdf2PasswordEncoder` constructors have been deprecated in favor of factory methods. " +
                "Refer to the [ Spring Security migration docs](https://docs.spring.io/spring-security/reference/5.8/migration/index.html#_update_pbkdf2passwordencoder) for more information.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PBKDF2_PASSWORD_ENCODER_CLASS, false), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J j = super.visitNewClass(newClass, ctx);
                if (j instanceof J.NewClass && TypeUtils.isOfClassType(((J.NewClass) j).getType(), PBKDF2_PASSWORD_ENCODER_CLASS)) {
                    newClass = (J.NewClass) j;
                    if (DEFAULT_CONSTRUCTOR_MATCHER.matches(newClass)) {
                        maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS);
                        return newFactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                    }
                    List<Expression> arguments = newClass.getArguments();
                    if (ONE_ARG_CONSTRUCTOR_MATCHER.matches(newClass)) {
                        Expression secret = arguments.get(0);
                        maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS);
                        if (resolvedValueMatchesLiteral(secret, DEFAULT_SECRET)) {
                            return newFactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                        }
                        String algorithm = HASH_WIDTH_TO_ALGORITHM_MAP.get(DEFAULT_HASH_WIDTH);
                        maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS + ".SecretKeyFactoryAlgorithm", algorithm);
                        return newConstructorTemplate(ctx, algorithm).apply(getCursor(), newClass.getCoordinates().replace(), secret, newIntLiteral(DEFAULT_SALT_LENGTH), newIntLiteral(DEFAULT_ITERATIONS));
                    }
                    if (TWO_ARG_CONSTRUCTOR_MATCHER.matches(newClass)) {
                        Expression secret = arguments.get(0);
                        Expression saltLength = arguments.get(1);
                        maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS);
                        if (resolvedValueMatchesLiteral(secret, DEFAULT_SECRET) &&
                                resolvedValueMatchesLiteral(saltLength, DEFAULT_SALT_LENGTH)) {
                            return newFactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                        }
                        String algorithm = HASH_WIDTH_TO_ALGORITHM_MAP.get(DEFAULT_HASH_WIDTH);
                        maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS + ".SecretKeyFactoryAlgorithm", algorithm);
                        return newConstructorTemplate(ctx, algorithm).apply(getCursor(), newClass.getCoordinates().replace(), secret, saltLength, newIntLiteral(DEFAULT_ITERATIONS));
                    }
                    if (THREE_ARG_CONSTRUCTOR_MATCHER.matches(newClass)) {
                        Expression secret = arguments.get(0);
                        Expression iterations = arguments.get(1);
                        Expression hashWidth = arguments.get(2);
                        Integer knownHashWidth = hashWidth instanceof J.Literal && hashWidth.getType() == JavaType.Primitive.Int ? (Integer) ((J.Literal) hashWidth).getValue() : null;
                        maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS);
                        if (resolvedValueMatchesLiteral(secret, DEFAULT_SECRET) &&
                                resolvedValueMatchesLiteral(iterations, DEFAULT_ITERATIONS) &&
                                DEFAULT_HASH_WIDTH.equals(knownHashWidth)) {
                            return newFactoryMethodTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace());
                        }
                        String algorithm = HASH_WIDTH_TO_ALGORITHM_MAP.get(knownHashWidth);
                        if (algorithm != null) {
                            maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS + ".SecretKeyFactoryAlgorithm", algorithm);
                            return newConstructorTemplate(ctx, algorithm).apply(getCursor(), newClass.getCoordinates().replace(), secret, newIntLiteral(DEFAULT_SALT_LENGTH), iterations);
                        }
                        return newDeprecatedConstructorTemplate(ctx).apply(getCursor(), newClass.getCoordinates().replace(), secret, newIntLiteral(DEFAULT_SALT_LENGTH), iterations, hashWidth);
                    }
                }
                return j;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J j = super.visitMethodInvocation(method, ctx);
                if (j instanceof J.MethodInvocation && VERSION5_5_FACTORY_MATCHER.matches(((J.MethodInvocation) j))) {
                    maybeAddImport(PBKDF2_PASSWORD_ENCODER_CLASS);
                    method = (J.MethodInvocation) j;
                    return newFactoryMethodTemplate(ctx).apply(getCursor(), method.getCoordinates().replace());
                }
                return j;
            }

            boolean resolvedValueMatchesLiteral(Expression expression, Object value) {
                Expression resolvedExpression = resolveExpression(expression, getCursor());
                return resolvedExpression instanceof J.Literal && Objects.equals(((J.Literal) resolvedExpression).getValue(), value);
            }

            private JavaTemplate newFactoryMethodTemplate(ExecutionContext ctx) {
                return JavaTemplate.builder("Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()")
                        .imports(PBKDF2_PASSWORD_ENCODER_CLASS)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-security-crypto-5.8.+"))
                        .build();
            }

            private JavaTemplate newConstructorTemplate(ExecutionContext ctx, String algorithm) {
                return JavaTemplate.builder("new Pbkdf2PasswordEncoder(#{any(java.lang.CharSequence)}, #{any(int)}, #{any(int)}, " + algorithm + ")")
                        .imports(PBKDF2_PASSWORD_ENCODER_CLASS)
                        .staticImports(PBKDF2_PASSWORD_ENCODER_CLASS + ".SecretKeyFactoryAlgorithm." + algorithm)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-security-crypto-5.8.+"))
                        .build();
            }

            private JavaTemplate newDeprecatedConstructorTemplate(ExecutionContext ctx) {
                return JavaTemplate.builder("new Pbkdf2PasswordEncoder(#{any(java.lang.CharSequence)}, #{any(int)}, #{any(int)}, #{any(int)})")
                        .imports(PBKDF2_PASSWORD_ENCODER_CLASS)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-security-crypto-5.8.+"))
                        .build();
            }
        });
    }

    private static J.Literal newIntLiteral(int i) {
        return new J.Literal(randomId(), Space.EMPTY, Markers.EMPTY, i, Integer.toString(i), null, JavaType.Primitive.Int);
    }
}
