package code.views.system

import code.model.dataAccess.{MappedBank, MappedBankAccount, ResourceUser, ViewImpl}
import net.liftweb.mapper._

class ViewUsage extends LongKeyedMapper[ViewUsage] with IdPK with CreatedUpdated {
  def getSingleton = ViewUsage
  object user extends MappedLongForeignKey(this, ResourceUser)
  object view extends MappedLongForeignKey(this, ViewImpl)
  object bank extends MappedLongForeignKey(this, MappedBank)
  object account extends MappedLongForeignKey(this, MappedBankAccount)
}
object ViewUsage extends ViewUsage with LongKeyedMetaMapper[ViewUsage] {
  override def dbIndexes: List[BaseIndex[ViewUsage]] = UniqueIndex(bank, account, view, user) :: super.dbIndexes
}
