package com.openbankproject.adapter

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.openfeign.EnableFeignClients

@SpringBootApplication
@EnableFeignClients
class ApplicationMain

object ApplicationMain extends App{
  SpringApplication.run(classOf[ApplicationMain], args:_*)
}

/**
  * run as dev profile
  */
private object ApplicationDevMain extends App {
  System.setProperty("spring.profiles.active", "dev")
  SpringApplication.run(classOf[ApplicationMain], args:_*)
}
