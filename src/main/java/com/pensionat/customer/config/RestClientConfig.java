package com.pensionat.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient restClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());

        return builder
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String token = incomingAuthHeader();
                    if (token != null) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, token);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private String incomingAuthHeader() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
