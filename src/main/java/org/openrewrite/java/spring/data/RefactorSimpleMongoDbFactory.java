/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.data;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.mongodb.MongoClientURI;
import org.openrewrite.java.template.RecipeDescriptor;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDbFactory;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;

@RecipeDescriptor(
        name = "Use `new SimpleMongoClientDbFactory(String)`",
        description = "Replace usage of deprecated `new SimpleMongoDbFactory(new MongoClientURI(String))` with `new SimpleMongoClientDbFactory(String)`.")
@SuppressWarnings("deprecation")
public class RefactorSimpleMongoDbFactory {

    @BeforeTemplate
    public MongoDbFactory before(String uri) {
        return new SimpleMongoDbFactory(new MongoClientURI(uri));
    }

    @AfterTemplate
    public MongoDbFactory after(String uri) {
        return new SimpleMongoClientDbFactory(uri);
    }
}
