package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class MigrateActuatorMediaTypeToApiVersionTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-boot-actuator", "spring-web", "spring-core")
            .build()

    override val recipe: Recipe
        get() = MigrateActuatorMediaTypeToApiVersion()

    @Test
    fun fromConstantToEnumVal() = assertChanged(
        before = """
            import org.springframework.boot.actuate.endpoint.http.ActuatorMediaType;
            import org.springframework.http.MediaType;
            
            class T {
                private static final MediaType actuatorMediaType2 = MediaType.parseMediaType(ActuatorMediaType.V2_JSON);
                private static final MediaType actuatorMediaType3 = MediaType.parseMediaType(ActuatorMediaType.V3_JSON);
            }
        """,
        after = """
            import org.springframework.boot.actuate.endpoint.ApiVersion;
            import org.springframework.http.MediaType;
            
            class T {
                private static final MediaType actuatorMediaType2 = MediaType.asMediaType(ApiVersion.V2.getProducedMimeType());
                private static final MediaType actuatorMediaType3 = MediaType.asMediaType(ApiVersion.V3.getProducedMimeType());
            }
        """
    )
}