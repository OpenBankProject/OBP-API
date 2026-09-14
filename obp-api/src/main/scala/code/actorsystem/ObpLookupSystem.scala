/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.actorsystem

import org.apache.pekko.actor.{ActorSystem}
import code.api.util.APIUtil
import code.bankconnectors.LocalMappedOutInBoundTransfer
import code.bankconnectors.akka.actor.{AkkaConnectorActorConfig, AkkaConnectorHelperActor}
import code.util.Helper
import code.util.Helper.MdcLoggable
// import com.openbankproject.adapter.pekko.commons.config.PekkoConfig // TODO: Re-enable when Pekko adapter is available
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Full


object ObpLookupSystem extends ObpLookupSystem {
  this.init
}

trait ObpLookupSystem extends MdcLoggable {
  // @volatile + synchronized double-checked init: without it two threads can both see null,
  // both build an ActorSystem (resource leak), and a reader can observe a stale null.
  @volatile var obpLookupSystem: ActorSystem = null
  val props_hostname = Helper.getHostname

  def init (): ActorSystem = {
    if (obpLookupSystem == null) {
      synchronized {
        if (obpLookupSystem == null) {
          val system = ActorSystem("ObpLookupSystem", ConfigFactory.load(ConfigFactory.parseString(ObpActorConfig.lookupConf)))
          logger.info(ObpActorConfig.lookupConf)
          obpLookupSystem = system
        }
      }
    }
    obpLookupSystem
  }

  def getActor(actorName: String) = {

    val actorPath: String = {

      val hostname = ObpActorConfig.localHostname
      val port = ObpActorConfig.localPort
      val props_hostname = Helper.getHostname
      if (port == 0) {
        logger.error("Failed to connect to local Remotedata actor, the port is 0, can not find a proper port in current machine.")
      }
      s"pekko.tcp://ObpActorSystem_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }

    this.obpLookupSystem.actorSelection(actorPath)
  }

  def getAkkaConnectorActor(actorName: String) = {

    val hostname = APIUtil.getPropsValue("akka_connector.hostname")
    val port = APIUtil.getPropsValue("akka_connector.port")
    val embeddedAdapter = APIUtil.getPropsAsBoolValue("akka_connector.embedded_adapter", false)

    val actorPath: String = (hostname, port) match {
      case (Full(h), Full(p)) if !embeddedAdapter =>
        val hostname = h
        val port = p
        val akka_connector_hostname = Helper.getAkkaConnectorHostname
        s"pekko.tcp://SouthSideAkkaConnector_${akka_connector_hostname}@${hostname}:${port}/user/${actorName}"

      case _ =>
        val hostname = AkkaConnectorActorConfig.localHostname
        val port = AkkaConnectorActorConfig.localPort
        val props_hostname = Helper.getHostname
        if (port == 0) {
          logger.error("Failed to find an available port.")
        }

        if(embeddedAdapter) {
          // AkkaConfig(LocalMappedOutInBoundTransfer, Some(ObpActorSystem.northSideAkkaConnectorActorSystem)) // TODO: Re-enable when Pekko adapter is available
        } else {
          AkkaConnectorHelperActor.startAkkaConnectorHelperActors(ObpActorSystem.northSideAkkaConnectorActorSystem)
        }

        s"pekko.tcp://SouthSideAkkaConnector_${props_hostname}@${hostname}:${port}/user/${actorName}"
    }
    this.obpLookupSystem.actorSelection(actorPath)
  }

}
