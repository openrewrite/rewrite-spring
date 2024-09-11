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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;

class SeparateApplicationPropertiesByProfileTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SeparateApplicationPropertiesByProfile());
    }

    @Test
    void noApplicationProperties() {
        rewriteRun(
          properties(
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              """,
            sourceSpecs -> sourceSpecs.path("application-dev.properties"))
        );
    }

    @Test
    void noSeparateProfile() {
        rewriteRun(
          properties(
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              """,
            sourceSpecs -> sourceSpecs.path("application.properties"))
        );
    }

    @DocumentExample
    @Test
    void separateProfileWithAppend() {
        rewriteRun(
          properties(
            //language=properties
            """
              line1=line1
              """,
            //language=properties
            """
              line1=line1
              oauth2.clientId=9999999999999999999999
              service.domainUrl= https://this.is.my.dev.url.com
              app.config.currentEnvironment=DEV
              """,
            sourceSpecs -> sourceSpecs.path("application-dev.properties")
          ),
          properties(
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              !---
              spring.config.activate.on-profile=dev
              oauth2.clientId=9999999999999999999999
              service.domainUrl= https://this.is.my.dev.url.com
              app.config.currentEnvironment=DEV
              #---
              spring.config.activate.on-profile=local
              app.config.currentEnvironment=LOCAL
              
              
              #---
              #### XX Configuration ####
              spring.config.activate.on-profile=prod
              oauth2.clientId=77777777777777
              service.domainUrl=https://this.is.my.prod.url.com
              """,
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              """,
            sourceSpecs -> sourceSpecs.path("application.properties")
          ),
          properties(
            null,
            """
              app.config.currentEnvironment=LOCAL
              """,
            sourceSpecs -> sourceSpecs.path("application-local.properties")
          ),
          properties(
            null,
            //language=properties
            """
              #### XX Configuration ####
              oauth2.clientId=77777777777777
              service.domainUrl=https://this.is.my.prod.url.com
              """,
            sourceSpecs -> sourceSpecs.path("application-prod.properties")
          )
        );
    }

    @Test
    void separateProfileWithoutAppend() {
        rewriteRun(
          properties(
            null,
            //language=properties
            """
              oauth2.clientId=9999999999999999999999
              service.domainUrl= https://this.is.my.dev.url.com
              app.config.currentEnvironment=DEV
              """,
            sourceSpecs -> sourceSpecs.path("application-dev.properties")
          ),
          properties(
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              !---
              spring.config.activate.on-profile=dev
              oauth2.clientId=9999999999999999999999
              service.domainUrl= https://this.is.my.dev.url.com
              app.config.currentEnvironment=DEV
              #---
              spring.config.activate.on-profile=local
              app.config.currentEnvironment=LOCAL
              
              
              #---
              #### XX Configuration ####
              spring.config.activate.on-profile=prod
              oauth2.clientId=77777777777777
              service.domainUrl=https://this.is.my.prod.url.com
              """,
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              """,
            sourceSpecs -> sourceSpecs.path("application.properties")
          ),
          properties(
            null,
            //language=properties
            """
              app.config.currentEnvironment=LOCAL
              """,
            sourceSpecs -> sourceSpecs.path("application-local.properties")
          ),
          properties(
            null,
            //language=properties
            """
              #### XX Configuration ####
              oauth2.clientId=77777777777777
              service.domainUrl=https://this.is.my.prod.url.com
              """,
            sourceSpecs -> sourceSpecs.path("application-prod.properties")
          )
        );
    }

    @Test
    void pathToApplicationProperties() {
        rewriteRun(
          properties(
            //language=properties
            """
              line1=line1
              """,
            //language=properties
            """
              line1=line1
              oauth2.clientId=9999999999999999999999
              service.domainUrl= https://this.is.my.dev.url.com
              app.config.currentEnvironment=DEV
              """,
            sourceSpecs -> sourceSpecs.path("folder1/folder2/application-dev.properties")
          ),
          properties(
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              !---
              spring.config.activate.on-profile=dev
              oauth2.clientId=9999999999999999999999
              service.domainUrl= https://this.is.my.dev.url.com
              app.config.currentEnvironment=DEV
              #---
              spring.config.activate.on-profile=local
              app.config.currentEnvironment=LOCAL
              
              
              #---
              #### XX Configuration ####
              spring.config.activate.on-profile=prod
              oauth2.clientId=77777777777777
              service.domainUrl=https://this.is.my.prod.url.com
              """,
            //language=properties
            """
              spring.application.name=Openrewrite-PR-Service
              #PR-Service
              base-url.PR-services=http://my.url.com
              exchange-token=1234567890
              exchange-tokens=${base-url.PR-services}/exchange-token
              """,
            sourceSpecs -> sourceSpecs.path("folder1/folder2/application.properties")
          ),
          properties(
            null,
            //language=properties
            """
              app.config.currentEnvironment=LOCAL
              """,
            sourceSpecs -> sourceSpecs.path("folder1/folder2/application-local.properties")
          ),
          properties(
            null,
            //language=properties
            """
              #### XX Configuration ####
              oauth2.clientId=77777777777777
              service.domainUrl=https://this.is.my.prod.url.com
              """,
            sourceSpecs -> sourceSpecs.path("folder1/folder2/application-prod.properties")
          )
        );
    }
}
