/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.data;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.ShortenFullyQualifiedTypeReferences;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.openrewrite.java.spring.internal.MethodDeclarations.withReturnTypeExpression;

public class MigrateCrudRepositoryMethods extends Recipe {

    private static final String CRUD_REPOSITORY = "org.springframework.data.repository.CrudRepository";
    private static final String FIND_BY_ID = "findById";

    private static final MethodMatcher OPTIONAL_OF_NULLABLE = new MethodMatcher("java.util.Optional ofNullable(..)");

    /**
     * Take the call being stubbed as their argument, so it has to stay the call that returns an `Optional`.
     */
    private static final List<MethodMatcher> STUBBINGS = Arrays.asList(
            new MethodMatcher("org.mockito.Mockito when(..)"),
            new MethodMatcher("org.mockito.BDDMockito given(..)"));

    /**
     * Return the mock itself, so the call made on what they return is a stubbing or a verification and not a value.
     */
    private static final List<MethodMatcher> MOCK_RECEIVERS = Arrays.asList(
            new MethodMatcher("org.mockito.Mockito verify(..)"),
            new MethodMatcher("org.mockito.InOrder verify(..)"),
            new MethodMatcher("org.mockito.BDDMockito.Then should(..)"),
            new MethodMatcher("org.mockito.stubbing.Stubber when(..)"),
            new MethodMatcher("org.mockito.BDDMockito.BDDStubber given(..)"));

    /**
     * Follow the stubbing, as in `when(mock.call()).thenReturn(value)`.
     */
    private static final List<MethodMatcher> ANSWERS_AFTER = Arrays.asList(
            new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)"),
            new MethodMatcher("org.mockito.BDDMockito.BDDMyOngoingStubbing willReturn(..)"));

    /**
     * Come before the stubbing, as in `doReturn(value).when(mock).call()`.
     */
    private static final List<MethodMatcher> ANSWERS_BEFORE = Arrays.asList(
            new MethodMatcher("org.mockito.Mockito doReturn(..)"),
            new MethodMatcher("org.mockito.stubbing.Stubber doReturn(..)"),
            new MethodMatcher("org.mockito.BDDMockito willReturn(..)"),
            new MethodMatcher("org.mockito.BDDMockito.BDDStubber willReturn(..)"));

    @Getter
    final String displayName = "Migrate `CrudRepository` methods renamed in Spring Data 2.0";

    @Getter
    final String description = "Spring Data 2.0 renamed the `CrudRepository` methods that took an id or an `Iterable`: " +
            "`findOne(id)` to `findById(id)`, which returns an `Optional`, `exists(id)` to `existsById(id)`, " +
            "`delete(id)` to `deleteById(id)`, `delete(Iterable)` to `deleteAll(Iterable)`, " +
            "`save(Iterable)` to `saveAll(Iterable)` and `findAll(Iterable)` to `findAllById(Iterable)`. " +
            "A call to `findOne(id)` becomes `findById(id).orElse(null)` so that it still gives `null` for a missing " +
            "entity. Mockito stubbings and verifications of `findOne(id)` move to `findById(id)`, and the values they " +
            "return become `Optional`s. Repositories that redeclare these methods are changed along with their callers, " +
            "since Spring Data 2.0 would otherwise read a leftover `findOne(id)` as a query method. A repository's own " +
            "`T findById(ID)`, which no longer compiles on Spring Data 2.0, becomes `Optional<T> findById(ID)` with its " +
            "callers. Calls to `delete(entity)` and `save(entity)` keep their names. Overrides of `findOne(id)` that " +
            "have a body, repositories with another `findById` that gives no `Optional`, and `repository::findOne` " +
            "method references are left for the compiler to point out. " +
            "Only code compiled against Spring Data 1.x is changed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CRUD_REPOSITORY, true), new JavaVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                // Spock's `repository.findOne(1) >> user` stubs through syntax this recipe does not read
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                boolean answersFindById = answersFindById(method);
                boolean unwrapsFindById = OPTIONAL_OF_NULLABLE.matches(method) && becomesFindById(method.getArguments().get(0));
                String newName = newName(method.getMethodType(), receiver(method));
                boolean keepsOptional = FIND_BY_ID.equals(newName) && isUsedAsOptional(method);

                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (unwrapsFindById) {
                    maybeRemoveImport("java.util.Optional");
                    return m.getArguments().get(0).withPrefix(m.getPrefix());
                }
                if (answersFindById) {
                    maybeAddImport("java.util.Optional");
                    return m.withArguments(ListUtils.map(m.getArguments(), this::toOptional));
                }
                if (newName == null) {
                    return m;
                }
                JavaType.Method renamed = m.getMethodType().withName(newName);
                if (!FIND_BY_ID.equals(newName)) {
                    return withMethodType(m, renamed);
                }
                JavaType value = renamed.getReturnType();
                JavaType.Parameterized optional = new JavaType.Parameterized(null,
                        JavaType.ShallowClass.build("java.util.Optional"), singletonList(value));
                J.MethodInvocation findById = withMethodType(m, renamed.withReturnType(optional));
                return keepsOptional ? findById : orElseNull(findById, optional, value);
            }

            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                JavaType.Method type = md.getMethodType();
                String newName = type == null ? null : newName(type, type.getDeclaringType());
                if (newName == null) {
                    return md;
                }
                if (FIND_BY_ID.equals(newName)) {
                    TypeTree returnType = md.getReturnTypeExpression();
                    if (returnType == null || returnType.getType() == null) {
                        return md;
                    }
                    // `java.util.Optional<T>` around the declared type, which the parser makes a parameterized type of
                    md = withReturnTypeExpression(md, new J.ParameterizedType(Tree.randomId(), returnType.getPrefix(), Markers.EMPTY,
                            TypeTree.build("java.util.Optional"),
                            JContainer.build(singletonList(JRightPadded.build(returnType.withPrefix(Space.EMPTY)))),
                            new JavaType.Parameterized(null, JavaType.ShallowClass.build("java.util.Optional"),
                                    singletonList(returnType.getType()))));
                    doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(md));
                    maybeAddImport("java.util.Optional");
                }
                JavaType.Method renamed = md.getMethodType().withName(newName);
                return md.withName(md.getName().withSimpleName(newName).withType(renamed)).withMethodType(renamed);
            }

            @Override
            public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference mr = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
                JavaType.Method type = mr.getMethodType();
                JavaType receiver = mr.getContaining().getType();
                JavaType.Parameterized crudRepository = asCrudRepository(receiver);
                if (type == null || crudRepository == null) {
                    return mr;
                }
                // Unlike the type of a call, the type of a method reference keeps the type variables `CrudRepository` declares
                String newName = newName(type.withParameterTypes(ListUtils.map(type.getParameterTypes(),
                        parameter -> substitute(parameter, crudRepository))), receiver);
                // `repository::findOne` can not stay a method reference, since it has to take the value out of the `Optional`
                if (newName == null || FIND_BY_ID.equals(newName)) {
                    return mr;
                }
                JavaType.Method renamed = type.withName(newName);
                return mr.withReference(mr.getReference().withSimpleName(newName).withType(renamed)).withMethodType(renamed);
            }

            private J.MethodInvocation withMethodType(J.MethodInvocation m, JavaType.Method type) {
                return m.withName(m.getName().withSimpleName(type.getName()).withType(type)).withMethodType(type);
            }

            private J.MethodInvocation orElseNull(J.MethodInvocation findById, JavaType.Parameterized optional, JavaType value) {
                JavaType.Method orElse = new JavaType.Method(null, Flag.flagsToBitMap(singleton(Flag.Public)), optional,
                        "orElse", value, singletonList("other"), singletonList(value),
                        emptyList(), emptyList(), emptyList(), emptyList());
                Expression nullLiteral = new J.Literal(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        null, "null", null, JavaType.Primitive.Null);
                return new J.MethodInvocation(Tree.randomId(), findById.getPrefix(), Markers.EMPTY,
                        JRightPadded.build((Expression) findById.withPrefix(Space.EMPTY)), null,
                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), "orElse", orElse, null),
                        JContainer.build(singletonList(JRightPadded.build(nullLiteral))), orElse);
            }

            private Expression toOptional(Expression value) {
                Cursor scope = new Cursor(getCursor(), value);
                if (value.getType() == JavaType.Primitive.Null) {
                    return JavaTemplate.builder("Optional.empty()")
                            .imports("java.util.Optional")
                            .build()
                            .apply(scope, value.getCoordinates().replace());
                }
                return JavaTemplate.builder("Optional.ofNullable(#{any()})")
                        .imports("java.util.Optional")
                        .build()
                        .apply(scope, value.getCoordinates().replace(), value);
            }

            /**
             * @return `true` for `Optional.ofNullable(call)`, `when(call)`, `verify(mock).call()` and the like, where
             * `call` is the method being migrated and what surrounds it wants the `Optional` rather than its value.
             */
            private boolean isUsedAsOptional(J.MethodInvocation call) {
                if (call.getSelect() instanceof J.MethodInvocation &&
                        matchesAny(MOCK_RECEIVERS, (J.MethodInvocation) call.getSelect())) {
                    return true;
                }
                Object parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.MethodInvocation) {
                    J.MethodInvocation enclosing = (J.MethodInvocation) parent;
                    return enclosing.getArguments().get(0) == call &&
                            (OPTIONAL_OF_NULLABLE.matches(enclosing) || matchesAny(STUBBINGS, enclosing));
                }
                return false;
            }

            private boolean answersFindById(J.MethodInvocation method) {
                if (matchesAny(ANSWERS_AFTER, method)) {
                    for (Expression select = method.getSelect(); select instanceof J.MethodInvocation;
                         select = ((J.MethodInvocation) select).getSelect()) {
                        J.MethodInvocation link = (J.MethodInvocation) select;
                        if (matchesAny(STUBBINGS, link)) {
                            return becomesFindById(link.getArguments().get(0));
                        }
                    }
                } else if (matchesAny(ANSWERS_BEFORE, method)) {
                    J link = method;
                    for (Cursor c = getCursor().getParentTreeCursor(); c.getValue() instanceof J.MethodInvocation;
                         c = c.getParentTreeCursor()) {
                        J.MethodInvocation next = c.getValue();
                        if (next.getSelect() != link) {
                            return false;
                        }
                        if (becomesFindById(next)) {
                            return true;
                        }
                        link = next;
                    }
                }
                return false;
            }

            private boolean becomesFindById(Expression expression) {
                return expression instanceof J.MethodInvocation &&
                        FIND_BY_ID.equals(newName(((J.MethodInvocation) expression).getMethodType(),
                                receiver((J.MethodInvocation) expression)));
            }

            private boolean matchesAny(List<MethodMatcher> matchers, J.MethodInvocation method) {
                for (MethodMatcher matcher : matchers) {
                    if (matcher.matches(method)) {
                        return true;
                    }
                }
                return false;
            }

            private @Nullable JavaType receiver(J.MethodInvocation method) {
                if (method.getSelect() != null) {
                    return method.getSelect().getType();
                }
                Iterator<Object> classes = getCursor().getPath(J.ClassDeclaration.class::isInstance);
                while (classes.hasNext()) {
                    JavaType.FullyQualified type = ((J.ClassDeclaration) classes.next()).getType();
                    if (TypeUtils.isAssignableTo(CRUD_REPOSITORY, type)) {
                        return type;
                    }
                }
                return null;
            }
        });
    }

    /**
     * @param method   A method called on, or declared by, `receiver`.
     * @param receiver The type whose `CrudRepository` type arguments say which overload the method is.
     * @return What Spring Data 2.0 calls this method, or `null` when it is not one of those Spring Data 2.0 renamed.
     */
    private static @Nullable String newName(JavaType.@Nullable Method method, @Nullable JavaType receiver) {
        if (method == null || method.getParameterTypes().size() != 1 || !declaresFindOne(method.getDeclaringType())) {
            return null;
        }
        JavaType parameter = method.getParameterTypes().get(0);
        boolean iterable = TypeUtils.isOfClassType(parameter, "java.lang.Iterable");
        switch (method.getName()) {
            case "findOne":
                return isId(parameter, receiver) && !isImplementation(method) && !hasOtherFindById(receiver, receiver) ?
                        FIND_BY_ID : null;
            case FIND_BY_ID:
                return isEntityFindById(method, receiver) && !isImplementation(method) && !hasOtherFindById(receiver, receiver) ?
                        FIND_BY_ID : null;
            case "exists":
                return isId(parameter, receiver) ? "existsById" : null;
            case "delete":
                return iterable ? "deleteAll" : isId(parameter, receiver) ? "deleteById" : null;
            case "save":
                return iterable ? "saveAll" : null;
            case "findAll":
                return iterable ? "findAllById" : null;
            default:
                return null;
        }
    }

    /**
     * Tells Spring Data 1.x from 2.x, where a repository's `findOne(Long)` is its own query method and
     * `CrudRepository` has nothing for it to override.
     */
    private static boolean declaresFindOne(JavaType.@Nullable FullyQualified type) {
        if (type == null) {
            return false;
        }
        if (TypeUtils.isOfClassType(type, CRUD_REPOSITORY)) {
            return type.getMethods().stream().anyMatch(method -> "findOne".equals(method.getName()));
        }
        return supertypes(type).stream().anyMatch(MigrateCrudRepositoryMethods::declaresFindOne);
    }

    /**
     * @return `true` for a `T findById(ID)` of the repository's own. On Spring Data 1.x that is a query method, but it
     * means what the `Optional<T> findById(ID)` of `CrudRepository` means on 2.0, with which it no longer compiles.
     */
    private static boolean isEntityFindById(JavaType.Method method, @Nullable JavaType receiver) {
        JavaType.Parameterized crudRepository = asCrudRepository(receiver);
        return crudRepository != null && crudRepository.getTypeParameters().size() == 2 &&
                method.getParameterTypes().size() == 1 && isId(method.getParameterTypes().get(0), receiver) &&
                TypeUtils.isOfType(method.getReturnType(), crudRepository.getTypeParameters().get(0));
    }

    /**
     * A call to `findById` reaches any method of that name a repository has, such as a `List<T> findById(ID)` of its
     * own or a `findById(long)` that a `long` argument prefers, and neither gives the `Optional` the call has to
     * unwrap. Only a person can say what should become of such a method, and of `findOne(id)` next to it.
     */
    private static boolean hasOtherFindById(@Nullable JavaType type, @Nullable JavaType receiver) {
        if (type instanceof JavaType.GenericTypeVariable) {
            return ((JavaType.GenericTypeVariable) type).getBounds().stream()
                    .anyMatch(bound -> hasOtherFindById(bound, receiver));
        }
        if (!(type instanceof JavaType.FullyQualified)) {
            return false;
        }
        JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
        for (JavaType.Method method : fq.getMethods()) {
            if (FIND_BY_ID.equals(method.getName()) && method.getParameterTypes().size() == 1 &&
                    !TypeUtils.isOfClassType(method.getReturnType(), "java.util.Optional") &&
                    !isEntityFindById(method, receiver)) {
                return true;
            }
        }
        return supertypes(fq).stream().anyMatch(supertype -> hasOtherFindById(supertype, receiver));
    }

    /**
     * What an override of `findOne(id)` returns has to become an `Optional`, and only its owner can say how: it may
     * well call something that returns one already. So it stays, with the calls to it, and on Spring Data 2.0 the
     * compiler points it out. Moving its callers alone could silently bypass it.
     */
    private static boolean isImplementation(JavaType.Method method) {
        // javac sets `ABSTRACT` on default methods too
        return (!method.hasFlags(Flag.Abstract) || method.hasFlags(Flag.Default)) &&
                !method.getDeclaringType().getFullyQualifiedName().startsWith("org.springframework.data.");
    }

    /**
     * The parameter types of a call are those of the receiver's type arguments, so `delete(ID)` and `delete(T)` can
     * only be told apart by finding out what `ID` is for that receiver.
     */
    private static boolean isId(JavaType parameter, @Nullable JavaType receiver) {
        JavaType.Parameterized crudRepository = asCrudRepository(receiver);
        if (crudRepository == null) {
            // the erasure of `ID extends Serializable`, for a raw repository
            return TypeUtils.isOfClassType(parameter, "java.io.Serializable");
        }
        List<JavaType> typeArguments = crudRepository.getTypeParameters();
        return typeArguments.size() == 2 && TypeUtils.isOfType(parameter, typeArguments.get(1));
    }

    /**
     * @return `CrudRepository` with the type arguments it has from the point of view of `type`, or `null` if `type`
     * is not a `CrudRepository` or is a raw one.
     */
    private static JavaType.@Nullable Parameterized asCrudRepository(@Nullable JavaType type) {
        if (type instanceof JavaType.GenericTypeVariable) {
            for (JavaType bound : ((JavaType.GenericTypeVariable) type).getBounds()) {
                JavaType.Parameterized crudRepository = asCrudRepository(bound);
                if (crudRepository != null) {
                    return crudRepository;
                }
            }
            return null;
        }
        if (!(type instanceof JavaType.FullyQualified)) {
            return null;
        }
        JavaType.FullyQualified fq = (JavaType.FullyQualified) type;
        if (TypeUtils.isOfClassType(fq, CRUD_REPOSITORY)) {
            return fq instanceof JavaType.Parameterized ? (JavaType.Parameterized) fq : null;
        }
        for (JavaType.FullyQualified supertype : supertypes(fq)) {
            JavaType.Parameterized crudRepository = asCrudRepository(supertype);
            if (crudRepository != null) {
                return crudRepository.withTypeParameters(ListUtils.map(crudRepository.getTypeParameters(),
                        argument -> substitute(argument, fq)));
            }
        }
        return null;
    }

    /**
     * @return The type argument `subtype` gives the type variable `argument`, as `Long` for `K` in `Base<User, Long>`.
     */
    private static JavaType substitute(JavaType argument, JavaType.FullyQualified subtype) {
        if (argument instanceof JavaType.GenericTypeVariable && subtype instanceof JavaType.Parameterized) {
            String name = ((JavaType.GenericTypeVariable) argument).getName();
            List<JavaType> variables = ((JavaType.Parameterized) subtype).getType().getTypeParameters();
            List<JavaType> typeArguments = subtype.getTypeParameters();
            for (int i = 0; i < variables.size() && i < typeArguments.size(); i++) {
                JavaType variable = variables.get(i);
                if (variable instanceof JavaType.GenericTypeVariable &&
                        name.equals(((JavaType.GenericTypeVariable) variable).getName())) {
                    return typeArguments.get(i);
                }
            }
        }
        return argument;
    }

    private static List<JavaType.FullyQualified> supertypes(JavaType.FullyQualified type) {
        List<JavaType.FullyQualified> supertypes = new ArrayList<>(type.getInterfaces());
        if (type.getSupertype() != null) {
            supertypes.add(type.getSupertype());
        }
        return supertypes;
    }
}
