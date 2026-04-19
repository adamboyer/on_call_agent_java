package com.example.oncallagent;

import com.example.oncallagent.config.SlackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SlackProperties.class)
public class OncallAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OncallAgentApplication.class, args);
    }
}