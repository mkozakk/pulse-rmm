package dev.pulsermm.commands;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.pulsermm.commands", "dev.pulsermm.common"})
public class CommandsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommandsServiceApplication.class, args);
    }
}
