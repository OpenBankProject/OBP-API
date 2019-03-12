package com.openbankproject.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

import scala.beans.BeanProperty

@Component
@ConfigurationProperties(prefix = "adapter.akka")
class AdapterProps {

  @BeanProperty var host:String = null
  @BeanProperty var port:Int = 0
}