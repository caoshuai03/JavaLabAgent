package com.cs.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JavaLabAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaLabAgentApplication.class, args);
        System.out.println("\n      _                           _               _          _                             _   \n" +
                "     | |   __ _  __   __   __ _  | |       __ _  | |__      / \\      __ _    ___   _ __   | |_ \n" +
                "  _  | |  / _` | \\ \\ / /  / _` | | |      / _` | | '_ \\    / _ \\    / _` |  / _ \\ | '_ \\  | __|\n" +
                " | |_| | | (_| |  \\ V /  | (_| | | |___  | (_| | | |_) |  / ___ \\  | (_| | |  __/ | | | | | |_ \n" +
                "  \\___/   \\__,_|   \\_/    \\__,_| |_____|  \\__,_| |_.__/  /_/   \\_\\  \\__, |  \\___| |_| |_|  \\__|\n" +
                "                                                                    |___/                      \n");
    }

}
