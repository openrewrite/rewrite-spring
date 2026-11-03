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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class PropertiesToKebabCaseTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResource(
          "/META-INF/rewrite/properties-to-kebab-case.yml",
          "org.openrewrite.java.spring.PropertiesToKebabCase");
    }

    @DocumentExample
    @Test
    void singleFlatProperty() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              "spring.main.showBanner: true",
              "spring.main.show-banner: true",
              spec -> spec.path("application.yaml")
            ),
            properties(
              "spring.main.showBanner=true",
              "spring.main.show-banner=true",
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void singleNestedProperty() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  spring:
                    main:
                      showBanner: true
                """,
              """
                  spring:
                    main:
                      show-banner: true
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void multipleFlatProperties() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  spring.main.showBanner: true
                  myCustom.propertyValue.GOES_HERE: example
                """,
              """
                  spring.main.show-banner: true
                  my-custom.property-value.goes-here: example
                """,
              spec -> spec.path("application.yaml")
            ),
            properties(
              """
                spring.main.showBanner=true
                myCustom.propertyValue.GOES_HERE=EXAMPLE
                """,
              """
                spring.main.show-banner=true
                my-custom.property-value.goes-here=EXAMPLE
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void multipleNestedProperties() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  SOME.EXAMPLE_CUSTOM:
                    firstNested:
                      firstValue: first-example
                      secondValue: second-example
                    second_nested: second_nested_example
                  another:
                    exampleGoes:
                      HERE: example
                """,
              """
                  some.example-custom:
                    first-nested:
                      first-value: first-example
                      second-value: second-example
                    second-nested: second_nested_example
                  another:
                    example-goes:
                      here: example
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void jpaPassThroughPropertiesUnchangedYaml() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                spring:
                  jpa:
                    properties:
                      hibernate:
                        default_schema: my_schema
                        default_batch_fetch_size: 16
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void jpaPassThroughPropertiesUnchangedProperties() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                spring.jpa.properties.hibernate.default_schema=my_schema
                spring.jpa.properties.hibernate.default_batch_fetch_size=16
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void jpaPassThroughButOtherKeysStillConverted() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                spring:
                  jpa:
                    showSql: true
                    properties:
                      hibernate:
                        default_schema: my_schema
                """,
              """
                spring:
                  jpa:
                    show-sql: true
                    properties:
                      hibernate:
                        default_schema: my_schema
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void kafkaPassThroughPropertiesUnchanged() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                spring:
                  kafka:
                    properties:
                      sasl.jaas.config: something
                    consumer:
                      properties:
                        max.poll_interval.ms: 300000
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void quartzPassThroughPropertiesUnchanged() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                spring.quartz.properties.org.quartz.threadPool.threadCount=5
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void flatYamlJpaPassThroughUnchanged() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                spring.jpa.properties.hibernate.default_schema: my_schema
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void passThroughPropertiesUnchangedProperties() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                logging.level.org.springframework.web=DEBUG
                logging.level.com.example.MyService=TRACE
                management.metrics.tags.applicationName=myApp
                management.metrics.tags.teamName=backend
                management.metrics.enable.jvm=false
                management.metrics.enable.process=true
                management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99
                management.metrics.distribution.percentiles-histogram.http.server.requests=true
                spring.flyway.placeholders.schema_name=public
                spring.flyway.placeholders.appVersion=1.0
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void passThroughPropertiesUnchangedYaml() {
        rewriteRun(
          srcMainResources(
            yaml(
              """
                logging:
                  level:
                    org.springframework.web: DEBUG
                    com.example.MyService: TRACE
                management:
                  metrics:
                    tags:
                      applicationName: myApp
                      teamName: backend
                    enable:
                      jvm: false
                      process: true
                    distribution:
                      percentiles:
                        http.server.requests: 0.5,0.95,0.99
                spring:
                  flyway:
                    placeholders:
                      schema_name: public
                      appVersion: 1.0
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void driverPropertiesKeepTheirNames() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                spring.datasource.hikari.dataSourceProperties.cachePrepStmts=true
                spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
                """,
              """
                spring.datasource.hikari.data-source-properties.cachePrepStmts=true
                spring.datasource.hikari.data-source-properties.prepStmtCacheSize=250
                """,
              spec -> spec.path("application.properties")
            ),
            yaml(
              """
                spring:
                  datasource:
                    hikari:
                      dataSourceProperties:
                        cachePrepStmts: true
                        prepStmtCacheSize: 250
                """,
              """
                spring:
                  datasource:
                    hikari:
                      data-source-properties:
                        cachePrepStmts: true
                        prepStmtCacheSize: 250
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void clientRegistrationIdsKeepTheirNames() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                spring.security.oauth2.client.registration.myClient.client-id=abc
                spring.security.oauth2.client.provider.myProvider.issuer-uri=https://idp.example.com
                """,
              spec -> spec.path("application.properties")
            ),
            yaml(
              """
                spring:
                  security:
                    oauth2:
                      client:
                        registration:
                          myClient:
                            client-id: abc
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void userDefinedNamesUnchanged() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                info.app.buildVersion=1.0
                logging.group.myGroup=com.example
                management.endpoint.health.group.customGroup.include=db
                spring.profiles.group.myProd=db,cache
                spring.cloud.stream.bindings.processOrder-in-0.destination=orders
                resilience4j.circuitbreaker.instances.backendA.sliding-window-size=10
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void keysBeforeTheUserDefinedNameAreConverted() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                management.metrics.distribution.percentilesHistogram.http.server.requests=true
                """,
              """
                management.metrics.distribution.percentiles-histogram.http.server.requests=true
                """,
              spec -> spec.path("application.properties")
            )
          )
        );
    }

    @Test
    void bracketedMapKeysUnchanged() {
        rewriteRun(
          srcMainResources(
            properties(
              """
                myApp.featureFlags[newCheckout]=true
                """,
              """
                my-app.feature-flags[newCheckout]=true
                """,
              spec -> spec.path("application.properties")
            ),
            yaml(
              """
                myApp:
                  featureFlags:
                    "[newCheckout]": true
                  clients:
                    "[api.example.com]":
                      connectTimeout: 5s
                """,
              """
                my-app:
                  feature-flags:
                    "[newCheckout]": true
                  clients:
                    "[api.example.com]":
                      connectTimeout: 5s
                """,
              spec -> spec.path("application.yaml")
            )
          )
        );
    }

    @Test
    void doNotChange() {
        //language=yaml
        rewriteRun(
          srcMainResources(
            yaml(
              """
                  some.example-custom:
                    first-nested:
                      first-value: first-example
                      second-value: second-example
                    second-nested: second_nested_example
                  another:
                    example-goes:
                      here: example
                """,
              spec -> spec.path("application.yaml")
            ),
            properties(
              """
                some.example-custom.first-nested.first-value=first-example
                some.example-custom.first-nested.second-value=second-example
                some.example-custom.second-nested=second_nested_example
                another.example-goes.here=example
                """
            )
          )
        );
    }
}
