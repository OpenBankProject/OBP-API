package code.api.ResourceDocs1_4_0

import java.util.Date

import code.api.util.APIUtil
import code.api.util.APIUtil.AdapterImplementation
import code.api.util.ExampleValue._
import com.openbankproject.commons.dto.{InboundAccount, InboundBank}
import com.openbankproject.commons.model._

import scala.collection.immutable.{List, Nil}

object MessageDocsSwaggerDefinitions
{
  
  val inboundAccountCommonCommons = InboundAccountCommonCommons(
    errorCode = "",
    bankId = bankIdExample.value,
    branchId = branchIdExample.value,
    accountId = accountIdExample.value,
    accountNumber = accountNumberExample.value,
    accountType = accountTypeExample.value,
    balanceAmount = balanceAmountExample.value,
    balanceCurrency = currencyExample.value,
    owners = List(owner1Example.value),
    viewsToGenerate = List("Owner", "Accountant", "Auditor"),
    bankRoutingScheme = bankRoutingSchemeExample.value,
    bankRoutingAddress = bankRoutingAddressExample.value,
    branchRoutingScheme = branchRoutingSchemeExample.value,
    branchRoutingAddress = branchRoutingAddressExample.value,
    accountRoutingScheme = accountRoutingSchemeExample.value,
    accountRoutingAddress = accountRoutingAddressExample.value
  )
  
  val bankAccountCommons = BankAccountCommons(
    accountId = AccountId(accountIdExample.value),
    accountType = accountTypeExample.value,
    balance = BigDecimal(balanceAmountExample.value),
    currency = currencyExample.value,
    name = usernameExample.value,
    label = labelExample.value,
    swift_bic = None,
    iban = Some(ibanExample.value),
    number = accountNumberExample.value,
    bankId = BankId(bankIdExample.value),
    lastUpdate = new Date(),
    branchId = branchIdExample.value,
    accountRoutingScheme = accountRoutingSchemeExample.value,
    accountRoutingAddress = accountRoutingAddressExample.value,
    accountRoutings = List(AccountRouting(accountRoutingSchemeExample.value,
                                          accountRoutingAddressExample.value)),
    accountRules = Nil,
    accountHolder = ""
  )
  
  val inboundAccountDec2018Example = 
    InboundAccount(
      bankId = bankIdExample.value,
      branchId = branchIdExample.value,
      accountId = accountIdExample.value,
      accountNumber = accountNumberExample.value,
      accountType = accountTypeExample.value,
      balanceAmount = balanceAmountExample.value,
      balanceCurrency = currencyExample.value,
      owners = owner1Example.value :: owner1Example.value :: Nil,
      viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
      bankRoutingScheme = bankRoutingSchemeExample.value,
      bankRoutingAddress = bankRoutingAddressExample.value,
      branchRoutingScheme = branchRoutingSchemeExample.value,
      branchRoutingAddress = branchRoutingAddressExample.value,
      accountRoutingScheme = accountRoutingSchemeExample.value,
      accountRoutingAddress = accountRoutingAddressExample.value,
      accountRouting = Nil,
      accountRules = Nil
    )
  
  val adapterAuthInfo = AdapterAuthInfo(
    userId = userIdExample.value, 
    username = usernameExample.value, 
    linkedCustomers = Some(List(BasicLindedCustomer(customerIdExample.value,customerNumberExample.value,legalNameExample.value))),
    userAuthContexts = Some(List(BasicUserAuthContext(keyExample.value, valueExample.value))),//be set by obp from some endpoints. 
    userCbsContexts= Some(List(BasicUserCbsContext(keyExample.value, valueExample.value))),  //be set by backend, send it back to the header? not finish yet.
    authViews = Some(List(AuthView(
      view = ViewBasic(
        id = viewIdExample.value,
        name = viewNameExample.value,
        description = viewDescriptionExample.value,
        ),
      account = AccountBasic(
        id = accountIdExample.value,
        accountRoutings =List(AccountRouting(
          scheme = accountRoutingSchemeExample.value,
          address = accountRoutingAddressExample.value
        )),
        customerOwners = List(InternalBasicCustomer(
          bankId = bankIdExample.value,
          customerId = customerIdExample.value,
          customerNumber = customerNumberExample.value,
          legalName = legalNameExample.value,
          dateOfBirth =  new Date(),
        )),
        userOwners = List(InternalBasicUser(
          userId = userIdExample.value,
          emailAddress = emailExample.value,
          name = usernameExample.value
        )))))))
  
  val adapterCallContext = Some(
    AdapterCallContext(
      correlationIdExample.value,
      Some(sessionIdExample.value),
      Some(adapterAuthInfo)
    )
  )
  
  val bank = 
    InboundBank(
      bankId = bankIdExample.value,
      shortName = "The Royal Bank of Scotland",
      fullName = "The Royal Bank of Scotland",
      logoUrl = "http://www.red-bank-shoreditch.com/logo.gif",
      websiteUrl = "http://www.red-bank-shoreditch.com",
      bankRoutingScheme = "OBP",
      bankRoutingAddress = "rbs"
    )
  
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
