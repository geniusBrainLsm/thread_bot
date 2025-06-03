package com.lsm.idea_print;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdeaPrintApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdeaPrintApplication.class, args);
	}

}
