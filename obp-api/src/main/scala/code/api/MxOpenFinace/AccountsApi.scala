package code.api.MxOpenFinace

import code.api.Constant
import code.api.MxOpenFinace.JSONFactory_MX_OPEN_FINANCE_0_0_1._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiTag, CallContext, NewStyle}
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.rest.RestHelper
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

object APIMethods_AccountsApi extends RestHelper {
    val apiVersion =  MxOpenFinanceCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getAccountByAccountId ::
      getAccounts ::
      Nil

            
     resourceDocs += ResourceDoc(
       getAccountByAccountId, 
       apiVersion, 
       nameOf(getAccountByAccountId),
       "GET", 
       "/accounts/ACCOUNT_ID", 
       "getAccountByAccountId",
       s"""${mockedDataText(false)}
            Get Account by AccountId
            """,
       emptyObjectJson,
       ofReadAccountBasic,
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Accounts") :: apiTagMXOpenFinance :: Nil
     )

     lazy val getAccountByAccountId : OBPEndpoint = {
       case "accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           val detailViewId = ViewId(Constant.READ_ACCOUNTS_DETAIL_VIEW_ID)
           val basicViewId = ViewId(Constant.READ_ACCOUNTS_BASIC_VIEW_ID)
           for {
             (Full(user), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- NewStyle.function.checkUKConsent(user, callContext)
             (account, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
             view: View <- NewStyle.function.checkViewsAccessAndReturnView(detailViewId, basicViewId, BankIdAccountId(account.bankId, AccountId(accountId)), Full(user), callContext)
             moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), callContext)
             (moderatedAttributes: List[AccountAttribute], callContext) <- NewStyle.function.getModeratedAccountAttributesByAccount(
               account.bankId,
               account.accountId,
               view.viewId,
               callContext: Option[CallContext])
           } yield {
            (createReadAccountBasicJsonMXOFV10(moderatedAccount, moderatedAttributes, view: View), callContext)
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccounts, 
       apiVersion, 
       nameOf(getAccounts),
       "GET", 
       "/accounts", 
       "getAccounts",
       s"""${mockedDataText(false)}
            Get Accounts
            """,
       emptyObjectJson,
       ofReadAccountBasic,
       List(
         UserNotLoggedIn, 
         ConsentNotFound,
         ConsentNotBeforeIssue,
         ConsentExpiredIssue, 
         UnknownError
       ),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Accounts") :: apiTagMXOpenFinance :: Nil
     )

     lazy val getAccounts : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           val detailViewId = ViewId(Constant.READ_ACCOUNTS_DETAIL_VIEW_ID)
           val basicViewId = ViewId(Constant.READ_ACCOUNTS_BASIC_VIEW_ID)
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- NewStyle.function.checkUKConsent(u, callContext)
             _ <- passesPsd2Aisp(callContext)
             availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
             (accounts: List[BankAccount], callContext) <- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
             (moderatedAttributes: List[AccountAttribute], callContext) <- NewStyle.function.getModeratedAccountAttributesByAccounts(
               accounts.map(a => BankIdAccountId(a.bankId, a.accountId)),
               basicViewId,
               callContext: Option[CallContext])
           } yield {
             val allAccounts: List[Box[(BankAccount, View)]] = for (account: BankAccount <- accounts) yield {
               APIUtil.checkViewAccessAndReturnView(detailViewId, BankIdAccountId(account.bankId, account.accountId), Full(u)).or(
                 APIUtil.checkViewAccessAndReturnView(basicViewId, BankIdAccountId(account.bankId, account.accountId), Full(u))
               ) match {
                 case Full(view) =>
                   Full(account, view)
                 case _ =>
                   Empty
               }
             }
             val accountsWithProperView: List[(BankAccount, View)] = allAccounts.filter(_.isDefined).map(_.openOrThrowException(attemptedToOpenAnEmptyBox))
             (createReadAccountsBasicJsonMXOFV10(accountsWithProperView, moderatedAttributes), callContext)
           }
         }
       }

}


