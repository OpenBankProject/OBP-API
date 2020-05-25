package code.api.util.newstyle

import code.api.util.APIUtil.{OBPReturnType, fullBoxOrException}
import code.api.util.CallContext
import code.usercustomerlinks.{UserCustomerLink}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Box

import scala.concurrent.Future

object UserCustomerLinkNewStyle {

  def getUserCustomerLink(userId: String,
                             callContext: Option[CallContext]
                            ): OBPReturnType[List[UserCustomerLink]] = {
    Future(UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(userId: String), callContext)
  }
  def getUserCustomerLinks(customerId: String,
                           callContext: Option[CallContext]
                            ): OBPReturnType[List[UserCustomerLink]] = {
    Future(UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByCustomerId(customerId: String), callContext)
  }
  
  def deleteUserCustomerLink(UserCustomerLinkId: String,
                             callContext: Option[CallContext]
                            ): OBPReturnType[Box[Boolean]] = {
    UserCustomerLink.userCustomerLink.vend.deleteUserCustomerLink(UserCustomerLinkId: String) map {
      fullBoxOrException(_)
    } map {
      i => (i, callContext)
    }
  }

}

