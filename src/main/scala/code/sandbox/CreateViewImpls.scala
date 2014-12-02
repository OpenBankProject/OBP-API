package code.sandbox

import code.model.{AccountId, BankId}
import code.model.dataAccess.ViewImpl

trait CreateViewImpls {

  type ViewType = ViewImpl

  protected def createSaveableViews(acc : SandboxAccountImport) : List[Saveable[ViewType]] = {

    def asSaveableViewImpl(viewImpl : ViewImpl) = new Saveable[ViewImpl] {
      val value = viewImpl
      def save() = value.save
    }


    val bankId = BankId(acc.bank)
    val accountId = AccountId(acc.id)

    val ownerView = ViewImpl.unsavedOwnerView(bankId, accountId, "Owner View")

    val publicView =
      if(acc.generate_public_view) Some(ViewImpl.unsavedDefaultPublicView(bankId, accountId, "Public View"))
      else None

    List(Some(ownerView), publicView).flatten.map(asSaveableViewImpl)
  }

}
