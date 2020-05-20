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
package org.openrewrite.spring.xml

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry
import java.nio.file.Files
import java.nio.file.Path

fun beanDefinitions(beanDefinitions: String): RewriteBeanDefinitionRegistry {
    MemoryFileSystemBuilder.newEmpty().build("project").use { fs ->
        val path: Path = fs.getPath("beans.xml")

        Files.newOutputStream(path).bufferedWriter().use {
            it.write(
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <beans xmlns="http://www.springframework.org/schema/beans"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
                        xmlns:aop="http://www.springframework.org/schema/aop"
                        xsi:schemaLocation="http://www.springframework.org/schema/beans
                        http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
                        http://www.springframework.org/schema/context
                        http://www.springframework.org/schema/context/spring-context-3.0.xsd
                        ">
                        $beanDefinitions
                    </beans>
                """.trimIndent()
            )
        }

        val beanDefinitionRegistry = RewriteBeanDefinitionRegistry()
        MigrateSpringXmlConfigurationJava.loadBeanDefinitions(listOf(path), beanDefinitionRegistry)
        return beanDefinitionRegistry
    }
}