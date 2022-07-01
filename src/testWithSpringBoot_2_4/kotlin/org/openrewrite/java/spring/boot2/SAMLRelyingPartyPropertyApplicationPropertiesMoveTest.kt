package org.openrewrite.java.spring.boot2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.Result
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.properties.PropertiesParser
import java.nio.file.Paths

class SAMLRelyingPartyPropertyApplicationPropertiesMoveTest : JavaRecipeTest {

    override val recipe: Recipe
        get() = SAMLRelyingPartyPropertyApplicationPropertiesMove()

    @Test
    fun movePropertyTestSingle() {
        val result = runRecipe(
            """
            spring.security.saml2.relyingparty.registration.idpone.identityprovider.entity-id=https://idpone.com
            spring.security.saml2.relyingparty.registration.idpone.identityprovider.sso-url=https://idpone.com
            spring.security.saml2.relyingparty.registration.idpone.identityprovider.verification.credentials.certificate-location=classpath:saml/idpone.crt
            """.trimIndent()
        )
        assertThat(result).hasSize(1)
        assertThat(result[0].after!!.printAll()).isEqualTo("""
            spring.security.saml2.relyingparty.registration.idpone.assertingparty.entity-id=https://idpone.com
            spring.security.saml2.relyingparty.registration.idpone.assertingparty.sso-url=https://idpone.com
            spring.security.saml2.relyingparty.registration.idpone.assertingparty.verification.credentials.certificate-location=classpath:saml/idpone.crt
            """.trimIndent())
    }

    private fun runRecipe(inputProperties: String): MutableList<Result> {
        val applicationProperties = PropertiesParser().parse(inputProperties)
            .map { it.withSourcePath(Paths.get("src/main/resources/application.properties")) }

        return recipe.run(applicationProperties)
    }
}
