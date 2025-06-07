package org.linlinjava.litemall.goods;

//import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;

import java.util.function.Consumer;


@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.db.dao", "org.linlinjava.litemall.core", "org.linlinjava.litemall.goods"})
//@MapperScan("org.linlinjava.litemall.db.dao")
@EnableEurekaClient
//@EnableCircuitBreaker
//@EnableBinding //This way of binding is deprecated
public class LitemallGoodsManagement {
	public static void main(String[] args) {
		SpringApplication.run(LitemallGoodsManagement.class, args);
	}
}
