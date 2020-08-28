package code.api.MxOpenFinace

import code.api.util.APIUtil._
import code.api.util.{ApiTag, CallContext, NewStyle}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._
import code.api.MxOpenFinace.JSONFactory_MX_OPEN_FINANCE_1_0._
import code.metadata.tags.Tags
import code.util.Helper
import code.views.Views
import com.openbankproject.commons.model.{AccountId, BankAccount, BankId, BankIdAccountId}

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

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
       s"""${mockedDataText(true)}
            Get Account by AccountId
            """,
       json.parse(""""""),
       ofReadAccountBasic,
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getAccountByAccountId : OBPEndpoint = {
       case "accounts" :: accountId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(user), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
             (account, callContext) <- NewStyle.function.getBankAccount(BankId(defaultBankId), AccountId(accountId), callContext)
             view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(BankId(defaultBankId), AccountId(accountId)), callContext)
             moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), callContext)
             (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesByAccount(
               BankId(defaultBankId),
               account.accountId,
               cc.callContext: Option[CallContext])
             tags <- Future(Tags.tags.vend.getTagsOnAccount(BankId(defaultBankId), account.accountId)(view.viewId))
           } yield {
            (createReadAccountBasicJsonMXOFV10(moderatedAccount), callContext)
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
       s"""${mockedDataText(true)}
            Get Accounts
            """,
       json.parse(""""""),
       ofReadAccountBasic,
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Accounts") :: apiTagMockedData :: Nil
     )

     lazy val getAccounts : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
             availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u)
             (accounts: List[BankAccount], callContext)<- NewStyle.function.getBankAccounts(availablePrivateAccounts, callContext)
           } yield {
             (createReadAccountsBasicJsonMXOFV10(accounts), callContext)
           }
         }
       }

}



