package code.api.MxOpenFinace

import code.api.Constant
import code.api.MxOpenFinace.JSONFactory_MX_OPEN_FINANCE_0_0_1._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiTag, NewStyle}
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

object APIMethods_BalancesApi extends RestHelper {
    val apiVersion =  MxOpenFinanceCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      getBalanceByAccountId ::
      Nil

            
     resourceDocs += ResourceDoc(
       getBalanceByAccountId, 
       apiVersion, 
       nameOf(getBalanceByAccountId),
       "GET", 
       "/accounts/ACCOUNT_ID/balances", 
       "getBalanceByAccountId",
       s"""${mockedDataText(false)}
            Get Balance for an Account
            """,
       json.parse(""""""),
       ofReadBalances,
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG), 
       ApiTag("Balances") :: apiTagMXOpenFinance :: Nil
     )

     lazy val getBalanceByAccountId : OBPEndpoint = {
       case "accounts" :: accountId:: "balances" :: Nil JsonGet _ => {
         cc =>
           val viewId = ViewId(Constant.READ_BALANCES_VIEW_ID)
           for {
             (Full(user), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             _ <- NewStyle.function.checkUKConsent(user, callContext)
             (account, callContext) <- NewStyle.function.getBankAccountByAccountId(AccountId(accountId), callContext)
             view: View <- NewStyle.function.checkViewAccessAndReturnView(viewId, BankIdAccountId(account.bankId, AccountId(accountId)), Full(user), callContext)
             moderatedAccount <- NewStyle.function.moderatedBankAccountCore(account, view, Full(user), callContext)
           } yield {
             (createAccountBalanceJSON(moderatedAccount), callContext)
           }
         }
       }

}



