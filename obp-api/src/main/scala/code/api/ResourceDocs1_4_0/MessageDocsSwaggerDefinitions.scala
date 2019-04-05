package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil
import code.api.util.APIUtil.AdapterImplementation
import com.openbankproject.commons.dto.{CallContextAkka, InboundAccount, InboundBank}
import com.openbankproject.commons.model.{AccountRouting, AccountRule}

import scala.collection.immutable.{List, Nil}

object MessageDocsSwaggerDefinitions
{
  val accountRouting = AccountRouting("","")
  val accountRule = AccountRule("","")
  val inboundAccount = InboundAccount(
      bankId = "",
      branchId = "",
      accountId = "",
      accountNumber = "",
      accountType = "",
      balanceAmount = "",
      balanceCurrency = "",
      owners = "" :: "" :: Nil,
      viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = "",
      accountRouting = List(accountRouting),
      accountRules = List(accountRule)
    )
  
  val callContextAkka = CallContextAkka(
      Some(""),
      Some("9ddb6507-9cec-4e5e-b09a-ef1cb203825a"),
      "",
      Some("")
    )
  
  val inboundBank = InboundBank(
      bankId = "",
      shortName = "The Royal Bank of Scotland",
      fullName = "The Royal Bank of Scotland",
      logoUrl = "http://www.red-bank-shoreditch.com/logo.gif",
      websiteUrl = "http://www.red-bank-shoreditch.com",
      bankRoutingScheme = "OBP",
      bankRoutingAddress = "rbs"
    )
  
  val adapterImplementation = AdapterImplementation("- Core", 2)
  
  val allFields ={
      val allFieldsThisFile = for (
        v <- this.getClass.getDeclaredFields
        //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
        if (APIUtil.notExstingBaseClass(v.getName()))
      ) yield {
          v.setAccessible(true)
          v.get(this)
        }
    allFieldsThisFile
  }
}
