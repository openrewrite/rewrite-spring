/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.springdoc;

import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.Collections;
import java.util.List;

/**
 * <p>Mapping between SpringFox Docket and OpenAPI:</p>
 * <table border="1" cellpadding="4" cellspacing="0">
 *   <thead>
 *     <tr>
 *       <th>Docket API</th>
 *       <th>OpenAPI API</th>
 *       <th>Notes</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>apiInfo(ApiInfo)</td>
 *       <td>info(Info)</td>
 *       <td>Both configure API metadata (title, description, version, contact, license)</td>
 *     </tr>
 *     <tr>
 *       <td>securitySchemes(List&lt;SecurityScheme&gt;)</td>
 *       <td>components(Components) with security schemes</td>
 *       <td>Both define security scheme definitions</td>
 *     </tr>
 *     <tr>
 *       <td>securityContexts(List&lt;SecurityContext&gt;)</td>
 *       <td>security(List&lt;SecurityRequirement&gt;)</td>
 *       <td>Both define global security requirements</td>
 *     </tr>
 *     <tr>
 *       <td>groupName(String)</td>
 *       <td>(none)</td>
 *       <td>Grouping is SpringFox-specific, not part of OpenAPI spec</td>
 *     </tr>
 *     <tr>
 *       <td>pathProvider(PathProvider)</td>
 *       <td>(none)</td>
 *       <td>URL generation is framework-specific</td>
 *     </tr>
 *     <tr>
 *       <td>globalResponseMessage(RequestMethod, List)</td>
 *       <td>(none)</td>
 *       <td>OpenAPI defines responses per-operation in paths</td>
 *     </tr>
 *     <tr>
 *       <td>globalOperationParameters(List)</td>
 *       <td>(none)</td>
 *       <td>OpenAPI defines parameters per-operation in paths</td>
 *     </tr>
 *     <tr>
 *       <td>ignoredParameterTypes(Class...)</td>
 *       <td>(none)</td>
 *       <td>Type filtering is framework-specific</td>
 *     </tr>
 *     <tr>
 *       <td>produces(Set&lt;String&gt;)</td>
 *       <td>(none)</td>
 *       <td>OpenAPI defines media types per-operation in paths</td>
 *     </tr>
 *     <tr>
 *       <td>consumes(Set&lt;String&gt;)</td>
 *       <td>(none)</td>
 *       <td>OpenAPI defines media types per-operation in paths</td>
 *     </tr>
 *     <tr>
 *       <td>host(String)</td>
 *       <td>servers(List&lt;Server&gt;)</td>
 *       <td>Both specify API host/base URL</td>
 *     </tr>
 *     <tr>
 *       <td>protocols(Set&lt;String&gt;)</td>
 *       <td>servers(List&lt;Server&gt;)</td>
 *       <td>Protocols are part of server URL schemes in OpenAPI</td>
 *     </tr>
 *     <tr>
 *       <td>alternateTypeRules(AlternateTypeRule...)</td>
 *       <td>(none)</td>
 *       <td>Type mapping is framework-specific</td>
 *     </tr>
 *     <tr>
 *       <td>directModelSubstitute(Class, Class)</td>
 *       <td>(none)</td>
 *       <td>Type substitution is framework-specific</td>
 *     </tr>
 *     <tr>
 *       <td>genericModelSubstitutes(Class...)</td>
 *       <td>(none)</td>
 *       <td>Generic type handling is framework-specific</td>
 *     </tr>
 *     <tr>
 *       <td>additionalModels(ResolvedType...)</td>
 *       <td>schema(String, Schema)</td>
 *       <td>Both add schema definitions to components</td>
 *     </tr>
 *     <tr>
 *       <td>operationOrdering(Comparator)</td>
 *       <td>(none)</td>
 *       <td>Operation ordering is presentation-specific</td>
 *     </tr>
 *     <tr>
 *       <td>apiListingReferenceOrdering(Comparator)</td>
 *       <td>(none)</td>
 *       <td>API listing ordering is presentation-specific</td>
 *     </tr>
 *     <tr>
 *       <td>apiDescriptionOrdering(Comparator)</td>
 *       <td>(none)</td>
 *       <td>API description ordering is presentation-specific</td>
 *     </tr>
 *     <tr>
 *       <td>tags(Tag...)</td>
 *       <td>tags(List&lt;Tag&gt;) or addTagsItem(Tag)</td>
 *       <td>Both add global tag definitions</td>
 *     </tr>
 *     <tr>
 *       <td>enable(boolean)</td>
 *       <td>(none)</td>
 *       <td>Framework-specific plugin control</td>
 *     </tr>
 *     <tr>
 *       <td>forCodeGeneration(boolean)</td>
 *       <td>(none)</td>
 *       <td>Framework-specific optimization flag</td>
 *     </tr>
 *     <tr>
 *       <td>pathMapping(String)</td>
 *       <td>servers(List&lt;Server&gt;)</td>
 *       <td>Both handle base path prefixes</td>
 *     </tr>
 *     <tr>
 *       <td>enableUrlTemplating(boolean)</td>
 *       <td>(none)</td>
 *       <td>Framework-specific URL templating</td>
 *     </tr>
 *     <tr>
 *       <td>useDefaultResponseMessages(boolean)</td>
 *       <td>(none)</td>
 *       <td>Framework-specific response generation control</td>
 *     </tr>
 *     <tr>
 *       <td>select()</td>
 *       <td>paths(Paths)</td>
 *       <td>Both control which API endpoints are documented</td>
 *     </tr>
 *     <tr>
 *       <td>configure(DocumentationContextBuilder)</td>
 *       <td>(none)</td>
 *       <td>Framework-specific builder pattern</td>
 *     </tr>
 *     <tr>
 *       <td>getGroupName()</td>
 *       <td>(none)</td>
 *       <td>No equivalent getter in OpenAPI</td>
 *     </tr>
 *     <tr>
 *       <td>isEnabled()</td>
 *       <td>(none)</td>
 *       <td>No equivalent getter in OpenAPI</td>
 *     </tr>
 *     <tr>
 *       <td>getDocumentationType()</td>
 *       <td>getOpenapi() or getSpecVersion()</td>
 *       <td>Both return specification version information</td>
 *     </tr>
 *     <tr>
 *       <td>supports(DocumentationType)</td>
 *       <td>(none)</td>
 *       <td>Framework-specific compatibility check</td>
 *     </tr>
 *     <tr>
 *       <td>(none)</td>
 *       <td>externalDocs(ExternalDocumentation)</td>
 *       <td>OpenAPI supports external documentation at API level</td>
 *     </tr>
 *     <tr>
 *       <td>(none)</td>
 *       <td>path(String, PathItem)</td>
 *       <td>OpenAPI allows direct path addition; Docket uses builder pattern</td>
 *     </tr>
 *     <tr>
 *       <td>(none)</td>
 *       <td>webhooks(Map)</td>
 *       <td>Webhooks are OpenAPI 3.1.0+ feature, not in SpringFox</td>
 *     </tr>
 *     <tr>
 *       <td>(none)</td>
 *       <td>schemaRequirement(String, SecurityScheme)</td>
 *       <td>Alternative approach to security scheme definitions</td>
 *     </tr>
 *     <tr>
 *       <td>(none)</td>
 *       <td>jsonSchemaDialect(String)</td>
 *       <td>JSON Schema dialect is OpenAPI 3.1.0+ feature</td>
 *     </tr>
 *     <tr>
 *       <td>(none)</td>
 *       <td>openapi(String)</td>
 *       <td>OpenAPI version string property</td>
 *     </tr>
 *     <tr>
 *       <td>extensions(List&lt;VendorExtension&gt;)</td>
 *       <td>extensions(Map) or addExtension(String, Object)</td>
 *       <td>Both support vendor extensions, different structure</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <p>Key Observations:</p>
 * <ol>
 *   <li>Direct mappings exist for: API metadata (info), security configuration, host/server configuration, tags, and schema definitions</li>
 *   <li>No OpenAPI equivalents for Docket's framework-specific features like grouping, type substitution, ordering, and filtering</li>
 *   <li>No Docket equivalents for OpenAPI's external docs, webhooks, and some newer OpenAPI 3.1.0 features</li>
 *   <li>Many Docket global settings (produces, consumes, parameters, responses) are defined per-operation in OpenAPI rather than globally</li>
 * </ol>
 */
public class DocketToOpenAPI extends Recipe {
    // Constructor
    private static final MethodMatcher DOCKET_MATCHER = new MethodMatcher("springfox.documentation.spring.web.plugins.Docket <constructor>(springfox.documentation.spi.DocumentationType)");

    @Override
    public String getDisplayName() {
        return "Migrate `Docket` to `OpenAPI`";
    }

    @Override
    public String getDescription() {
        return "Migrate SpringDoc's `Docket` to Swagger's `OpenAPI`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                Tree t = tree;
                t = replaceDocket(ctx, t);
                return t;
            }

            private Tree replaceDocket(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(DOCKET_MATCHER), new JavaVisitor<ExecutionContext>() {
                    // Replace `Contact` constructor
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (DOCKET_MATCHER.matches(newClass)) {
                            maybeRemoveImport("springfox.documentation.spring.web.plugins.Docket");
                            maybeRemoveImport("springfox.documentation.spi.DocumentationType");
                            maybeAddImport("io.swagger.v3.oas.models.OpenAPI");
                            return JavaTemplate.builder("new OpenAPI()")
                                    .imports("io.swagger.v3.oas.models.OpenAPI")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace());
                        }
                        return super.visitNewClass(newClass, ctx);
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }
        };
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Collections.singletonList(
                new ChangeMethodName(
                        "springfox.documentation.spring.web.plugins.Docket apiInfo(..)",
                        "info",
                        true,
                        true)
        );
    }
}
