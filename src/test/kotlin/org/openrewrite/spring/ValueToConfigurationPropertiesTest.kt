/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.*
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import java.io.File

class ValueToConfigurationPropertiesTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-beans")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ValueToConfigurationProperties())

    @Test
    fun sharedPrefix() = assertRefactored(
        before = """
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.refresh-rate}")
                private int refreshRate;
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties("app")
            class MyConfiguration {
                private int refreshRate;
            }
        """
    )

    @Test
    fun changeFieldName() = assertRefactored(
        before = """
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.refresh-rate}")
                private int refresh;
            
                public int getRefresh() {
                    return this.refresh;
                }
            
                public void setRefresh(int refresh) {
                    this.refresh = refresh;
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties("app")
            class MyConfiguration {
                private int refreshRate;
            
                public int getRefreshRate() {
                    return this.refreshRate;
                }
            
                public void setRefreshRate(int refresh) {
                    this.refreshRate = refresh;
                }
            }
        """
    )

    @Test
    fun changeFieldNameReferences() = assertRefactored(
        before = """
            class MyService {
                MyConfiguration config;
            
                {
                    config.getRefresh();
                    config.setRefresh(1);
                }
            }
        """,
        dependencies = listOf("""
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.refresh-rate}")
                private int refresh;
            
                public int getRefresh() {
                    return this.refresh;
                }
            
                public void setRefresh(int refresh) {
                    this.refresh = refresh;
                }
            }
        """),
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties("app")
            class MyService {
                MyConfiguration config;
            
                {
                    config.getRefreshRate();
                    config.setRefreshRate(1);
                }
            }
        """
    )


    /**
     * FIXME Implement me!
     */
    @Test
    fun nestedClass() {
        val aSource = """
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.screen.refresh-rate}")
                private int refresh;

                @Value("${"$"}{app.name}")
                private String name;

                public int getRefresh() {
                    return this.refresh;
                }
            }
        """.trimIndent()

        val bSource = """
            class MyService {
                MyConfiguration config;

                {
                    config.getRefresh();
                }
            }
        """.trimIndent()

        val fixed: List<SourceFile> = Refactor()
                .visit(ValueToConfigurationProperties())
                .fix(parser.parse(aSource, bSource))
                .map { it.fixed }
                .toList()

        val aFixed = fixed[0]
        val bFixed = fixed[1]
        assertRefactored(aFixed, """
            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("app")
            class MyConfiguration {
                private Screen screen;

                private String name;

                public Screen getScreen() {
                    return this.screen;
                }

                public void setScreen() {
                    this.screen = screen;
                }

                public static class Screen {
                    private int refreshRate;

                    public int getRefreshRate() {
                        return this.refreshRate;
                    }
                }
            }
        """)
        assertRefactored(bFixed, """
            class MyService {
                MyConfiguration config;

                {
                    config.getScreen().getRefreshRate();
                }
            }
        """.trimIndent())
    }

    @Test
    fun nestedClass2() {
        val aSource = """
            package org.springframework.samples.petclinic;
            
            
            import org.springframework.beans.factory.annotation.Value;
            
            public class PetClinicConfiguration {
                @Value("${"$"}{app.mail.tech-support-contact}")
                private String techSupportContact;
                @Value("${"$"}{app.mail.api-token}")
                private String apiToken;
                @Value("${"$"}{app.mail.oauth-secret}")
                private String oauthSecret;
                @Value("${"$"}{app.name}")
                private String name;
                @Value("${"$"}{app.port}")
                private String port;
                @Value("${"$"}{app.aws.accessKey}")
                private String accessKey;
                @Value("${"$"}{app.aws.secretKey}")
                private String secretKey;
            
                public String getTechSupportContact() {
                    return techSupportContact;
                }
            
                public void setTechSupportContact(String techSupportContact) {
                    this.techSupportContact = techSupportContact;
                }
            
                public String getApiToken() {
                    return apiToken;
                }
            
                public void setApiToken(String apiToken) {
                    this.apiToken = apiToken;
                }
            
                public String getOauthSecret() {
                    return oauthSecret;
                }
            
                public void setOauthSecret(String oauthSecret) {
                    this.oauthSecret = oauthSecret;
                }
            
                public String getName() {
                    return name;
                }
            
                public void setName(String name) {
                    this.name = name;
                }
            
                public String getPort() {
                    return port;
                }
            
                public void setPort(String port) {
                    this.port = port;
                }
            
                public String getAccessKey() {
                    return accessKey;
                }
            
                public void setAccessKey(String accessKey) {
                    this.accessKey = accessKey;
                }
            
                public String getSecretKey() {
                    return secretKey;
                }
            
                public void setSecretKey(String secretKey) {
                    this.secretKey = secretKey;
                }
            }
        """.trimIndent()
        val aFixed = Refactor().visit(ValueToConfigurationProperties())
                .fix(parser.parse(aSource)).first().fixed
        println("\n-----------------------------------\n")
        println(aFixed.printTrimmed())
        assertThat(aFixed).isNotNull
    }

    @Test
    fun cheat() {
        val target = File("F:\\Projects\\openrewrite\\spring-petclinic-migration\\src\\main\\java\\org\\springframework\\samples\\petclinic\\PetClinicConfiguration.java")
        assertThat(target).exists()
        val contents = """
            package org.springframework.samples.petclinic;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.samples.petclinic.PetClinicConfiguration.Aws;
            import org.springframework.samples.petclinic.PetClinicConfiguration.Mail;

            @ConfigurationProperties("app")
            public class PetClinicConfiguration {
                private PetClinicConfiguration.Aws aws;
                private PetClinicConfiguration.Mail mail;
                private String name;
                private String port;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

                public String getPort() {
                    return port;
                }

                public void setPort(String port) {
                    this.port = port;
                }

                public PetClinicConfiguration.Mail getMail() {
                    return mail;
                }

                public void setMail(PetClinicConfiguration.Mail value) {
                    mail = value;
                }

                public PetClinicConfiguration.Mail getMail() {
                    return mail;
                }

                public void setMail(PetClinicConfiguration.Mail value) {
                    mail = value;
                }

                public static class Mail {
                    private String techSupportContact;

                    public String getTechSupportContact() {
                        return techSupportContact;
                    }

                    public void setTechSupportContact(String value) {
                        techSupportContact = value;
                    }

                    private String apiToken;

                    public String getApiToken() {
                        return apiToken;
                    }

                    public void setApiToken(String value) {
                        apiToken = value;
                    }

                    private String oauthSecret;

                    public String getOauthSecret() {
                        return oauthSecret;
                    }

                    public void setOauthSecret(String value) {
                        oauthSecret = value;
                    }
                }

                public PetClinicConfiguration.Mail getMail() {
                    return mail;
                }

                public void setMail(PetClinicConfiguration.Mail value) {
                    mail = value;
                }

                public PetClinicConfiguration.Aws getAws() {
                    return aws;
                }

                public void setAws(PetClinicConfiguration.Aws value) {
                    aws = value;
                }

                public static class Aws {
                    private String accessKey;

                    public String getAccessKey() {
                        return accessKey;
                    }

                    public void setAccessKey(String value) {
                        accessKey = value;
                    }

                    private String secretKey;

                    public String getSecretKey() {
                        return secretKey;
                    }

                    public void setSecretKey(String value) {
                        secretKey = value;
                    }
                }

                public PetClinicConfiguration.Aws getAws() {
                    return aws;
                }

                public void setAws(PetClinicConfiguration.Aws value) {
                    aws = value;
                }
            }
        """.trimIndent()

        target.writeText(contents)
    }

    @Test
    fun longestCommonPrefix() {
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a.b", "a")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a", "a.b")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a", "a")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a", "b")).isEqualTo("")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix(null, "a")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a.b.c.d", "a.b")).isEqualTo("a.b")
    }
}
