package code.bankconnectors.akka.actor

import code.api.util.APIUtil
import code.util.Helper


object AkkaConnectorActorConfig {

  val remoteHostname = APIUtil.getPropsValue("akka_connector.hostname").openOr("127.0.0.1")
  val remotePort = APIUtil.getPropsValue("akka_connector.port").openOr("2662")

  val localHostname = "127.0.0.1"
  def localPort = Helper.findAvailablePort()

  val akka_loglevel = APIUtil.getPropsValue("akka_connector.loglevel").openOr("INFO")

  val commonConf = 
  """
  pekko {
    loggers = ["org.apache.pekko.event.slf4j.Slf4jLogger"]
    loglevel =  """ + akka_loglevel + """
    actor {
      provider = "org.apache.pekko.remote.RemoteActorRefProvider"
      allow-java-serialization = on
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
        java = "org.apache.pekko.serialization.JavaSerializer"
      }
      serialization-bindings {
        "net.liftweb.common.Full" = java,
        "net.liftweb.common.Empty" = java,
        "net.liftweb.common.Box" = java,
        "net.liftweb.common.ParamFailure" = java,
        "code.api.APIFailure" = java,
        "com.openbankproject.commons.model.BankAccount" = java,
        "com.openbankproject.commons.model.View" = java,
        "com.openbankproject.commons.model.User" = java,
        "com.openbankproject.commons.model.ViewId" = java,
        "com.openbankproject.commons.model.BankIdAccountIdViewId" = java,
        "com.openbankproject.commons.model.Permission" = java,
        "scala.Unit" = java,
        "scala.Boolean" = java,
        "java.io.Serializable" = java,
        "scala.collection.immutable.List" = java,
        "org.apache.pekko.actor.ActorSelectionMessage" = java,
        "code.model.Consumer" = java,
        "code.model.AppType" = java
      }
    }
    remote {
      artery {
        transport = tcp
        canonical.hostname = "127.0.0.1"
        canonical.port = 0
        bind.hostname = "127.0.0.1"
        bind.port = 0
        advanced {
          maximum-frame-size = 52428800
          buffer-pool-size = 128
          maximum-large-frame-size = 52428800
        }
      }
    }
  }
  """

  val lookupConf = 
  s"""
  ${commonConf} 
  pekko {
    remote.artery {
      canonical.hostname = ${localHostname}
      canonical.port = 0
      bind.hostname = ${localHostname}
      bind.port = 0
    }
  }
  """

  val localConf =
  s"""
  ${commonConf} 
  pekko {
    remote.artery {
      canonical.hostname = ${localHostname}
      canonical.port = ${localPort}
      bind.hostname = ${localHostname}
      bind.port = ${localPort}
    }
  }
  """

  val remoteConf = 
  s"""
  ${commonConf} 
  pekko {
    remote.artery {
      canonical.hostname = ${remoteHostname}
      canonical.port = ${remotePort}
      bind.hostname = ${remoteHostname}
      bind.port = ${remotePort}
    }
  }
  """
}
