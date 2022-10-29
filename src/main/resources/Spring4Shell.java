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

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory;

class Fix {
    @Bean
    public WebMvcRegistrations mvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
                return new ExtendedRequestMappingHandlerAdapter();
            }
        };
    }

private static class ExtendedRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {
        @Override
        protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> methods) {
            return new ServletRequestDataBinderFactory(methods, getWebBindingInitializer()) {
                @Override
                protected ServletRequestDataBinder createBinderInstance(
                        Object target, String name, NativeWebRequest request) {

                    ServletRequestDataBinder binder = super.createBinderInstance(target, name, request);
                    String[] fields = binder.getDisallowedFields();
                    List<String> fieldList = new ArrayList<>(fields != null ? Arrays.asList(fields) : Collections.emptyList());
                    fieldList.addAll(Arrays.asList("class.*", "Class.*", "*.class.*", "*.Class.*"));
                    binder.setDisallowedFields(fieldList.toArray(new String[]{}));
                    return binder;
                }
        };
    }
}
}
