package code.sandbox

import code.model.{AccountId, BankId}
import code.model.dataAccess.ViewImpl

trait CreateViewImpls {

  type ViewType = ViewImpl

  def asSaveableViewImpl(viewImpl : ViewImpl) = new Saveable[ViewImpl] {
    val value = viewImpl
    def save() = value.save
  }

  protected def createSaveableOwnerView(bankId : BankId, accountId : AccountId) : Saveable[ViewType] =
    asSaveableViewImpl(ViewImpl.unsavedOwnerView(bankId, accountId, "Owner View"))

  protected def createSaveablePublicView(bankId : BankId, accountId : AccountId) : Saveable[ViewType] =
    asSaveableViewImpl(ViewImpl.unsavedDefaultPublicView(bankId, accountId, "Public View"))

}
