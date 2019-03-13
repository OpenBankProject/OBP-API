package code.bankconnectors.akka.actor

import code.api.util.APIUtil
import code.util.Helper


object AkkaConnectorActorConfig {

  val remoteHostname = APIUtil.getPropsValue("akka_connector.hostname").openOr("127.0.0.1")
  val remotePort = APIUtil.getPropsValue("akka_connector.port").openOr("2662")

  val localHostname = "127.0.0.1"
  val localPort = Helper.findAvailablePort()

  val akka_loglevel = APIUtil.getPropsValue("akka_connector.loglevel").openOr("INFO")

  val commonConf = 
  """
  akka {
    loggers = ["akka.event.slf4j.Slf4jLogger"]
    loglevel =  """ + akka_loglevel + """
    actor {
      provider = "akka.remote.RemoteActorRefProvider"
      allow-java-serialization = off
      kryo  {
      type = "graph"
      idstrategy = "default"
      buffer-size = 65536
      max-buffer-size = -1
      use-manifests = false
      use-unsafe = true
      post-serialization-transformations = "off"
      #post-serialization-transformations = "lz4,aes"
      #encryption {
      #  aes {
      #      mode = "AES/CBC/PKCS5Padding"
      #      key = j68KkRjq21ykRGAQ
      #      IV-length = 16
      #  }
      #}
      implicit-registration-logging = false
      kryo-trace = false
      resolve-subclasses = true
      }
      serializers {
        kryo = "com.twitter.chill.akka.AkkaSerializer"
      }
      serialization-bindings {
        "net.liftweb.common.Full" = kryo,
        "net.liftweb.common.Empty" = kryo,
        "net.liftweb.common.Box" = kryo,
        "net.liftweb.common.ParamFailure" = kryo,
        "code.api.APIFailure" = kryo,
        "com.openbankproject.commons.model.BankAccount" = kryo,
        "com.openbankproject.commons.model.View" = kryo,
        "code.model.dataAccess.ViewImpl" = kryo,
        "com.openbankproject.commons.model.User" = kryo,
        "com.openbankproject.commons.model.ViewId" = kryo,
        "com.openbankproject.commons.model.ViewIdBankIdAccountId" = kryo,
        "com.openbankproject.commons.model.Permission" = kryo,
        "scala.Unit" = kryo,
        "scala.Boolean" = kryo,
        "java.io.Serializable" = kryo,
        "scala.collection.immutable.List" = kryo,
        "akka.actor.ActorSelectionMessage" = kryo,
        "code.model.Consumer" = kryo,
        "code.model.AppType" = kryo
      }
    }
    remote {
      enabled-transports = ["akka.remote.netty.tcp"]
      netty {
        tcp {
          send-buffer-size    = 50000000
          receive-buffer-size = 50000000
          maximum-frame-size  = 52428800
        }
      }
    }
  }
  """

  val lookupConf = 
  s"""
  ${commonConf} 
  akka {
    remote.netty.tcp.hostname = ${localHostname}
    remote.netty.tcp.port = 0
  }
  """

  val localConf =
  s"""
  ${commonConf} 
  akka {
    remote.netty.tcp.hostname = ${localHostname}
    remote.netty.tcp.port = ${localPort}
  }
  """

  val remoteConf = 
  s"""
  ${commonConf} 
  akka {
    remote.netty.tcp.hostname = ${remoteHostname}
    remote.netty.tcp.port = ${remotePort}
  }
  """
}
