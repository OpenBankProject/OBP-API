package com.openbankproject.adapter.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration}
import springfox.documentation.builders.{ApiInfoBuilder, PathSelectors, RequestHandlerSelectors}
import springfox.documentation.service.ApiInfo
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class Swagger2Config {
  @Value("${openbankproject.adapter.swagger.enable:false}")
  var enableSwagger: Boolean = _

  @Bean
  def createRestApi() :Docket = {
     new Docket(DocumentationType.SWAGGER_2)
      .enable(enableSwagger)
      .apiInfo(apiInfo())
      .select()
      .apis(RequestHandlerSelectors.basePackage("com.openbankproject.adapter.endpoint"))
      .paths(PathSelectors.any())
      .build();
  }
  private def apiInfo() :ApiInfo = {
     new ApiInfoBuilder()
      .title("Spring Boot and Swagger2 create RESTful APIs")
      .description("more information please refer to：https://www.openbankproject.com/")
      .termsOfServiceUrl("https://github.com/OpenBankProject")
      .contact("OBP ADAPTER")
      .version("1.0")
      .build();
  }
}
