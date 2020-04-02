package code.api.pemusage

import code.util.Helper.MdcLoggable
import net.liftweb.mapper._

import scala.collection.immutable.List

object MappedPemUsageProvider extends PemUsageProviderTrait with MdcLoggable {
  
}

class PemUsage extends PemUsageTrait with LongKeyedMapper[PemUsage] with IdPK with CreatedUpdated {
  override def getSingleton = PemUsage
  object PemHash extends MappedString(this, 50)
  object ConsumerId extends MappedString(this, 50)
  object LastUserId extends MappedString(this, 50)

  def pemHash: String = PemHash.get
  def consumerId: String = ConsumerId.get
  def lastUserId: String = LastUserId.get

}

object PemUsage extends PemUsage with LongKeyedMetaMapper[PemUsage] {
  override def dbIndexes: List[BaseIndex[PemUsage]] = UniqueIndex(PemHash) :: super.dbIndexes
}
