package org.gradle.rewrite.spring.xml

import org.gradle.rewrite.spring.assertRefactored
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.openrewrite.java.JavaParser


class MakeComponentScannableTest : JavaParser() {
    @Test
    fun propertyRef() {
        val repository = """
            package repositories;
            public class UserRepository {
            }
        """.trimIndent()

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

        val fixed = service.refactor().visit(MakeComponentScannable(beanDefinitions("""
            <bean id="userRepository" class="repositories.UserRepository"/>
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

    @ParameterizedTest
    @ValueSource(strings = ["1000", "\${maxUsers}", "#{1000}"])
    fun propertyValue(value: String) {
        val service = parse("""
            package services;
            
            public class UserService {
                private int maxUsers;
                
                public void setMaxUsers(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """.trimIndent())

        val fixed = service.refactor().visit(MakeComponentScannable(beanDefinitions("""
            <bean id="userService" class="services.UserService">
                <property name="maxUsers" value="$value" />
            </bean>
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Component;

            @Component
            public class UserService {
                @Value(${if (value.contains("\${") || value.contains("#{")) "\"$value\"" else value})
                private int maxUsers;
                
                public void setMaxUsers(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """)
    }

    @ParameterizedTest
    @EmptySource // technically Spring can't even do this for primitive types, but we don't need to unnecessarily limit argument resolution here
    @ValueSource(strings = ["name=\"maxUsers\"", "index=\"0\"", "type=\"int\""])
    fun constructorArgValue(arg: String) {
        val service = parse("""
            package services;
            
            public class UserService {
                private int maxUsers;
            
                public UserService(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """.trimIndent())

        val fixed = service.refactor().visit(MakeComponentScannable(beanDefinitions("""
            <bean id="userService" class="services.UserService">
                <constructor-arg $arg value="1000" />
            </bean>
        """))).fix().fixed

        assertRefactored(fixed, """
            package services;
            
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Component;

            @Component
            public class UserService {
                private int maxUsers;
            
                public UserService(@Value(1000) int maxUsers) {
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
        """.trimIndent())

        val fixed = service.refactor().visit(MakeComponentScannable(beanDefinitions("""
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
        """.trimIndent())

        val fixed = service.refactor().visit(MakeComponentScannable(beanDefinitions("""
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
        """.trimIndent())

        val fixed = service.refactor().visit(MakeComponentScannable(beanDefinitions("""
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
}
