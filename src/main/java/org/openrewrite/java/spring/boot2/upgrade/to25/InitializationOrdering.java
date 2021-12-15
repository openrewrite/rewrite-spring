/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2.upgrade.to25;

import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;

import java.util.function.Supplier;

@Incubating(since = "4.15.0")
public class InitializationOrdering extends Recipe {

    public InitializationOrdering() {
        doNext(new FindSpringBeansDependingOnDataSource());
        doNext(new AnnotateSpringComponentsDependingOnDataSource());
        doNext(new AnnotateBeanDefinitionsForSpringBeansDependingOnDataSource());
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
