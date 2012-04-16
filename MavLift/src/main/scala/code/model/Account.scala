package code.model

import com.mongodb.QueryBuilder
import net.liftweb.mongodb.JsonObjectMeta
import net.liftweb.mongodb.JsonObject
import net.liftweb.mongodb.record.MongoMetaRecord
import net.liftweb.mongodb.record.field.ObjectIdPk
import net.liftweb.mongodb.record.MongoRecord
import net.liftweb.mongodb.record.field.BsonRecordField
import net.liftweb.mongodb.record.field.MongoJsonObjectListField
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mongodb.record.field.BsonRecordListField
import net.liftweb.mongodb.record.{BsonRecord, BsonMetaRecord}
import net.liftweb.record.field.{StringField, BooleanField}

/**
 * There should be only one of these for every real life "this" account. TODO: Enforce this
 * 
 * As a result, this can provide a single point from which to retrieve the aliases associated with
 * this account, rather than needing to duplicate the aliases into every single transaction.
 */
class Account extends MongoRecord[Account] with ObjectIdPk[Account]{
 def meta = Account 
 
  object anonAccess extends BooleanField(this, false)
  protected object holder extends StringField(this, 255)
  protected object number extends StringField(this, 255)
  protected object kind extends StringField(this, 255)
  protected object bank extends BsonRecordField(this, OBPBank)
  object otherAccounts extends BsonRecordListField(this, OtherAccount)
  
  def allEnvelopes : List[OBPEnvelope] = {
   //TODO: This should use ids instead of just the holder name
   //TODO : Also, due to some string hardcoding where we used "Music Pictures Limited" instead of
   // 		what we get from our actual bank account ("MUSIC PICTURES LIMITED"), we have to make it
   //  		upper case. This all needs to be revamped once we support multiple accounts, so despite being
   //		incredibly ugly, it should be okay temporarily
   val qry = QueryBuilder.start("obp_transaction.this_account.holder").is(holder.get.toUpperCase()).get
   OBPEnvelope.findAll(qry)
 }
 
  def getUnmediatedOtherAccountUrl(user: String, otherAccountHolder: String) : Box[String] = {
   for{
     o <- otherAccounts.get.find(acc=> {
	     acc.holder.get.equals(otherAccountHolder)
     })
   } yield o.url.get
 }
  
  def getMediatedOtherAccountURL(user: String, otherAccountHolder: String) : Box[String] = {
   val otherAccountURL = for{
     o <- otherAccounts.get.find(acc=> {
	     acc.holder.get.equals(otherAccountHolder)
     })
   } yield o.url.get
   
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
	     acc.holder.get.equals(otherAccountHolder)
     })
   } yield o.imageUrl.get
   
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
	     acc.holder.get.equals(otherAccountHolder)
     })
   } yield o.moreInfo.get
   
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

class OtherAccount private() extends BsonRecord[OtherAccount] {
  def meta = OtherAccount
  
  object holder extends StringField(this, 200)
  
  object publicAlias extends StringField(this, 100)
  object privateAlias extends StringField(this, 100)
  object moreInfo extends StringField(this, 100)
  object url extends StringField(this, 100)
  object imageUrl extends StringField(this, 100)
  object openCorporatesUrl extends StringField(this, 100){
    override def optional_? = true
  }
}

object OtherAccount extends OtherAccount with BsonMetaRecord[OtherAccount]
