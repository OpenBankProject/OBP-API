package code.api.ResourceDocs1_4_0

import code.api.Constant
import java.util.Date

import code.api.util.APIUtil._
import code.api.util.ExampleValue._
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.CustomerAttributeType
import com.openbankproject.commons.model.{BankAccountCommons, CustomerAttributeCommons, CustomerCommons, InboundAdapterCallContext, InboundAdapterInfoInternal, InboundStatusMessage, _}
import com.openbankproject.commons.util.ReflectUtils

import scala.collection.immutable.{List, Nil}

object MessageDocsSwaggerDefinitions
{
  
  val inboundAccountCommons = InboundAccountCommons(
    bankId = bankIdExample.value,
    branchId = branchIdExample.value,
    accountId = accountIdExample.value,
    accountNumber = accountNumberExample.value,
    accountType = accountTypeExample.value,
    balanceAmount = balanceAmountExample.value,
    balanceCurrency = currencyExample.value,
    owners = List(owner1Example.value),
    viewsToGenerate = List(Constant.SYSTEM_OWNER_VIEW_ID, Constant.SYSTEM_ACCOUNTANT_VIEW_ID,Constant.SYSTEM_AUDITOR_VIEW_ID),
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
    number = accountNumberExample.value,
    bankId = BankId(bankIdExample.value),
    lastUpdate = new Date(),
    branchId = branchIdExample.value,
    accountRoutings = List(AccountRouting(accountRoutingSchemeExample.value,
                                          accountRoutingAddressExample.value)),
    accountRules = Nil,
    accountHolder = ""
  )
  
  val outboundAdapterAuthInfo = OutboundAdapterAuthInfo(
    userId = Some(userIdExample.value),
    username = Some(usernameExample.value),
    linkedCustomers = Some(List(BasicLinkedCustomer(customerIdExample.value,customerNumberExample.value,legalNameExample.value))),
    userAuthContext = Some(List(BasicUserAuthContext(keyExample.value,valueExample.value))), //be set by obp from some endpoints.
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
          dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
        )),
        userOwners = List(InternalBasicUser(
          userId = userIdExample.value,
          emailAddress = emailExample.value,
          name = usernameExample.value
        )))))))

  val outboundAdapterConsenterInfo = OutboundAdapterAuthInfo(
    userId = Some(userIdExample.value),
    username = Some(usernameExample.value),
    linkedCustomers = Some(List(BasicLinkedCustomer(customerIdExample.value,customerNumberExample.value,legalNameExample.value))),
    userAuthContext = Some(List(BasicUserAuthContext(keyExample.value,valueExample.value))), //be set by obp from some endpoints.
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
          dateOfBirth=parseDate(dateOfBirthExample.value).getOrElse(sys.error("dateOfBirthExample.value is not validate date format.")),
        )),
        userOwners = List(InternalBasicUser(
          userId = userIdExample.value,
          emailAddress = emailExample.value,
          name = usernameExample.value
        )))))))
  
  val outboundAdapterCallContext = OutboundAdapterCallContext(
      correlationIdExample.value,
      Some(sessionIdExample.value),
      Some(consumerIdExample.value),
      generalContext = Some(List(BasicGeneralContext(keyExample.value,valueExample.value))), 
      Some(outboundAdapterAuthInfo),
      Some(outboundAdapterConsenterInfo)
    )
  
  val inboundAdapterCallContext = InboundAdapterCallContext(
    correlationIdExample.value,
    Some(sessionIdExample.value),
    Some(List(BasicGeneralContext(keyExample.value,valueExample.value)))
  )
  
  
  val inboundStatusMessage = InboundStatusMessage(
    source = sourceExample.value,
    status = statusExample.value,
    errorCode = errorCodeExample.value,
    text = textExample.value,
    duration= Some(BigDecimal(durationExample.value)),
  )

  val inboundStatus = Status("Status errorCode", List(inboundStatusMessage))

  val successStatus = Status("", List(inboundStatusMessage.copy(errorCode = "")))

  val inboundAdapterInfoInternal = InboundAdapterInfoInternal(
    errorCode ="",
    backendMessages = List(inboundStatusMessage),
    name = usernameExample.value,
    version = versionExample.value,
    git_commit = gitCommitExample.value,
    date = DateWithMsExampleString
  )

  val bankCommons = BankCommons(
      bankId = BankId(bankIdExample.value),
      shortName = "The Royal Bank of Scotland",
      fullName = "The Royal Bank of Scotland",
      logoUrl = "http://www.red-bank-shoreditch.com/logo.gif",
      websiteUrl = "http://www.red-bank-shoreditch.com",
      bankRoutingScheme = "OBP",
      bankRoutingAddress = "rbs",
      swiftBic = "String",
      nationalIdentifier = "String"
    )
  
  val customerFaceImage= CustomerFaceImage(
    date = DateWithDayExampleObject,
    url = urlExample.value
  )
  
  val creditRating= CreditRating(
    rating = ratingExample.value, 
    source = sourceExample.value
  )
  
  val creditLimit = CreditLimit(
    currency = currencyExample.value,
    amount = balanceAmountExample.value
  )
  
  val customerCommons = CustomerCommons(
    customerId = customerIdExample.value,
    bankId = bankIdExample.value,
    number = accountNumberExample.value,
    legalName = legalNameExample.value,
    mobileNumber = mobileNumberExample.value,
    email = emailExample.value,
    faceImage = customerFaceImage,
    dateOfBirth = DateWithDayExampleObject,
    relationshipStatus  =relationshipStatusExample.value,
    dependents = dependentsExample.value.toInt,
    dobOfDependents = List(DateWithDayExampleObject),
    highestEducationAttained = highestEducationAttainedExample.value,
    employmentStatus =employmentStatusExample.value,
    creditRating = creditRating,
    creditLimit = creditLimit,
    kycStatus = kycStatusExample.value.toBoolean,
    lastOkDate = DateWithDayExampleObject,
    title =titleExample.value,
    branchId = branchIdExample.value,
    nameSuffix = nameSuffixExample.value
  )

  val customerAttribute = CustomerAttributeCommons(
    bankId = BankId(customerCommons.bankId),
    customerId = CustomerId(customerCommons.customerId),
    customerAttributeId = "some_customer_attributeId_value",
    attributeType = CustomerAttributeType.INTEGER,
    name = "customer_attribute_field",
    value = "example_value"
  )
  
  val counterparty = Counterparty(
    nationalIdentifier= "", // This is the scheme a consumer would use to instruct a payment e.g. IBAN
    kind ="", // Type of bank account.
    counterpartyId = counterpartyIdExample.value,
    counterpartyName = counterpartyNameExample.value,
    thisBankId = BankId(bankIdExample.value), // i.e. the Account that sends/receives money to/from this Counterparty
    thisAccountId = AccountId(accountIdExample.value), // These 2 fields specify the account that uses this Counterparty
    otherBankRoutingScheme = bankRoutingSchemeExample.value, // This is the scheme a consumer would use to specify the bank e.g. BIC
    otherBankRoutingAddress= Some(bankRoutingAddressExample.value), // The (BIC) value e.g. 67895
    otherAccountRoutingScheme = accountRoutingSchemeExample.value, // This is the scheme a consumer would use to instruct a payment e.g. IBAN
    otherAccountRoutingAddress = Some(accountRoutingAddressExample.value), // The (IBAN) value e.g. 2349870987820374
    otherAccountProvider = otherAccountProviderExample.value , // hasBankId and hasAccountId would refer to an OBP account
    isBeneficiary = isBeneficiaryExample.value.toBoolean // True if the originAccount can send money to the Counterparty
  )
  
  val transaction = Transaction(
    `uuid`= transactionIdExample.value,
    id = TransactionId(transactionIdExample.value),
    thisAccount = bankAccountCommons,
    otherAccount = counterparty,
    transactionType = transactionTypeExample.value,
    amount = BigDecimal(balanceAmountExample.value),
    currency = currencyExample.value,
    description = Some(transactionDescriptionExample.value),
    startDate = DateWithDayExampleObject,
    finishDate = DateWithDayExampleObject,
    balance  = BigDecimal(balanceAmountExample.value)
  )
  
  val accountRouting = AccountRouting("","")
  val accountRule = AccountRule("","")
  
  val adapterImplementation = AdapterImplementation("- Core", 2)
  
  val allFields: List[AnyRef] =
    ReflectUtils.getValues(this, List(nameOf(allFields)))
      .filter(it => it != null && it.isInstanceOf[AnyRef])
      .map(_.asInstanceOf[AnyRef])
}
