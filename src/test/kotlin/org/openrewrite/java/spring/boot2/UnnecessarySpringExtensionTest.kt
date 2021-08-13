package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class UnnecessarySpringExtensionTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-context", "spring-test", "spring-boot-test", "junit-jupiter-api", "spring-boot-test-autoconfigure")
            .build()

    override val recipe: Recipe
        get() = UnnecessarySpringExtension()

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/43")
    @Test
    fun removeSpringExtensionIfSpringBootTestIsPresent() = assertChanged(
        before = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import org.springframework.boot.test.context.SpringBootTest;
            import org.springframework.test.context.junit.jupiter.SpringExtension;
            
            @SpringBootTest
            @ExtendWith(SpringExtension.class)
            class Test {
            }
        """,
        after = """
            import org.springframework.boot.test.context.SpringBootTest;
            
            @SpringBootTest
            class Test {
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/72")
    @ParameterizedTest
    @ValueSource(strings = [
        "org.springframework.boot.test.autoconfigure.jdbc.JdbcTest",
        "org.springframework.boot.test.autoconfigure.web.client.RestClientTest",
        "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
        "org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest",
        "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
        "org.springframework.boot.test.autoconfigure.webservices.client.WebServiceClientTest",
        "org.springframework.boot.test.autoconfigure.jooq.JooqTest",
        "org.springframework.boot.test.autoconfigure.json.JsonTest",
        "org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest",
        "org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest",
        "org.springframework.boot.test.autoconfigure.data.ldap.DataLdapTest",
        "org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest",
        "org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest",
        "org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest",
        "org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest"])
    fun removeSpringExtensionForTestSliceAnnotations(annotationName: String) =  assertChanged(
        before = """
            import org.junit.jupiter.api.extension.ExtendWith;
            import ${annotationName};
            import org.springframework.test.context.junit.jupiter.SpringExtension;
            
            @${annotationName.substring(annotationName.lastIndexOf('.') + 1)}
            @ExtendWith(SpringExtension.class)
            class Test {
            }
        """,
            after = """
            import ${annotationName};
            
            @${annotationName.substring(annotationName.lastIndexOf('.') + 1)}
            class Test {
            }
        """
    )
}
