package code.selections

import code.util.Helper.MdcLoggable
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

trait SelectionsProvider {
  def createSelection(
    userId: String,
    selectionName: String,
    isFavourites: Boolean,
    isSharable: Boolean
  ): Box[SelectionsTrait]

  def getSelectionById(
    selectionId: String
  ): Box[SelectionsTrait]

  def deleteSelectionById(
    selectionId: String,
  ): Box[Boolean]
  
  def getSelectionsByUserId(
    userId: String
  ): List[SelectionsTrait]

}

object MappedSelectionsProvider extends MdcLoggable with SelectionsProvider{
  
  override def createSelection(
    userId: String,
    selectionName: String,
    isFavourites: Boolean,
    isSharable: Boolean
  ): Box[SelectionsTrait] =
    tryo (
      Selections
        .create
        .UserId(userId)
        .SelectionName(selectionName)
        .IsFavourites(isFavourites)
        .IsSharable(isSharable) 
        .saveMe()
    )

  override def getSelectionById(
    selectionId: String
  ) = Selections.find(By(Selections.SelectionId,selectionId))

  override def deleteSelectionById(
    selectionId: String,
  ): Box[Boolean]  =  Selections.find(By(Selections.SelectionId,selectionId)).map(_.delete_!)

  override def getSelectionsByUserId(
    userId: String
  ): List[SelectionsTrait] = Selections.findAll(By(Selections.UserId,userId))

}