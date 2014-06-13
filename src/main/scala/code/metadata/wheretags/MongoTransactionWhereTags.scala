package code.metadata.wheretags

import java.util.Date
import code.model.GeoTag
import code.model.dataAccess.{OBPGeoTag, OBPEnvelope}
import org.bson.types.ObjectId

object MongoTransactionWhereTags extends WhereTags {

  def addWhereTag(bankId : String, accountId : String, transactionId: String)
                 (userId: String, viewId : Long, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    (for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      val newTag = OBPGeoTag.createRecord.
        userId(userId).
        viewID(viewId).
        date(datePosted).
        geoLongitude(longitude).
        geoLatitude(latitude)


      //before to save the geo tag we need to be sure there is only one per view
      //so we look if there is already a tag with the same view (viewId)
      val tags = env.whereTags.get.find(geoTag => geoTag.viewID equals viewId) match {
        case Some(tag) => {
          //if true remplace it with the new one
          newTag :: env.whereTags.get.diff(Seq(tag))
        }
        case _ =>
          //else just add this one
          newTag :: env.whereTags.get
      }
      env.whereTags(tags).save
      true
    }).getOrElse(false)
  }

  def deleteWhereTag(bankId : String, accountId : String, transactionId: String)(viewId : Long) : Boolean = {
    //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
    (for {
      env <- OBPEnvelope.find(new ObjectId(transactionId)) ?~! "Transaction not found"
    } yield {
      val where :Option[OBPGeoTag] = env.whereTags.get.find(loc=>{loc.viewId ==viewId})
      where match {
        case Some(w) => {
          val newWhereTags = env.whereTags.get.diff(Seq(w))
          env.whereTags(newWhereTags).save
          true
        }
        case None => false
      }
    }).getOrElse(false)
  }

  def getWhereTagsForTransaction(bankId : String, accountId : String, transactionId: String) : Iterable[GeoTag] = {
      //current implementation has transactionId = mongoId (we don't need to use bankId or accountId
      val env = OBPEnvelope.find(new ObjectId(transactionId))
      env.map(_.whereTags.get).getOrElse(Nil)
  }
}
