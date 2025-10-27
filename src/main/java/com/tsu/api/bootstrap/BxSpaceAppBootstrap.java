package com.tsu.api.bootstrap;

import com.tsu.api.config.ApiConfig;
import com.tsu.entry.config.EntryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackageClasses = {ApiConfig.class, EntryConfig.class})
public class BxSpaceAppBootstrap {


    public static void main(String[] args) {
        SpringApplication.run(BxSpaceAppBootstrap.class);
    }
}
