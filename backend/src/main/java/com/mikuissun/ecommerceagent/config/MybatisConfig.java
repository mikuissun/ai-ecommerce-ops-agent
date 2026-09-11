package com.mikuissun.ecommerceagent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.mikuissun.ecommerceagent.mapper")
public class MybatisConfig {
}
