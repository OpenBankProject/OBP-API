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

import org.apache.pekko.actor.ActorSystem
import code.bankconnectors.akka.actor.AkkaConnectorActorConfig
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.typesafe.config.ConfigFactory


object ObpActorSystem extends MdcLoggable {

  val props_hostname = Helper.getHostname
  // @volatile so the single assignment of each actor system is visible to all reader threads
  // (the JVM memory model does not guarantee visibility of a non-volatile write across threads).
  @volatile var obpActorSystem: ActorSystem = _
  @volatile var northSideAkkaConnectorActorSystem: ActorSystem = _

  def startLocalActorSystem() = localActorSystem

  lazy val localActorSystem: ActorSystem = {
    logger.info("Starting local actor system")
    val localConf = ObpActorConfig.localConf
    logger.info(localConf)
    obpActorSystem = ActorSystem.create(s"ObpActorSystem_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(localConf)))
    obpActorSystem
  }

  // synchronized double-checked init so concurrent callers start the connector system exactly once.
  def startNorthSideAkkaConnectorActorSystem(): ActorSystem = {
    if (northSideAkkaConnectorActorSystem == null) {
      synchronized {
        if (northSideAkkaConnectorActorSystem == null) {
          logger.info("Starting North Side Akka Connector actor system")
          val localConf = AkkaConnectorActorConfig.localConf
          logger.info(localConf)
          northSideAkkaConnectorActorSystem = ActorSystem.create(s"SouthSideAkkaConnector_${props_hostname}", ConfigFactory.load(ConfigFactory.parseString(localConf)))
        }
      }
    }
    northSideAkkaConnectorActorSystem
  }
}