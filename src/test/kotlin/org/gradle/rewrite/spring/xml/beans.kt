package org.gradle.rewrite.spring.xml

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import org.gradle.rewrite.spring.xml.parse.RewriteBeanDefinitionRegistry
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