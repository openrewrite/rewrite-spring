package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringBootProperties34Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromYaml(
            //language=yaml
            """
              type: specs.openrewrite.org/v1beta/recipe
              name: org.openrewrite.java.spring.boot3.SpringBootProperties_3_4_Test
              displayName: Migrate Spring Boot properties to 3.4
              description: Migrate properties found in `application.properties` and `application.yml`.
              tags:
                - spring
                - boot
              recipeList:
                - org.openrewrite.java.spring.ChangeSpringPropertyValue:
                    propertyKey: management.endpoint.auditevents.enabled
                    newValue: read-only
                    oldValue: true
                - org.openrewrite.java.spring.ChangeSpringPropertyValue:
                    propertyKey: management.endpoint.auditevents.enabled
                    newValue: none
                    oldValue: false
                - org.openrewrite.java.spring.ChangeSpringPropertyKey:
                    oldPropertyKey: management.endpoint.auditevents.enabled
                    newPropertyKey: management.endpoint.auditevents.access
              """,
            "org.openrewrite.java.spring.boot3.SpringBootProperties_3_4_Test");
    }

    @Test
    void enabledTrueToAccessReadOnly() {
        rewriteRun(
          srcMainResources(
            //language=properties
            properties(
              """
                management.endpoint.auditevents.enabled=true
                """,
              """
                management.endpoint.auditevents.access=read-only
                """,
              s -> s.path("src/main/resources/application.properties")
            )
          )
        );
    }

    @Test
    void enableVirtualThreadsYaml() {
        rewriteRun(
          srcMainResources(
            //language=yaml
            yaml("""
                management:
                  endpoint:
                    auditevents:
                      enabled: true
                """,
              """
                management:
                  endpoint:
                    auditevents:
                      access: read-only
                """,
              s -> s.path("src/main/resources/application.yml")
            )
          )
        );
    }
}
