package org.cottoevia;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Main entry point for the Cotto e Via catalog backend.
 *
 * Renamed from ShoppingCartApplication during the project rename to
 * cottoevia-catalog. Root package is now org.cottoevia — everything
 * under it (catalog/, SecurityConfiguration) moved accordingly.
 */
@SpringBootApplication
public class CottoeviaCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CottoeviaCatalogApplication.class, args);
    }

    /**
     * Debug aid — prints every Spring-managed bean on startup.
     * Kept from the original project; safe to remove once you no
     * longer need to inspect the bean graph during development.
     */
    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            System.out.println("Let's inspect the beans provided by Spring Boot:");
            String[] beanNames = ctx.getBeanDefinitionNames();
            java.util.Arrays.sort(beanNames);
            for (String beanName : beanNames) {
                System.out.println(beanName);
            }
        };
    }
}
