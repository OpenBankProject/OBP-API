package com.openbankproject.adapter.config

import akka.actor.{Actor, ActorRef, ActorSystem, IndirectActorProducer}
import com.openbankproject.adapter.actor.SouthSideActor
import com.typesafe.config.ConfigFactory
import javax.annotation.{PreDestroy, Resource}
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.stereotype.Component

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.actor.{Actor, ActorSystem, Props}


@Configuration
class AkkaConfig {

  val logger = LoggerFactory.getLogger(classOf[AkkaConfig])
  val SYSTEM_NAME = "SouthSideAkkaConnector_"

  @Bean
  def actorSystem: ActorSystem = {
    val loadConfig = ConfigFactory.load
    val hostname = loadConfig.getString("akka.remote.netty.tcp.hostname")
    ActorSystem.create(SYSTEM_NAME + hostname.replace('.', '-'), loadConfig)
  }

  @PreDestroy
  private def preDestroy(): Unit = {
    val actorSystem = this.actorSystem
     logger.info("start terminate actorSystem")
    Await.ready(actorSystem.terminate, 1 minutes )
    logger.info("finished terminate actorSystem")
  }

  @Bean
  def sourthSideActorRef(adapterBuilder: ActorBuilder) = adapterBuilder.getActorRef(classOf[SouthSideActor], "akka-connector-actor")
}


@Component
class ActorBuilder {

  @Resource
  private var applicationContext:ApplicationContext = null
  @Resource
  private var actorSystem:ActorSystem = null

  def getActorRef(actorType: Class[_ <: Actor], actorName: String): ActorRef = this.getActorRef(actorType.getSimpleName, actorName)

  def getActorRef(actorBeanName: String, actorName: String): ActorRef = {
    val props = Props.create(classOf[SpringActorProducer], applicationContext, actorBeanName)
    actorSystem.actorOf(props, actorName)
  }
}

class SpringActorProducer(val applicationContext: ApplicationContext, val actorBeanName: String) extends IndirectActorProducer {

  override def produce: Actor = applicationContext.getBean(actorBeanName).asInstanceOf[Actor]

  override def actorClass: Class[_ <: Actor] = applicationContext.getType(actorBeanName).asInstanceOf[Class[_ <: Actor]]
}