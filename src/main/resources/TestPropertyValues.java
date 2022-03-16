/*
 * Copyright 2021 the original author or authors.
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

package org.springframework.boot.test.util;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

public final class TestPropertyValues {
    public TestPropertyValues and(String... pairs) { return this; }

    private TestPropertyValues and(Stream<Pair> pairs) { return this; }

    public void applyTo(ConfigurableApplicationContext context) {}

    public void applyTo(ConfigurableEnvironment environment) {}

    public void applyTo(ConfigurableEnvironment environment, TestPropertyValues.Type type) {}

    public void applyTo(ConfigurableEnvironment environment, TestPropertyValues.Type type, String name) {}

    public <T> T applyToSystemProperties(Callable<T> call) { return null; }

    private <E extends Throwable> void rethrow(Throwable e) throws E {}

    private void addToSources(MutablePropertySources sources, TestPropertyValues.Type type, String name) {}

    public static TestPropertyValues of(String... pairs) { return null; }

    public static TestPropertyValues of(Iterable<String> pairs) { return null; }

    public static TestPropertyValues of(Stream<String> pairs) { return null; }

    public static TestPropertyValues empty() { return null; }

    public static class Pair {}

    public enum Type {SYSTEM_ENVIRONMENT, MAP}
}
