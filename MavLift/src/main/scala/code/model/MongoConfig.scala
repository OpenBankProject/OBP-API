package myapp.model

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
       Props.get("mongo.host", "obp_mongodb"),
       Props.getInt("mongo.port", 27017)
    )
    MongoDB.defineDb(DefaultMongoIdentifier, new Mongo(srvr), "OBP003")
    MongoDB.defineDb(AdminDb, new Mongo(srvr), "admin")
  }
}
