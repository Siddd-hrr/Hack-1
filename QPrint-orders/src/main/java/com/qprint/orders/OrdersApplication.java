package com.qprint.orders;

import com.qprint.orders.security.AuthTokenRelayInterceptor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class OrdersApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate(AuthTokenRelayInterceptor authTokenRelayInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(authTokenRelayInterceptor);
        return restTemplate;
    }
}
