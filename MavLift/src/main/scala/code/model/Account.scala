package code.model

import net.liftweb.mongodb.JsonObjectMeta
import net.liftweb.mongodb.JsonObject
import net.liftweb.mongodb.record.MongoMetaRecord
import net.liftweb.mongodb.record.field.ObjectIdPk
import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.mongodb.record.field.BsonRecordField
import net.liftweb.mongodb.record.field.MongoJsonObjectListField
import net.liftweb.common.{Box, Empty, Full}

/**
 * There should be only one of these for every real life "this" account. TODO: Enforce this
 * 
 * As a result, this can provide a single point from which to retrieve the aliases associated with
 * this account, rather than needing to duplicate the aliases into every single transaction.
 */
class Account extends MongoRecord[Account] with ObjectIdPk[Account]{
 def meta = Account 
 
  protected object holder extends net.liftweb.record.field.StringField(this, 255)
  protected object number extends net.liftweb.record.field.StringField(this, 255)
  protected object kind extends net.liftweb.record.field.StringField(this, 255)
  protected object bank extends BsonRecordField(this, OBPBank)
  object otherAccounts extends MongoJsonObjectListField[Account, OtherAccount](this, OtherAccount)
  
  def getMediatedOtherAccountURL(user: String, otherAccountHolder: String) : Box[String] = {
   val otherAccountURL = for{
     o <- otherAccounts.get.find(acc=> {
	     acc match{
	       case OtherAccount(`otherAccountHolder`, _, _, _, _, _) => true
	       case _ => false
	     }
     })
   } yield o.url
   
   user match{
      case "team" => otherAccountURL
      case "board" => otherAccountURL
      case "authorities" => otherAccountURL
      case "my-view" => otherAccountURL
      case "our-network" => otherAccountURL
      case _ => Empty
   }
 }
 
 def getMediatedOtherAccountImageURL(user: String, otherAccountHolder: String) : Box[String] = {
   val otherAccountImageURL = for{
     o <- otherAccounts.get.find(acc=> {
	     acc match{
	       case OtherAccount(`otherAccountHolder`, _, _, _, _, _) => true
	       case _ => false
	     }
     })
   } yield o.imageUrl
   
   user match{
      case "team" => otherAccountImageURL
      case "board" => otherAccountImageURL
      case "authorities" => otherAccountImageURL
      case "my-view" => otherAccountImageURL
      case "our-network" => otherAccountImageURL
      case _ => Empty
   }
 }
  
  def getMediatedOtherAccountMoreInfo(user: String, otherAccountHolder: String) : Box[String] = {
   val otherAccountMoreInfo = for{
     o <- otherAccounts.get.find(acc=> {
	     acc match{
	       case OtherAccount(`otherAccountHolder`, _, _, _, _, _) => true
	       case _ => false
	     }
     })
   } yield o.moreInfo
   
   user match{
      case "team" => otherAccountMoreInfo
      case "board" => otherAccountMoreInfo
      case "authorities" => otherAccountMoreInfo
      case "my-view" => otherAccountMoreInfo
      case "our-network" => otherAccountMoreInfo
      case _ => Empty
   }
 }
 
}

object Account extends Account with MongoMetaRecord[Account]

case class OtherAccount(holder: String = "",
						publicAlias: String = "", 
						privateAlias: String = "", 
						moreInfo: String = "", 
						url: String = "", 
						imageUrl: String = "")
	extends JsonObject[OtherAccount]{
  def meta = OtherAccount
}

object OtherAccount extends JsonObjectMeta[OtherAccount]
