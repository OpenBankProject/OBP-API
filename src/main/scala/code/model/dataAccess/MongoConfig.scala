/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.model.dataAccess

import code.api.util.APIUtil
import code.metadata.narrative.OBPNarrativeInit
import code.metadata.wheretags.OBPWhereTagInit
import com.mongodb.MongoClient
import net.liftweb.util.ConnectionIdentifier

object AdminDb extends ConnectionIdentifier {
  val jndiName = "admin"
}

object MongoConfig {
  def init: Unit = {
    import net.liftweb.mongodb.MongoDB
    import com.mongodb.{Mongo, ServerAddress}
    import net.liftweb.util.{Props, DefaultConnectionIdentifier}


    val srvr = new ServerAddress(
       APIUtil.getPropsValue("mongo.host", "localhost"),
       APIUtil.getPropsAsIntValue("mongo.port", 27017)
    )
    val defaultDatabase = Props.mode match {
        case Props.RunModes.Test  => "test"
        case _ => "OBP006"
      }

    MongoDB.defineDb(DefaultConnectionIdentifier, new MongoClient(srvr), APIUtil.getPropsValue("mongo.dbName", defaultDatabase))
    MongoDB.defineDb(AdminDb, new MongoClient(srvr), "admin")


    HostedBank.init
    Account.init
    OBPNarrativeInit.init
    OBPWhereTagInit.init
  }
}