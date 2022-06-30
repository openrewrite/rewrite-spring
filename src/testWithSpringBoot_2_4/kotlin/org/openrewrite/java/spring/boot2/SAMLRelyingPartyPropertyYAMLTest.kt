/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.Result
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.yaml.YamlParser
import java.nio.file.Paths


/**
 * @author Sandeep Nagaraj
 */
class SAMLRelyingPartyPropertyYAMLTest : JavaRecipeTest {

    override val recipe: Recipe
        get() = SAMLRelyingPartyPropertyYAMLMove()

    @Test
    fun movePropertyTestSingle() {
        val result = runRecipe(
            """
                spring:
                  security:
                    saml2:
                      relyingparty:
                        registration:
                          idpone:
                            identityprovider:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
            """.trimIndent()
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].after!!.printAll()).isEqualTo("""
                spring:
                  security:
                    saml2:
                      relyingparty:
                        registration:
                          idpone:
                            assertingparty:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
            """.trimIndent())
    }

    @Test
    fun movePropertyTestMultiple() {
        val result = runRecipe(
            """
                spring:
                  security:
                    saml2:
                      relyingparty:
                        registration:
                          idpone:
                            identityprovider:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
                          okta:
                            identityprovider:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
            """.trimIndent()
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].after!!.printAll()).isEqualTo(
            """
                spring:
                  security:
                    saml2:
                      relyingparty:
                        registration:
                          idpone:
                            assertingparty:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
                          okta:
                            assertingparty:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
            """.trimIndent()
        )
    }

    @Test
    fun movePropertyWhenCorrectHierarchyIsDetected() {
        val inputYaml = """
                some:
                  random:
                    thing:
                      relyingparty:
                        registration:
                          idpone:
                            identityprovider:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"

            """.trimIndent()

        val result = runRecipe(inputYaml)
        assertThat(result).hasSize(0)
    }

    @Test
    fun resolveBasedOnCorrectHierarchy() {
        val result = runRecipe(
            """
                spring:
                  security:
                    saml2:
                      relyingparty:
                        registration:
                          idpone:
                            identityprovider:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
                relyingparty:
                    registration:
                        something:
                            identityprovider: 
                                of: value
            """.trimIndent()
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].after!!.printAll()).isEqualTo(
            """
                spring:
                  security:
                    saml2:
                      relyingparty:
                        registration:
                          idpone:
                            assertingparty:
                              entity-id: https://idpone.com
                              sso-url: https://idpone.com
                              verification:
                                credentials:
                                  - certificate-location: "classpath:saml/idpone.crt"
                relyingparty:
                    registration:
                        something:
                            identityprovider: 
                                of: value
            """.trimIndent())
    }

    private fun runRecipe(inputYaml: String): List<Result> {
        val applicationYaml = YamlParser().parse(inputYaml)
            .map { it.withSourcePath(Paths.get("src/main/resources/application.yml")) }

        return recipe.run(applicationYaml)
    }
}
