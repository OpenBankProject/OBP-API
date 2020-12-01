package code.selections

import code.util.MappedUUID
import net.liftweb.mapper._

class Selection extends SelectionTrait with LongKeyedMapper[Selection] with IdPK with CreatedUpdated {
  def getSingleton = Selection

  object SelectionId extends MappedUUID(this)
  object UserId extends MappedString(this, 100)
  object SelectionName extends MappedString(this, 100)
  object IsFavourites extends MappedBoolean(this)
  object IsSharable extends MappedBoolean(this)

  override def selectionId: String = SelectionId.get    
  override def userId: String = UserId.get              
  override def selectionName: String = SelectionName.get                  
  override def isFavourites: Boolean = IsFavourites.get
  override def isSharable: Boolean = IsSharable.get    
}

object Selection extends Selection with LongKeyedMetaMapper[Selection] {
  override def dbIndexes = UniqueIndex(SelectionId) :: super.dbIndexes
}

trait SelectionTrait {
  def selectionId: String
  def userId: String
  def selectionName: String
  def isFavourites: Boolean
  def isSharable: Boolean
}
