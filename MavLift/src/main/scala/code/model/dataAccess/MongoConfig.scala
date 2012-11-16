/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.      

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
    by 
    Simon Redfern : simon AT tesobe DOT com
    Everett Sochowski: everett AT tesobe DOT com
    Benali Ayoub : ayoub AT tesobe DOT com

 */
package code.model.dataAccess

import net.liftweb._
import mongodb._
import util.Props
import com.mongodb.{Mongo, ServerAddress}

object AdminDb extends MongoIdentifier {
  val jndiName = "admin"
}

object MongoConfig {
  def init: Unit = {
    val srvr = new ServerAddress(
       Props.get("mongo.host", "obp_mongod"),
       Props.getInt("mongo.port", 27017)
    )
    MongoDB.defineDb(DefaultMongoIdentifier, new Mongo(srvr), "OBP006")
    MongoDB.defineDb(AdminDb, new Mongo(srvr), "admin")
  }
}
