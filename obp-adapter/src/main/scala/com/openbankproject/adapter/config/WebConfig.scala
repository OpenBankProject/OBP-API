package com.openbankproject.adapter.config

import java.util

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig extends WebMvcConfigurer{
  override def configureMessageConverters(converters: util.List[HttpMessageConverter[_]]): Unit = {
    super.configureMessageConverters(converters)
    // make jackson can deserialize scala class and case class
    converters.stream()
      .filter(_.isInstanceOf[AbstractJackson2HttpMessageConverter])
      .forEach(it=> it.asInstanceOf[AbstractJackson2HttpMessageConverter].getObjectMapper.registerModule(DefaultScalaModule))
  }
}
