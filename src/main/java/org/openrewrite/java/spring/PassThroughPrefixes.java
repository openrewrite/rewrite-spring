/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Boot pass-through property prefixes. Properties under these prefixes
 * are forwarded verbatim to vendor-specific libraries and should not have their
 * keys converted to kebab-case.
 */
final class PassThroughPrefixes {

    private PassThroughPrefixes() {
    }

    private static final List<String> PREFIXES = Arrays.asList(
            "spring.jpa.properties.",
            "spring.kafka.properties.",
            "spring.kafka.consumer.properties.",
            "spring.kafka.producer.properties.",
            "spring.kafka.admin.properties.",
            "spring.kafka.streams.properties.",
            "spring.quartz.properties.",
            "spring.flyway.properties.",
            "spring.liquibase.properties."
    );

    static boolean isUnderPassThroughPrefix(String propertyPath) {
        for (String prefix : PREFIXES) {
            if (propertyPath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
