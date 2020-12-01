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
  ): Box[SelectionTrait]

  def getSelectionById(
    selectionId: String
  ): Box[SelectionTrait]

  def deleteSelectionById(
    selectionId: String,
  ): Box[Boolean]
  
  def getSelectionsByUserId(
    userId: String
  ): List[SelectionTrait]

}

object MappedSelectionsProvider extends MdcLoggable with SelectionsProvider{
  
  override def createSelection(
    userId: String,
    selectionName: String,
    isFavourites: Boolean,
    isSharable: Boolean
  ): Box[SelectionTrait] =
    tryo (
      Selection
        .create
        .UserId(userId)
        .SelectionName(selectionName)
        .IsFavourites(isFavourites)
        .IsSharable(isSharable) 
        .saveMe()
    )

  override def getSelectionById(
    selectionId: String
  ) = Selection.find(By(Selection.SelectionId,selectionId))

  override def deleteSelectionById(
    selectionId: String,
  ): Box[Boolean]  =  Selection.find(By(Selection.SelectionId,selectionId)).map(_.delete_!)

  override def getSelectionsByUserId(
    userId: String
  ): List[SelectionTrait] = Selection.findAll(By(Selection.UserId,userId))

}