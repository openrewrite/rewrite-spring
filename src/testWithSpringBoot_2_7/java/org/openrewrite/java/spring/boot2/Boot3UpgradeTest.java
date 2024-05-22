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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class Boot3UpgradeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2")
          )
          .parser(JavaParser.fromJavaVersion()
            .classpath( "spring-context", "spring-data-jpa", "spring-web",
              "spring-boot", "spring-core", "persistence-api", "validation-api", "xml.bind-api"));
    }

    @Test
    void xmlBindMissing() {

        rewriteRun(
          mavenProject("project",
            //language=xml
            pomXml(
              """
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.springframework.samples</groupId>
  <artifactId>spring-petclinic</artifactId>
  <version>2.7.3</version>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.3</version>
  </parent>
  <name>petclinic</name>

  <properties>

    <!-- Generic properties -->
    <java.version>1.8</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

    <!-- Web dependencies -->
    <webjars-bootstrap.version>5.1.3</webjars-bootstrap.version>
    <webjars-font-awesome.version>4.7.0</webjars-font-awesome.version>

    <jacoco.version>0.8.7</jacoco.version>
    <nohttp-checkstyle.version>0.0.10</nohttp-checkstyle.version>
    <spring-format.version>0.0.31</spring-format.version>

  </properties>

  <dependencies>
    <!-- Spring and Spring Boot dependencies -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Databases - Uses H2 by default -->
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>mysql</groupId>
      <artifactId>mysql-connector-java</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- caching -->
    <dependency>
      <groupId>javax.cache</groupId>
      <artifactId>cache-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.ehcache</groupId>
      <artifactId>ehcache</artifactId>
    </dependency>

    <!-- webjars -->
    <dependency>
      <groupId>org.webjars.npm</groupId>
      <artifactId>bootstrap</artifactId>
      <version>${webjars-bootstrap.version}</version>
    </dependency>
    <dependency>
      <groupId>org.webjars.npm</groupId>
      <artifactId>font-awesome</artifactId>
      <version>${webjars-font-awesome.version}</version>
    </dependency>
    <!-- end of webjars -->

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-devtools</artifactId>
      <optional>true</optional>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-checkstyle-plugin</artifactId>
        <version>3.1.2</version>
        <dependencies>
          <dependency>
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>8.45.1</version>
          </dependency>
          <dependency>
            <groupId>io.spring.nohttp</groupId>
            <artifactId>nohttp-checkstyle</artifactId>
            <version>${nohttp-checkstyle.version}</version>
          </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>nohttp-checkstyle-validation</id>
            <phase>validate</phase>
            <configuration>
              <configLocation>src/checkstyle/nohttp-checkstyle.xml</configLocation>
              <suppressionsLocation>src/checkstyle/nohttp-checkstyle-suppressions.xml</suppressionsLocation>
              <encoding>UTF-8</encoding>
              <sourceDirectories>${basedir}</sourceDirectories>
              <includes>**/*</includes>
              <excludes>**/.git/**/*,**/.idea/**/*,**/target/**/,**/.flattened-pom.xml,**/*.class</excludes>
            </configuration>
            <goals>
              <goal>check</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution>
            <!-- Spring Boot Actuator displays build-related information
              if a META-INF/build-info.properties file is present -->
            <goals>
              <goal>build-info</goal>
            </goals>
            <configuration>
              <additionalProperties>
                <encoding.source>${project.build.sourceEncoding}</encoding.source>
                <encoding.reporting>${project.reporting.outputEncoding}</encoding.reporting>
                <java.source>${maven.compiler.source}</java.source>
                <java.target>${maven.compiler.target}</java.target>
              </additionalProperties>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>${jacoco.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>prepare-agent</goal>
            </goals>
          </execution>
          <execution>
            <id>report</id>
            <phase>prepare-package</phase>
            <goals>
              <goal>report</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

      <!-- Spring Boot Actuator displays build-related information if a git.properties
        file is present at the classpath -->
      <plugin>
        <groupId>pl.project13.maven</groupId>
        <artifactId>git-commit-id-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>revision</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <verbose>true</verbose>
          <dateFormat>yyyy-MM-dd'T'HH:mm:ssZ</dateFormat>
          <generateGitPropertiesFile>true</generateGitPropertiesFile>
          <generateGitPropertiesFilename>${project.build.outputDirectory}/git.properties
          </generateGitPropertiesFilename>
          <failOnNoGitDirectory>false</failOnNoGitDirectory>
          <failOnUnableToExtractRepoInfo>false</failOnUnableToExtractRepoInfo>
        </configuration>
      </plugin>

    </plugins>
  </build>

  <repositories>
    <repository>
      <id>spring-snapshots</id>
      <name>Spring Snapshots</name>
      <url>https://repo.spring.io/snapshot</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
    <repository>
      <id>spring-milestones</id>
      <name>Spring Milestones</name>
      <url>https://repo.spring.io/milestone</url>
      <snapshots>
        <enabled>false</enabled>
      </snapshots>
    </repository>
  </repositories>

  <pluginRepositories>
    <pluginRepository>
      <id>spring-snapshots</id>
      <name>Spring Snapshots</name>
      <url>https://repo.spring.io/snapshot</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </pluginRepository>
    <pluginRepository>
      <id>spring-milestones</id>
      <name>Spring Milestones</name>
      <url>https://repo.spring.io/milestone</url>
      <snapshots>
        <enabled>false</enabled>
      </snapshots>
    </pluginRepository>
  </pluginRepositories>

</project>
                """,
              """
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.springframework.samples</groupId>
  <artifactId>spring-petclinic</artifactId>
  <version>2.7.3</version>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.2</version>
  </parent>
  <name>petclinic</name>

  <properties>

    <!-- Generic properties -->
    <java.version>17</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

    <!-- Web dependencies -->
    <webjars-bootstrap.version>5.1.3</webjars-bootstrap.version>
    <webjars-font-awesome.version>4.7.0</webjars-font-awesome.version>

    <jacoco.version>0.8.8</jacoco.version>
    <nohttp-checkstyle.version>0.0.10</nohttp-checkstyle.version>
    <spring-format.version>0.0.31</spring-format.version>

  </properties>

  <dependencies>
    <!-- Spring and Spring Boot dependencies -->
    <dependency>
      <groupId>jakarta.xml.bind</groupId>
      <artifactId>jakarta.xml.bind-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>

    <!-- Databases - Uses H2 by default -->
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.glassfish.jaxb</groupId>
      <artifactId>jaxb-runtime</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- caching -->
    <dependency>
      <groupId>javax.cache</groupId>
      <artifactId>cache-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.ehcache</groupId>
      <artifactId>ehcache</artifactId>
      <classifier>jakarta</classifier>
    </dependency>

    <!-- webjars -->
    <dependency>
      <groupId>org.webjars.npm</groupId>
      <artifactId>bootstrap</artifactId>
      <version>${webjars-bootstrap.version}</version>
    </dependency>
    <dependency>
      <groupId>org.webjars.npm</groupId>
      <artifactId>font-awesome</artifactId>
      <version>${webjars-font-awesome.version}</version>
    </dependency>
    <!-- end of webjars -->

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-devtools</artifactId>
      <optional>true</optional>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-checkstyle-plugin</artifactId>
        <version>3.3.1</version>
        <dependencies>
          <dependency>
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>8.45.1</version>
          </dependency>
          <dependency>
            <groupId>io.spring.nohttp</groupId>
            <artifactId>nohttp-checkstyle</artifactId>
            <version>${nohttp-checkstyle.version}</version>
          </dependency>
        </dependencies>
        <executions>
          <execution>
            <id>nohttp-checkstyle-validation</id>
            <phase>validate</phase>
            <configuration>
              <configLocation>src/checkstyle/nohttp-checkstyle.xml</configLocation>
              <suppressionsLocation>src/checkstyle/nohttp-checkstyle-suppressions.xml</suppressionsLocation>
              <encoding>UTF-8</encoding>
              <sourceDirectories>${basedir}</sourceDirectories>
              <includes>**/*</includes>
              <excludes>**/.git/**/*,**/.idea/**/*,**/target/**/,**/.flattened-pom.xml,**/*.class</excludes>
            </configuration>
            <goals>
              <goal>check</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <executions>
          <execution>
            <!-- Spring Boot Actuator displays build-related information
              if a META-INF/build-info.properties file is present -->
            <goals>
              <goal>build-info</goal>
            </goals>
            <configuration>
              <additionalProperties>
                <encoding.source>${project.build.sourceEncoding}</encoding.source>
                <encoding.reporting>${project.reporting.outputEncoding}</encoding.reporting>
                <java.source>${maven.compiler.source}</java.source>
                <java.target>${maven.compiler.target}</java.target>
              </additionalProperties>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>${jacoco.version}</version>
        <executions>
          <execution>
            <goals>
              <goal>prepare-agent</goal>
            </goals>
          </execution>
          <execution>
            <id>report</id>
            <phase>prepare-package</phase>
            <goals>
              <goal>report</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

      <!-- Spring Boot Actuator displays build-related information if a git.properties
        file is present at the classpath -->
      <plugin>
        <groupId>pl.project13.maven</groupId>
        <artifactId>git-commit-id-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>revision</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <verbose>true</verbose>
          <dateFormat>yyyy-MM-dd'T'HH:mm:ssZ</dateFormat>
          <generateGitPropertiesFile>true</generateGitPropertiesFile>
          <generateGitPropertiesFilename>${project.build.outputDirectory}/git.properties
          </generateGitPropertiesFilename>
          <failOnNoGitDirectory>false</failOnNoGitDirectory>
          <failOnUnableToExtractRepoInfo>false</failOnUnableToExtractRepoInfo>
        </configuration>
      </plugin>

    </plugins>
  </build>

  <repositories>
    <repository>
      <id>spring-snapshots</id>
      <name>Spring Snapshots</name>
      <url>https://repo.spring.io/snapshot</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
    <repository>
      <id>spring-milestones</id>
      <name>Spring Milestones</name>
      <url>https://repo.spring.io/milestone</url>
      <snapshots>
        <enabled>false</enabled>
      </snapshots>
    </repository>
  </repositories>

  <pluginRepositories>
    <pluginRepository>
      <id>spring-snapshots</id>
      <name>Spring Snapshots</name>
      <url>https://repo.spring.io/snapshot</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </pluginRepository>
    <pluginRepository>
      <id>spring-milestones</id>
      <name>Spring Milestones</name>
      <url>https://repo.spring.io/milestone</url>
      <snapshots>
        <enabled>false</enabled>
      </snapshots>
    </pluginRepository>
  </pluginRepositories>

</project>
                """),
            srcMainJava(
              java(
                """
        package org.springframework.samples.petclinic.vet;
        
        import java.io.Serializable;
        
        import javax.persistence.Column;
        import javax.persistence.Entity;
        import javax.persistence.GeneratedValue;
        import javax.persistence.GenerationType;
        import javax.persistence.Id;
        import javax.persistence.Table;
        
        @Entity
        @Table(name = "specialties")
        public class Specialty implements Serializable {
            
            @Id
            @GeneratedValue(strategy = GenerationType.IDENTITY)
            private Integer id;
        
            @Column(name = "name")
            private String name;
            
            public Integer getId() {
                return id;
            }
        
            public void setId(Integer id) {
                this.id = id;
            }
        
            public boolean isNew() {
                return this.id == null;
            }
        
        
            public String getName() {
                return this.name;
            }
        
            public void setName(String name) {
                this.name = name;
            }
        
        
        }
                      """,
                """
    package org.springframework.samples.petclinic.vet;
    
    import java.io.Serializable;
    
    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.GenerationType;
    import jakarta.persistence.Id;
    import jakarta.persistence.Table;
    
    @Entity
    @Table(name = "specialties")
    public class Specialty implements Serializable {
        
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;
    
        @Column(name = "name")
        private String name;
        
        public Integer getId() {
            return id;
        }
    
        public void setId(Integer id) {
            this.id = id;
        }
    
        public boolean isNew() {
            return this.id == null;
        }
    
    
        public String getName() {
            return this.name;
        }
    
        public void setName(String name) {
            this.name = name;
        }
    
    
    }
                  """
              ),
              java(
                """
    package org.springframework.samples.petclinic.vet;
    
    import java.io.Serializable;
    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;
    
    import javax.persistence.Column;
    import javax.persistence.Entity;
    import javax.persistence.FetchType;
    import javax.persistence.GeneratedValue;
    import javax.persistence.GenerationType;
    import javax.persistence.Id;
    import javax.persistence.JoinColumn;
    import javax.persistence.JoinTable;
    import javax.persistence.ManyToMany;
    import javax.persistence.Table;
    import javax.validation.constraints.NotEmpty;
    import javax.xml.bind.annotation.XmlElement;
    
    @Entity
    @Table(name = "vets")
    public class Vet implements Serializable {
        
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;
    
        @Column(name = "name")
        private String name;
        
        @Column(name = "first_name")
        @NotEmpty
        private String firstName;
    
        @Column(name = "last_name")
        @NotEmpty
        private String lastName;
    
        @ManyToMany(fetch = FetchType.EAGER)
        @JoinTable(name = "vet_specialties", joinColumns = @JoinColumn(name = "vet_id"),
                inverseJoinColumns = @JoinColumn(name = "specialty_id"))
        private Set<Specialty> specialties;
        
        public Integer getId() {
            return id;
        }
    
        public void setId(Integer id) {
            this.id = id;
        }
    
        public boolean isNew() {
            return this.id == null;
        }
    
    
        public String getName() {
            return this.name;
        }
    
        public void setName(String name) {
            this.name = name;
        }
    
    
        protected Set<Specialty> getSpecialtiesInternal() {
            if (this.specialties == null) {
                this.specialties = new HashSet<>();
            }
            return this.specialties;
        }
    
        protected void setSpecialtiesInternal(Set<Specialty> specialties) {
            this.specialties = specialties;
        }
    
        @XmlElement
        public List<Specialty> getSpecialties() {
            List<Specialty> sortedSpecs = new ArrayList<>(getSpecialtiesInternal());
            return Collections.unmodifiableList(sortedSpecs);
        }
    
        public int getNrOfSpecialties() {
            return getSpecialtiesInternal().size();
        }
    
        public void addSpecialty(Specialty specialty) {
            getSpecialtiesInternal().add(specialty);
        }
    
        public String getFirstName() {
            return this.firstName;
        }
    
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    
        public String getLastName() {
            return this.lastName;
        }
    
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
                """,
                """
    package org.springframework.samples.petclinic.vet;
    
    import java.io.Serializable;
    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;
    
    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.FetchType;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.GenerationType;
    import jakarta.persistence.Id;
    import jakarta.persistence.JoinColumn;
    import jakarta.persistence.JoinTable;
    import jakarta.persistence.ManyToMany;
    import jakarta.persistence.Table;
    import jakarta.validation.constraints.NotEmpty;
    import jakarta.xml.bind.annotation.XmlElement;
    
    @Entity
    @Table(name = "vets")
    public class Vet implements Serializable {
        
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;
    
        @Column(name = "name")
        private String name;
        
        @Column(name = "first_name")
        @NotEmpty
        private String firstName;
    
        @Column(name = "last_name")
        @NotEmpty
        private String lastName;
    
        @ManyToMany(fetch = FetchType.EAGER)
        @JoinTable(name = "vet_specialties", joinColumns = @JoinColumn(name = "vet_id"),
                inverseJoinColumns = @JoinColumn(name = "specialty_id"))
        private Set<Specialty> specialties;
        
        public Integer getId() {
            return id;
        }
    
        public void setId(Integer id) {
            this.id = id;
        }
    
        public boolean isNew() {
            return this.id == null;
        }
    
    
        public String getName() {
            return this.name;
        }
    
        public void setName(String name) {
            this.name = name;
        }
    
    
        protected Set<Specialty> getSpecialtiesInternal() {
            if (this.specialties == null) {
                this.specialties = new HashSet<>();
            }
            return this.specialties;
        }
    
        protected void setSpecialtiesInternal(Set<Specialty> specialties) {
            this.specialties = specialties;
        }
    
        @XmlElement
        public List<Specialty> getSpecialties() {
            List<Specialty> sortedSpecs = new ArrayList<>(getSpecialtiesInternal());
            return Collections.unmodifiableList(sortedSpecs);
        }
    
        public int getNrOfSpecialties() {
            return getSpecialtiesInternal().size();
        }
    
        public void addSpecialty(Specialty specialty) {
            getSpecialtiesInternal().add(specialty);
        }
    
        public String getFirstName() {
            return this.firstName;
        }
    
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    
        public String getLastName() {
            return this.lastName;
        }
    
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }
                """
              )
            )//language=properties
          )
        );

    }

}


