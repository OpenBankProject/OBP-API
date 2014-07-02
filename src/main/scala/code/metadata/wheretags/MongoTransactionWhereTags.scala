package code.metadata.wheretags

import java.util.Date
import code.model.GeoTag
import code.model.dataAccess.{OBPEnvelope}
import org.bson.types.ObjectId
import net.liftweb.common.{Loggable, Full}
import scala.collection.mutable

object MongoTransactionWhereTags extends WhereTags with Loggable {

  def addWhereTag(bankId : String, accountId : String, transactionId: String)
                 (userId: String, viewId : Long, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {


    val newTag = OBPGeoTag.createRecord.
      bankId(bankId).
      accountId(accountId).
      transactionId(transactionId).
      userId(userId).
      viewID(viewId).
      date(datePosted).
      geoLongitude(longitude).
      geoLatitude(latitude)

    //before to save the geo tag we need to be sure there is only one per view
    //so we look if there is already a tag with the same view (viewId)

      val existing = OBPGeoTag.find(bankId, accountId, transactionId, viewId)
      if (existing.isDefined) {
        //remove the old one and replace it with the new one
        existing.get.delete_!
      }
      newTag.saveTheRecord().isDefined

  }

  def deleteWhereTag(bankId: String, accountId: String, transactionId: String)(viewId: Long): Boolean = {
    val existing = OBPGeoTag.find(bankId, accountId, transactionId, viewId)
    existing match {
      case Full(tag) => tag.delete_!
      case _ => {
        logger.info("Could not delete tag: not found")
        false
      }
    }
  }

  def getWhereTagsForTransaction(bankId : String, accountId : String, transactionId: String)() : List[GeoTag] = {
    OBPGeoTag.findAll(bankId, accountId, transactionId)
  }
}
