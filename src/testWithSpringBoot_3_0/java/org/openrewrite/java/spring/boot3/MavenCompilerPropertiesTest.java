package org.openrewrite.java.spring.boot3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.other;
import static org.openrewrite.test.SourceSpecs.text;

public class MavenCompilerPropertiesTest implements RewriteTest {

	@Test
	void upgradeGradleWrapperAndPlugins() {
		rewriteRun(spec -> spec.recipe(Environment.builder()
			.scanRuntimeClasspath("org.openrewrite.java.spring")
			.build()
			.activateRecipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1")
		  ),
		  pomXml("""
			<?xml version="1.0" encoding="UTF-8"?>
			<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>org.springframework.samples</groupId>
			  <artifactId>test-project</artifactId>
			  <version>2.7.3</version>
			   
			  <parent>
			    <groupId>org.springframework.boot</groupId>
			    <artifactId>spring-boot-starter-parent</artifactId>
			    <version>2.7.3</version>
			  </parent>
			  <name>petclinic</name>
			   
			  <properties>
			    <compiler-version>${maven.compiler.source}</compiler-version>
			    <target-version>${maven.compiler.target}</target-version>
			  </properties>
			   
			</project>
			""", """
			<?xml version="1.0" encoding="UTF-8"?>
			<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>org.springframework.samples</groupId>
			  <artifactId>test-project</artifactId>
			  <version>2.7.3</version>
			   
			  <parent>
			    <groupId>org.springframework.boot</groupId>
			    <artifactId>spring-boot-starter-parent</artifactId>
			    <version>2.7.3</version>
			  </parent>
			  <name>petclinic</name>
			   
			  <properties>
			    <compiler-version>${java.version}</compiler-version>
			    <target-version>${maven.compiler.release}</target-version>
			  </properties>
			   
			</project>
			""")
		);
	}

}
