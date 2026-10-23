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
package org.openrewrite.java.spring;

import org.openrewrite.internal.NameCaseConvention;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

/**
 * Converts property keys to kebab-case up to the first key that is not a Spring property name but a user-defined
 * map key, such as a logger name, a JDBC driver property or an OAuth2 client registration id. Those are bound
 * verbatim, so changing them changes what the application does.
 */
final class KebabCasePropertyKeys {

    private static final List<List<String>> MAPS_WITH_USER_DEFINED_KEYS = Stream.of(
            "info",
            "logging.group",
            "logging.level",
            "management.endpoint.health.group",
            "management.metrics.distribution.*",
            "management.metrics.enable",
            "management.metrics.export.prometheus.pushgateway.grouping-key",
            "management.metrics.tags",
            "management.observations.enable",
            "management.observations.key-values",
            "management.opentelemetry.resource-attributes",
            "management.otlp.logging.headers",
            "management.otlp.metrics.export.headers",
            "management.otlp.metrics.export.resource-attributes",
            "management.otlp.tracing.headers",
            "management.prometheus.metrics.export.pushgateway.grouping-key",
            "server.servlet.context-parameters",
            "server.servlet.jsp.init-parameters",
            "spring.flyway.placeholders",
            "spring.freemarker.settings",
            "spring.ldap.base-environment",
            "spring.liquibase.parameters",
            "spring.mvc.contentnegotiation.media-types",
            "spring.profiles.group",
            "spring.security.oauth2.authorizationserver.client",
            "spring.security.oauth2.client.provider",
            "spring.security.oauth2.client.registration",
            "spring.security.saml2.relyingparty.registration",
            "spring.ssl.bundle.jks",
            "spring.ssl.bundle.pem",
            "spring.cloud.openfeign.client.config",
            "spring.cloud.stream.binders",
            "spring.cloud.stream.bindings",
            "spring.cloud.stream.kafka.bindings",
            "spring.cloud.stream.rabbit.bindings",
            "feign.client.config",
            "resilience4j.*.configs",
            "resilience4j.*.instances"
    ).map(name -> asList(name.split("\\."))).collect(toList());

    private KebabCasePropertyKeys() {
    }

    static String toKebabCase(String enclosingKeys, String key) {
        List<String> path = new ArrayList<>();
        if (!enclosingKeys.isEmpty()) {
            if (enclosingKeys.contains("[")) {
                return key;
            }
            for (String segment : enclosingKeys.split("\\.")) {
                if (hasUserDefinedKeys(path)) {
                    return key;
                }
                path.add(NameCaseConvention.LOWER_HYPHEN.format(segment));
            }
        }

        int bracket = key.indexOf('[');
        String dotted = bracket < 0 ? key : key.substring(0, bracket);
        String[] segments = dotted.split("\\.", -1);
        StringBuilder kebab = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (hasUserDefinedKeys(path)) {
                kebab.append(String.join(".", asList(segments).subList(i, segments.length)));
                break;
            }
            String segment = NameCaseConvention.LOWER_HYPHEN.format(segments[i]);
            kebab.append(segment);
            if (i < segments.length - 1) {
                kebab.append('.');
            }
            path.add(segment);
        }
        return bracket < 0 ? kebab.toString() : kebab + key.substring(bracket);
    }

    private static boolean hasUserDefinedKeys(List<String> path) {
        if (path.isEmpty()) {
            return false;
        }
        if (path.get(path.size() - 1).endsWith("properties")) {
            return true;
        }
        for (List<String> map : MAPS_WITH_USER_DEFINED_KEYS) {
            if (matches(map, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(List<String> map, List<String> path) {
        if (map.size() != path.size()) {
            return false;
        }
        for (int i = 0; i < map.size(); i++) {
            if (!"*".equals(map.get(i)) && !map.get(i).equals(path.get(i))) {
                return false;
            }
        }
        return true;
    }
}
