package com.qprint.checkout;

import com.qprint.checkout.security.AuthTokenRelayInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class CheckoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(AuthTokenRelayInterceptor authTokenRelayInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(authTokenRelayInterceptor);
        return restTemplate;
    }
}
