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
package org.openrewrite.java.spring.boot2.search;

import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds a marker to an AST if Spring Data Repository invalid domain ID type is discovered. The marker is over the AST
 * node of an ID type if it is available in the AST. Alternatively it would over the class name the repository extends
 * from.
 *
 * @author Alex Boyko
 */
public class EntityIdForRepositoryVisitor<T> extends JavaIsoVisitor<T> {

    private static final String REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String ID = "org.springframework.data.annotation.Id";

    private static final AnnotationMatcher ID_ANNOTATION_MATCHER = new AnnotationMatcher("@" + ID, true);
    public static final String ID_CLASS = "idClass";

    final private boolean considerIdField;

    public EntityIdForRepositoryVisitor(boolean considerIdField) {
        this.considerIdField = considerIdField;
    }

    public EntityIdForRepositoryVisitor() {
        this(false);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, T p) {
        J.Annotation noBeanRepoAnnotation = findAnnotation(classDecl.getLeadingAnnotations(), "org.springframework.data.repository.NoRepositoryBean");
        if (noBeanRepoAnnotation == null) {
            J.Annotation repoDefAnnotation = findAnnotation(classDecl.getLeadingAnnotations(), "org.springframework.data.repository.RepositoryDefinition");
            if (repoDefAnnotation != null) {
                J.Annotation newRepoDefAnnotation = handleRepoDefinition(repoDefAnnotation);
                if (newRepoDefAnnotation != repoDefAnnotation) {
                    return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), a -> a == repoDefAnnotation ? newRepoDefAnnotation : a));
                }
            }
            return handleRepoType(classDecl);
        }
        return super.visitClassDeclaration(classDecl, p);
    }

    private J.ClassDeclaration handleRepoType(J.ClassDeclaration typeDecl) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(typeDecl.getType());
        if (type != null) {
            List<JavaType.FullyQualified> repoTypeChain = findRepoTypeChain(type, Collections.emptyList());
            if (repoTypeChain != null) {
                JavaType domainType = null;
                JavaType idType = null;
                int domainTypeIndex = 0;
                int idTypeIndex = 1;
                int idTypeIndexInChain = repoTypeChain.size() - 1;
                for (int i = repoTypeChain.size() - 1; i >= 0 && (idType == null || idType instanceof JavaType.GenericTypeVariable || domainType == null || domainType instanceof JavaType.GenericTypeVariable); i--) {
                    JavaType.FullyQualified repoType = repoTypeChain.get(i);
                    List<JavaType> typeParams = repoType.getTypeParameters();
                    boolean domainTypeChanged = false;
                    boolean idTypeChanged = false;
                    if (repoType instanceof JavaType.Parameterized) {
                        if (domainType instanceof JavaType.GenericTypeVariable || domainType == null) {
                            int idx = domainType == null ? -1 : findTypeVarIndex(((JavaType.Parameterized) repoType).getType().getTypeParameters(), ((JavaType.GenericTypeVariable) domainType).getName());
                            if (idx < 0) {
                                domainType = typeParams.get(domainTypeIndex);
                            } else {
                                domainTypeIndex = idx;
                                domainType = typeParams.get(domainTypeIndex);
                            }
                            domainTypeChanged = true;
                        }
                        if (idType instanceof JavaType.GenericTypeVariable || idType == null) {
                            idTypeIndexInChain = i;
                            int idx = idType == null ? -1 : findTypeVarIndex(((JavaType.Parameterized) repoType).getType().getTypeParameters(), ((JavaType.GenericTypeVariable) idType).getName());
                            if (idx < 0) {
                                idType = typeParams.get(idTypeIndex);
                            } else {
                                idTypeIndex = idx;
                                idType = typeParams.get(idTypeIndex);
                            }
                            idTypeChanged = true;
                        }
                    } else {
                        if (idType instanceof JavaType.GenericTypeVariable || idType == null) {
                            idTypeIndexInChain = i;
                            idType = typeParams.get(idTypeIndex);
                            idTypeChanged = true;
                        }
                        if (domainType instanceof JavaType.GenericTypeVariable || domainType == null) {
                            domainType = typeParams.get(domainTypeIndex);
                            domainTypeChanged = true;
                        }
                    }
                    // Adjust domainTypeIndex or idTypeIndex if needed as well as remaining expected number of parameters
                    if (idType instanceof JavaType.FullyQualified && idTypeChanged) {
                        if (domainType instanceof JavaType.GenericTypeVariable && domainTypeIndex > idTypeIndex) {
                            domainTypeIndex--;
                        }
                    }
                    if (domainType instanceof JavaType.FullyQualified && domainTypeChanged) {
                        if (idType instanceof JavaType.GenericTypeVariable && idTypeIndex > domainTypeIndex) {
                            idTypeIndex--;
                        }
                    }
                }
                JavaType.FullyQualified domainClassType;
                if (domainType instanceof JavaType.GenericTypeVariable) {
                    domainClassType = TypeUtils.asFullyQualified(((JavaType.GenericTypeVariable) domainType).getBounds().get(0));
                } else {
                    domainClassType = TypeUtils.asFullyQualified(domainType);
                }
                if (domainClassType != null) {
                    JavaType domainIdType = findIdType(domainClassType);
                    if (domainIdType != null && !isValidRepoIdType(idType, domainIdType)) {
                        if (idTypeIndexInChain == 0) {
                            List<J.TypeParameter> astParams = typeDecl.getTypeParameters();
                            List<JavaType> params = typeDecl.getType().getTypeParameters();
                            int idx = params.indexOf(idType);
                            if (idx < 0 || astParams == null || astParams.size() <= idx) {
                                return typeDecl.withName(typeDecl.getName().withMarkers(typeDecl.getName().getMarkers().addIfAbsent(createMarker(domainIdType))));
                            } else {
                                astParams.set(idx, astParams.get(idx).withMarkers(astParams.get(idx).getMarkers().addIfAbsent(createMarker(domainIdType))));
                                return typeDecl.withTypeParameters(astParams);
                            }
                        } else {
                            if (typeDecl.getExtends() != null && repoTypeChain.get(1).equals(typeDecl.getExtends().getType())) {
                                return typeDecl.withExtends(markTypeParam(typeDecl.getExtends(), idTypeIndex, createMarker(domainIdType)));
                            } else if (typeDecl.getImplements() != null) {
                                final int finalIdTypeIndex = idTypeIndex;
                                final int finalIdTypeIndexInChain = idTypeIndexInChain;
                                AtomicBoolean hasMarker = new AtomicBoolean(false);
                                J.ClassDeclaration newTypeDecl = typeDecl.withImplements(ListUtils.map(typeDecl.getImplements(), it -> {
                                    JavaType.FullyQualified interfaceType = TypeUtils.asFullyQualified(it.getType());
                                    if (repoTypeChain.get(1).equals(interfaceType)) {
                                        hasMarker.set(true);
                                        return markTypeParam(it, finalIdTypeIndexInChain == 1 ? finalIdTypeIndex : -1, createMarker(domainIdType));
                                    }
                                    return it;
                                }));
                                if (hasMarker.get()) {
                                    return newTypeDecl;
                                }
                            }
                        }
                        return typeDecl.withName(typeDecl.getName().withMarkers(typeDecl.getName().getMarkers().addIfAbsent(createMarker(domainIdType))));
                    }
                }
            }
        }
        return typeDecl;
    }

    private static TypeTree markTypeParam(TypeTree tt, int idx, Marker m) {
        if (tt instanceof J.ParameterizedType) {
            J.ParameterizedType pt = (J.ParameterizedType) tt;
            List<Expression> astParams = pt.getTypeParameters();
            if (astParams != null && idx >= 0 && idx < astParams.size()) {
                astParams.set(idx, astParams.get(idx).withMarkers(astParams.get(idx).getMarkers().addIfAbsent(m)));
                return pt.withTypeParameters(astParams);
            }
        }
        return tt.withMarkers(tt.getMarkers().addIfAbsent(m));
    }

    private static int findTypeVarIndex(List<JavaType> typeParams, String genericVarName) {
        for (int i = 0; i < typeParams.size(); i++) {
            JavaType t = typeParams.get(i);
            if (t instanceof JavaType.GenericTypeVariable) {
                if (genericVarName.equals(((JavaType.GenericTypeVariable) t).getName())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static @Nullable List<JavaType.FullyQualified> findRepoTypeChain(JavaType.FullyQualified type, List<JavaType.FullyQualified> visited) {
        if (visited.contains(type)) {
            return null;
        }
        ArrayList<JavaType.FullyQualified> ls = new ArrayList<>(visited.size() + 1);
        ls.addAll(visited);
        ls.add(type);
        if (type instanceof JavaType.Parameterized &&  REPOSITORY.equals(((JavaType.Parameterized) type).getType().getFullyQualifiedName())) {
            return ls;
        }

        if (type.getSupertype() != null) {
            List<JavaType.FullyQualified> superChain = findRepoTypeChain(type.getSupertype(), ls);
            if (superChain != null) {
                return superChain;
            }
        }

        for (JavaType.FullyQualified it : type.getInterfaces()) {
            List<JavaType.FullyQualified> superChain = findRepoTypeChain(it, ls);
            if (superChain != null) {
                return superChain;
            }
        }

        return null;
    }

    private J.Annotation handleRepoDefinition(J.Annotation repoDefAnnotation) {
        JavaType.FullyQualified domainClass = TypeUtils.asFullyQualified(getParameterClass(repoDefAnnotation, "domainClass"));
        if (domainClass != null) {
            JavaType idType = findIdType(domainClass);
            if (idType != null) {
                JavaType.FullyQualified repoIdType = TypeUtils.asFullyQualified(getParameterClass(repoDefAnnotation, ID_CLASS));
                if (repoIdType != null) {
                    if (!isValidRepoIdType(repoIdType, idType)) {
                        Marker marker = createMarker(idType);
                        return repoDefAnnotation.withArguments(ListUtils.map(repoDefAnnotation.getArguments(), arg -> {
                            if (arg instanceof J.Assignment) {
                                J.Assignment assign = (J.Assignment) arg;
                                if (assign.getVariable() instanceof J.Identifier && ID_CLASS.equals(((J.Identifier) assign.getVariable()).getSimpleName())) {
                                    return assign.withAssignment(assign.getAssignment().withMarkers(assign.getAssignment().getMarkers().addIfAbsent(marker)));
                                }
                            }
                            return arg;
                        }));
                    }
                }
            }
        }

        return repoDefAnnotation;
    }

    private boolean isValidRepoIdType(JavaType repoIdType, JavaType idType) {
        return TypeUtils.isAssignableTo(idType, repoIdType) || TypeUtils.isAssignableTo(repoIdType, idType);
    }

    protected Marker createMarker(JavaType domainIdType) {
        return new SearchResult(Tree.randomId(), "Expected Domain Type ID is '" + domainIdType + "'");
    }

    private static J.@Nullable Annotation findAnnotation(Collection<J.Annotation> annotations, String fqName) {
        for (J.Annotation a : annotations) {
            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(a.getAnnotationType().getType());
            if (fqType != null && fqName.equals(fqType.getFullyQualifiedName())) {
                return a;
            }
        }
        return null;
    }

    private static @Nullable JavaType getParameterClass(J.Annotation a, String arg) {
        J.Assignment assign = getArgument(a, arg);
        if (assign != null) {
            JavaType type = assign.getAssignment().getType();
            if (type instanceof JavaType.Parameterized) {
                JavaType.Parameterized parameterizedType = (JavaType.Parameterized) type;
                if ("java.lang.Class".equals(parameterizedType.getType().getFullyQualifiedName())) {
                    return parameterizedType.getTypeParameters().get(0);
                }
            }
        }
        return null;
    }

    private static J.@Nullable Assignment getArgument(J.Annotation a, String arg) {
        if (a.getArguments() != null) {
            for (Expression e : a.getArguments()) {
                if (e instanceof J.Assignment) {
                    J.Assignment assign = (J.Assignment) e;
                    if (assign.getVariable() instanceof J.Identifier && arg.equals(((J.Identifier) assign.getVariable()).getSimpleName())) {
                        return assign;
                    }
                }
            }
        }
        return null;
    }

    private @Nullable JavaType findIdType(JavaType.FullyQualified type) {
        JavaType idType = findAnnotatedIdType(type, new HashSet<>());
        if (idType == null && considerIdField) {
            idType = findIdFieldType(type);
        }
        return idType;
    }

    private static JavaType findIdFieldType(JavaType.FullyQualified type) {
        for (JavaType.Variable m : type.getMembers()) {
            if ("id".equals(m.getName())) {
                return m.getType();
            }
        }
        for (JavaType.Method m : type.getMethods()) {
            if ("id".equals(m.getName()) && m.getParameterTypes().isEmpty() && m.getReturnType() != JavaType.Primitive.Void) {
                return m.getReturnType();
            }
        }
        if (type.getSupertype() != null) {
            return findIdFieldType(type.getSupertype());
        }
        return null;
    }

    private static @Nullable JavaType findAnnotatedIdType(JavaType.FullyQualified type, Set<String> visited) {
        for (JavaType.Variable m : type.getMembers()) {
            String s = fieldSignature(m);
            if (!visited.contains(s) && m.getAnnotations().stream().anyMatch(ID_ANNOTATION_MATCHER::matchesAnnotationOrMetaAnnotation)) {
                return m.getType();
            }
            visited.add(s);
        }
        for (JavaType.Method m : type.getMethods()) {
            String s = methodSignature(m);
            if (!visited.contains(s) && m.getAnnotations().stream().anyMatch(ID_ANNOTATION_MATCHER::matchesAnnotationOrMetaAnnotation)) {
                return m.getReturnType();
            }
            visited.add(s);
        }
        if (type.getSupertype() != null) {
            return findAnnotatedIdType(type.getSupertype(), visited);
        }
        return null;
    }

    private static String methodSignature(JavaType.Method m) {
        String s = MethodMatcher.methodPattern(m);
        int idx = s.indexOf(' ');
        if (idx >= 0 && idx < s.length() - 1) {
            return s.substring(idx + 1);
        }
        return s;
    }

    private static String fieldSignature(JavaType.Variable f) {
        return f.getName();
    }

}
