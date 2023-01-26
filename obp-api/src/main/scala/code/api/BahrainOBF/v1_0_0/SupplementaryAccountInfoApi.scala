package code.api.BahrainOBF.v1_0_0

import code.api.berlin.group.v1_3.JvalueCaseClass
import code.api.util.APIUtil._
import code.api.util.ApiTag
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer

object APIMethods_SupplementaryAccountInfoApi extends RestHelper {
    val apiVersion =  ApiCollector.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      accountsAccountIdSupplementaryAccountInfoGet ::
      Nil

            
     resourceDocs += ResourceDoc(
       accountsAccountIdSupplementaryAccountInfoGet, 
       apiVersion, 
       nameOf(accountsAccountIdSupplementaryAccountInfoGet),
       "GET", 
       "/accounts/ACCOUNT_ID/supplementary-account-info", 
       "Get Accounts Supplementary Account Info by AccountId",
       s"""${mockedDataText(true)}
            
            """,
       json.parse(""""""),
       json.parse("""{
  "Data" : {
    "ReadAccount" : {
      "ReadLoanMortgageInfo" : {
        "JointHolderName" : "JointHolderName",
        "LoanAmount" : "LoanAmount",
        "DisbursedAmount" : "DisbursedAmount",
        "LoanFrequency" : "LoanFrequency",
        "Rate" : "Rate",
        "Numberofinstallments" : "Numberofinstallments",
        "OutstandingLoanAmount" : "OutstandingLoanAmount"
      },
      "AccountID" : "AccountID",
      "ReadDepositInfo" : {
        "JointHolderName" : "JointHolderName",
        "DepositFrequency" : "DepositFrequency",
        "MaturityAmount" : "MaturityAmount",
        "Rate" : "Rate",
        "InitialDepositAmount" : "InitialDepositAmount",
        "MaturityDate" : "2000-01-23T04:56:07.000+00:00"
      },
      "AccountOpeningDate" : "2000-01-23T04:56:07.000+00:00",
      "ReadCASAInfo" : {
        "JointHolderName" : "JointHolderName",
        "Rate" : "Rate",
        "LienAmount" : "LienAmount"
      },
      "ReadCreditCardInfo" : {
        "Rate" : "Rate",
        "GracePeriod" : "GracePeriod",
        "CardLimit" : "CardLimit",
        "URL" : "URL"
      },
      "ReadEWalletInfo" : {
        "ChargeFrequency" : "ChargeFrequency",
        "Charge" : "Charge"
      }
    }
  }
}"""),
       List(UserNotLoggedIn, UnknownError),
       ApiTag("Supplementary Account Info") :: apiTagMockedData :: Nil
     )

     lazy val accountsAccountIdSupplementaryAccountInfoGet : OBPEndpoint = {
       case "accounts" :: accountId:: "supplementary-account-info" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authenticatedAccess(cc, UserNotLoggedIn)
             } yield {
            (json.parse("""{
  "Data" : {
    "ReadAccount" : {
      "ReadLoanMortgageInfo" : {
        "JointHolderName" : "JointHolderName",
        "LoanAmount" : "LoanAmount",
        "DisbursedAmount" : "DisbursedAmount",
        "LoanFrequency" : "LoanFrequency",
        "Rate" : "Rate",
        "Numberofinstallments" : "Numberofinstallments",
        "OutstandingLoanAmount" : "OutstandingLoanAmount"
      },
      "AccountID" : "AccountID",
      "ReadDepositInfo" : {
        "JointHolderName" : "JointHolderName",
        "DepositFrequency" : "DepositFrequency",
        "MaturityAmount" : "MaturityAmount",
        "Rate" : "Rate",
        "InitialDepositAmount" : "InitialDepositAmount",
        "MaturityDate" : "2000-01-23T04:56:07.000+00:00"
      },
      "AccountOpeningDate" : "2000-01-23T04:56:07.000+00:00",
      "ReadCASAInfo" : {
        "JointHolderName" : "JointHolderName",
        "Rate" : "Rate",
        "LienAmount" : "LienAmount"
      },
      "ReadCreditCardInfo" : {
        "Rate" : "Rate",
        "GracePeriod" : "GracePeriod",
        "CardLimit" : "CardLimit",
        "URL" : "URL"
      },
      "ReadEWalletInfo" : {
        "ChargeFrequency" : "ChargeFrequency",
        "Charge" : "Charge"
      }
    }
  }
}"""), callContext)
           }
         }
       }

}



