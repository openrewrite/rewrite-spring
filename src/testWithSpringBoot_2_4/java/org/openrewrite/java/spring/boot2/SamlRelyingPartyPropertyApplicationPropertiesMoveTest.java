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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;

class SamlRelyingPartyPropertyApplicationPropertiesMoveTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SamlRelyingPartyPropertyApplicationPropertiesMove());
    }

    @Test
    void movePropertyTestSingle() {
        rewriteRun(
          srcMainResources(
            //language=properties
            properties(
              """
                spring.security.saml2.relyingparty.registration.idpone.identityprovider.entity-id=https://idpone.com
                spring.security.saml2.relyingparty.registration.idpone.identityprovider.sso-url=https://idpone.com
                spring.security.saml2.relyingparty.registration.idpone.identityprovider.verification.credentials.certificate-location=classpath:saml/idpone.crt
                """,
              """
                spring.security.saml2.relyingparty.registration.idpone.assertingparty.entity-id=https://idpone.com
                spring.security.saml2.relyingparty.registration.idpone.assertingparty.sso-url=https://idpone.com
                spring.security.saml2.relyingparty.registration.idpone.assertingparty.verification.credentials.certificate-location=classpath:saml/idpone.crt
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void movePropertyTestMultiple() {
        rewriteRun(
          srcMainResources(
            //language=properties
            properties(
              """
                    spring.security.saml2.relyingparty.registration.idpone.identityprovider.entity-id=https://idpone.com
                    spring.security.saml2.relyingparty.registration.idpone.identityprovider.sso-url=https://idpone.com
                    spring.security.saml2.relyingparty.registration.idpone.identityprovider.verification.credentials.certificate-location=classpath:saml/idpone.crt
                                
                    spring.security.saml2.relyingparty.registration.okta.identityprovider.entity-id=https://idpone.com
                    spring.security.saml2.relyingparty.registration.okta.identityprovider.sso-url=https://idpone.com
                    spring.security.saml2.relyingparty.registration.okta.identityprovider.verification.credentials.certificate-location=classpath:saml/idpone.crt
                """,
              """
                spring.security.saml2.relyingparty.registration.idpone.assertingparty.entity-id=https://idpone.com
                spring.security.saml2.relyingparty.registration.idpone.assertingparty.sso-url=https://idpone.com
                spring.security.saml2.relyingparty.registration.idpone.assertingparty.verification.credentials.certificate-location=classpath:saml/idpone.crt
                            
                spring.security.saml2.relyingparty.registration.okta.assertingparty.entity-id=https://idpone.com
                spring.security.saml2.relyingparty.registration.okta.assertingparty.sso-url=https://idpone.com
                spring.security.saml2.relyingparty.registration.okta.assertingparty.verification.credentials.certificate-location=classpath:saml/idpone.crt
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void shouldNotMovePropertyInWrongHierarchy() {
        rewriteRun(
          srcMainResources(
            //language=properties
            properties(
              """
                spring.security.saml2.relyingparty.identityprovider.registration.idpone.entity-id=https://idpone.com
                spring.identityprovider.security.saml2.relyingparty.registration.idpone.sso-url=https://idpone.com
                spring.security.identityprovider.saml2.relyingparty.registration.idpone.verification.credentials.certificate-location=classpath:saml/idpone.crt
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }
}
