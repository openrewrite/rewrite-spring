/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.data;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A MongoDB-persisted value category that needs an explicit representation configured under Spring
 * Data MongoDB 5, and the Spring configuration property that supplies it.
 */
public enum MongoValueRepresentationKind {
    // Spring Data MongoDB 4 configures JAVA_LEGACY when no UUID representation is supplied.
    // Mirrors org.bson.UuidRepresentation, excluding UNSPECIFIED (not a valid choice).
    UUID("spring.mongodb.representation.uuid",
            "`spring.mongodb.representation.uuid` needs a concrete UUID representation matching the existing BSON data.",
            // Renamed by UpgradeSpringBoot_4_0 (spring-boot-40-properties.yml) from spring.data.mongodb.uuid-representation;
            // a project not yet through that migration may still carry the old key, and its value (if concrete) is just
            // as valid a signal that UUID representation is already configured.
            "spring.data.mongodb.uuid-representation",
            "java-legacy",
            "standard", "java-legacy", "c-sharp-legacy", "python-legacy"),
    // Spring Data MongoDB 4 configures STRING when no BigDecimal/BigInteger representation is supplied.
    // Mirrors Spring Data MongoDB's BigDecimalRepresentation, excluding UNSPECIFIED.
    BIG_NUMBER("spring.data.mongodb.representation.big-decimal",
            "`spring.data.mongodb.representation.big-decimal` needs a concrete big-number representation matching the existing BSON data.",
            null,
            "string",
            "string", "decimal128");

    public final String configurationProperty;
    public final String invalidPropertyMessage;
    final @Nullable String legacyConfigurationProperty;
    final String springData4DefaultValue;
    private final Set<String> supportedValues;

    MongoValueRepresentationKind(String configurationProperty, String invalidPropertyMessage,
              @Nullable String legacyConfigurationProperty, String springData4DefaultValue, String... supportedValues) {
        this.configurationProperty = configurationProperty;
        this.invalidPropertyMessage = invalidPropertyMessage;
        this.legacyConfigurationProperty = legacyConfigurationProperty;
        this.springData4DefaultValue = springData4DefaultValue;
        this.supportedValues = new HashSet<>();
        for (String supportedValue : supportedValues) {
            this.supportedValues.add(normalize(supportedValue));
        }
    }

    public boolean isConfiguredValue(@Nullable String value) {
        if (value == null) {
            return false;
        }
        String candidate = value.trim();
        if (candidate.startsWith("${") && candidate.endsWith("}")) {
            return true;
        }
        return supportedValues.contains(normalize(candidate));
    }

    private static String normalize(String value) {
        return value.replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }
}
