package org.gradle.rewrite.spring.xml

import com.netflix.rewrite.Parser
import org.gradle.rewrite.spring.assertRefactored
import org.junit.jupiter.api.Test
import java.io.InputStream

class AnnotationBasedBeanConfigurationTest: Parser() {
    private val repository = """
        package repositories;
        public class UserRepository {
        }
    """.trimIndent()

    @Test
    fun propertyRef() {
        val service = parse("""
            package services;
            
            import repositories.*;
            
            public class UserService {
                private UserRepository userRepository;
                
                public void setUserRepository(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }
            }
        """.trimIndent(), repository)

        val fixed = service.refactor().visit(AnnotationBasedBeanConfiguration(beanDefinitions("""
            <bean id="userService" class="services.UserService">
                <property name="userRepository" ref="userRepository" />
            </bean>
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;

            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.stereotype.Component;
            import repositories.*;

            @Component
            public class UserService {
                @Autowired
                private UserRepository userRepository;
                
                public void setUserRepository(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }
            }
        """)
    }

    @Test
    fun propertyValue() {
        val service = parse("""
            package services;
            
            public class UserService {
                private int maxUsers;
                
                public void setMaxUsers(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """.trimIndent(), repository)

        val fixed = service.refactor().visit(AnnotationBasedBeanConfiguration(beanDefinitions("""
            <bean id="userService" class="services.UserService">
                <property name="maxUsers" value="1000" />
            </bean>
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Component;

            @Component
            public class UserService {
                @Value(1000)
                private int maxUsers;
                
                public void setMaxUsers(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """)
    }

    @Test
    fun lazyInit() {
        val service = parse("""
            package services;
            
            public class UserService {
            }
        """.trimIndent(), repository)

        val fixed = service.refactor().visit(AnnotationBasedBeanConfiguration(beanDefinitions("""
            <bean id="userService" class="services.UserService" lazy-init="true" />
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;
            
            import org.springframework.context.annotation.Lazy;
            import org.springframework.stereotype.Component;

            @Component
            @Lazy
            public class UserService {
            }
        """)
    }

    @Test
    fun prototypeScope() {
        val service = parse("""
            package services;
            
            public class UserService {
            }
        """.trimIndent(), repository)

        val fixed = service.refactor().visit(AnnotationBasedBeanConfiguration(beanDefinitions("""
            <bean id="userService" class="services.UserService" scope="prototype" />
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;
            
            import org.springframework.beans.factory.config.ConfigurableBeanFactory;
            import org.springframework.context.annotation.Scope;
            import org.springframework.stereotype.Component;

            @Component
            @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
            public class UserService {
            }
        """)
    }

    @Test
    fun lifecycleMethods() {
        val service = parse("""
            package services;
            
            public class UserService {
                public void onInit() {
                }
            
                public void onDestroy() {
                }
            }
        """.trimIndent(), repository)

        val fixed = service.refactor().visit(AnnotationBasedBeanConfiguration(beanDefinitions("""
            <bean id="userService" class="services.UserService" init-method="onInit" destroy-method="onDestroy" />
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;
            
            import javax.annotation.PostConstruct;
            import javax.annotation.PreDestroy;
            import org.springframework.stereotype.Component;

            @Component
            public class UserService {
                @PostConstruct
                public void onInit() {
                }
            
                @PreDestroy
                public void onDestroy() {
                }
            }
        """)
    }

    private fun beanDefinitions(beanDefinitions: String): InputStream = """
        <?xml version="1.0" encoding="UTF-8"?>
        <beans xmlns="http://www.springframework.org/schema/beans"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
            xmlns:aop="http://www.springframework.org/schema/aop"
            xsi:schemaLocation="http://www.springframework.org/schema/beans
            http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
            http://www.springframework.org/schema/context
            http://www.springframework.org/schema/context/spring-context-3.0.xsd
            ">
            <bean id="userRepository" class="repositories.UserRepository"/>
            $beanDefinitions
        </beans>
    """.trimIndent().byteInputStream()
}
