package com.sjtudoit.majiang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude= {DataSourceAutoConfiguration.class})
public class MajiangApplication {

    public static void main(String[] args) {
        SpringApplication.run(MajiangApplication.class, args);
    }

}
