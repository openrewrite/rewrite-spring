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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.*;

@Incubating(since = "4.15.0")
public class InitializationOrdering extends Recipe {

    public InitializationOrdering() {
        doNext(new FindClassesDependingOnDataSource());
        doNext(new AnnotateClassesDependingOnDataSource());
    }

    @Override
    public String getDisplayName() {
        return "Adds @DependsOnDatabaseInitialization to Spring Beans depending on javax.sql.DataSource.";
    }

    @Override
    public String getDescription() {
        return "Beans of certain well-known types, such as JdbcTemplate, will be ordered so that they are initialized after the database has been initialized. If you have a bean that works with the DataSource directly, annotate its class or @Bean method with @DependsOnDatabaseInitialization to ensure that it too is initialized after the database has been initialized.";
    }
}
