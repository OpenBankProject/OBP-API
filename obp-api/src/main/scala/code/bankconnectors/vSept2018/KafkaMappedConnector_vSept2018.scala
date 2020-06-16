package code.bankconnectors.vSept2018

/*
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID.randomUUID

import code.api.APIFailure
import code.api.Constant._
import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import code.api.util._
import code.api.v2_1_0.TransactionRequestBodyCommonJSON
import code.bankconnectors._
import code.bankconnectors.vJune2017.{InternalCustomer, JsonFactory_vJune2017}
import code.bankconnectors.vMar2017._
import code.context.UserAuthContextProvider
import code.customer._
import code.kafka.{KafkaHelper, Topics}
import code.model._
import code.model.dataAccess._
import code.users.Users
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model.{AmountOfMoneyTrait, CounterpartyTrait, CreditRatingTrait, _}
import com.sksamuel.avro4s.SchemaFor
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb
import net.liftweb.common.{Box, _}
import net.liftweb.json.{MappingException, parse}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.{ApiVersion, RequiredFieldValidation}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.reflect.runtime.universe._

trait KafkaMappedConnector_vSept2018 extends Connector with KafkaHelper with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{CustomerFaceImage, CreditLimit, CreditRating, AmountOfMoney}

  implicit override val nameOfConnector = KafkaMappedConnector_vSept2018.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound...) are defined below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Sept2018"

  // This is tricky for now. Because for GatewayLogin, we do not create any user for the first CBS Call. 
  // We get the username from gatewayLogin token -> call CBS (CBS checked the user and return the response) -> api create the users.  
  def getAuthInfoFirstCbsCall (username: String, callContext: Option[CallContext]): Box[AuthInfo]=
    for{
      cc <- tryo {callContext.get} ?~! NoCallContext
      gatewayLoginRequestPayLoad <- cc.gatewayLoginRequestPayload orElse (
        Some(PayloadOfJwtJSON(login_user_name = "",
                         is_first = false,
                         app_id = "",
                         app_name = "",
                         time_stamp = "",
                         cbs_token = Some(""),
                         cbs_id = "",
                         session_id = Some(""))))
      isFirst = gatewayLoginRequestPayLoad.is_first
      correlationId = cc.correlationId
      sessionId = cc.sessionId.getOrElse("")
      //Here, need separate the GatewayLogin and other Types, because of for Gatewaylogin, there is no user here. Others, need sign up user in OBP side. 
      basicUserAuthContexts <- cc.gatewayLoginRequestPayload match {
        case None => 
          for{
            user <- Users.users.vend.getUserByUserName(username) ?~! "getAuthInfoFirstCbsCall: can not get user object here."
            userAuthContexts<- UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(user.userId)?~! "getAuthInfoFirstCbsCall: can not get userAuthContexts object here."
            basicUserAuthContexts = JsonFactory_vSept2018.createBasicUserAuthContextJson(userAuthContexts)
          } yield
            basicUserAuthContexts
        case _ => Full(Nil)
      }
    } yield{
      AuthInfo("",username, "", isFirst, correlationId, sessionId, Nil, basicUserAuthContexts, Nil)
    }
  
  def getAuthInfo (callContext: Option[CallContext]): Box[AuthInfo]=
    for{
      cc <- tryo {callContext.get} ?~! s"$NoCallContext. inside the getAuthInfo method "
      user <- cc.user ?~! "getAuthInfo: User is not in side CallContext!"
      username =user.name
      currentResourceUserId = user.userId
      gatewayLoginPayLoad <- cc.gatewayLoginRequestPayload orElse (
        Some(PayloadOfJwtJSON(login_user_name = "",
                         is_first = false,
                         app_id = "",
                         app_name = "",
                         time_stamp = "",
                         cbs_token = Some(""),
                         cbs_id = "",
                         session_id = Some(""))))
      cbs_token <- gatewayLoginPayLoad.cbs_token.orElse(Full(""))
      isFirst <- tryo(gatewayLoginPayLoad.is_first) ?~! "getAuthInfo:is_first can not be got from gatewayLoginPayLoad!"
      correlationId <- tryo(cc.correlationId) ?~! "getAuthInfo: User id can not be got from callContext!"
      sessionId <- tryo(cc.sessionId.getOrElse(""))?~! "getAuthInfo: session id can not be got from callContext!"
      permission <- Views.views.vend.getPermissionForUser(user)?~! "getAuthInfo: No permission for this user"
      views <- tryo(permission.views)?~! "getAuthInfo: No views for this user"
      linkedCustomers <- tryo(CustomerX.customerProvider.vend.getCustomersByUserId(user.userId))?~! "getAuthInfo: No linked customers for this user"
      likedCustomersBasic = JsonFactory_vSept2018.createBasicCustomerJson(linkedCustomers)
      userAuthContexts<- UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(user.userId) ?~! "getAuthInfo: No userAuthContexts for this user"
      basicUserAuthContexts = JsonFactory_vSept2018.createBasicUserAuthContextJson(userAuthContexts)
      authViews<- tryo(
        for{
          view <- views              //TODO, need double check whether these data come from OBP side or Adapter.
          (account, callContext )<- code.bankconnectors.LocalMappedConnector.getBankAccountLegacy(view.bankId, view.accountId, Some(cc)) ?~! {s"getAuthInfo: $BankAccountNotFound"}
          internalCustomers = JsonFactory_vSept2018.createCustomersJson(account.customerOwners.toList)
          internalUsers = JsonFactory_vSept2018.createUsersJson(account.userOwners.toList)
          viewBasic = ViewBasic(view.viewId.value, view.name, view.description)
          accountBasic =  AccountBasic(
            account.accountId.value, 
            account.accountRoutings, 
            internalCustomers.customers,
            internalUsers.users)
        }yield 
          AuthView(viewBasic, accountBasic)
      )?~! "getAuthInfo: No authViews for this user"
    } yield{
      AuthInfo(currentResourceUserId, username, cbs_token, isFirst, correlationId, sessionId, likedCustomersBasic, basicUserAuthContexts, authViews)
    }



  val outboundAdapterCallContext = OutboundAdapterCallContext(
    correlationId = "string",
    sessionId = Option("string"),
    consumerId = Option("string"),
    generalContext = Option(List(BasicGeneralContext(key = "string",
      value = "string"))),
    outboundAdapterAuthInfo = Option(OutboundAdapterAuthInfo(userId = Option("string"),
      username = Option("string"),
      linkedCustomers = Option(List(BasicLinkedCustomer(customerId = "string",
        customerNumber = "string",
        legalName = "string"))),
      userAuthContext = Option(List(BasicUserAuthContext(key = "string",
        value = "string"))),
      authViews = Option(List(AuthView(view = ViewBasic(id = "string",
        name = "string",
        description = "string"),
        account = AccountBasic(id = "string",
          accountRoutings = List(AccountRouting(scheme = "string",
            address = "string")),
          customerOwners = List(InternalBasicCustomer(bankId = "string",
            customerId = "string",
            customerNumber = "string",
            legalName = "string",
            dateOfBirth = new Date())),
          userOwners = List(InternalBasicUser(userId = "string",
            emailAddress = "string",
            name = "string")))))))))

  val inboundAdapterCallContext = InboundAdapterCallContext(
    correlationId = "string",
    sessionId = Option("string"),
    generalContext = Option(List(BasicGeneralContext(key = "string",
      value = "string"))))
  
  val viewBasicExample = ViewBasic("owner","Owner", "This is the owner view")

  val internalBasicCustomerExample = InternalBasicCustomer(
    bankId = ExampleValue.bankIdExample.value,
    customerId = customerIdExample.value,
    customerNumber = customerNumberExample.value,
    legalName = legalNameExample.value,
    dateOfBirth = DateWithSecondsExampleObject
  )
  val internalBasicUserExample = InternalBasicUser(
    userId = userIdExample.value,
    emailAddress = emailExample.value,
    name = legalNameExample.value // Assuming this is the legal name
  )
  val accountBasicExample = AccountBasic(
    id = accountIdExample.value,
    List(AccountRouting("AccountNumber",accountNumberExample.value),
         AccountRouting("IBAN",ibanExample.value)),
    List(internalBasicCustomerExample),
    List(internalBasicUserExample)
  )
  val accountRoutingExample = AccountRouting("AccountNumber",accountNumberExample.value)
  val authViewExample = AuthView(viewBasicExample, accountBasicExample)
  val authViewsExample = List(authViewExample)
  val basicCustomerExample = BasicCustomer(customerIdExample.value,customerNumberExample.value,legalNameExample.value)
  val basicCustomersExample = List(basicCustomerExample)
  val basicUserAuthContextExample1 = BasicUserAuthContext("CUSTOMER_NUMBER",customerNumberExample.value)
  val basicUserAuthContextExample2 = BasicUserAuthContext("TOKEN","qieuriopwoir987ASYDUFISUYDF678u")
  val BasicUserAuthContextsExample = List(basicUserAuthContextExample1, basicUserAuthContextExample2)
  val authInfoExample = AuthInfo(
    userId = userIdExample.value,
    username = usernameExample.value,
    cbsToken = cbsTokenExample.value,
    isFirst = true,
    correlationId = correlationIdExample.value,
    sessionId = userIdExample.value,
    basicCustomersExample,
    BasicUserAuthContextsExample,
    authViewsExample
  )
  val inboundStatusMessagesExample = List(InboundStatusMessage("ESB", "Success", "0", "OK"))
  val errorCodeExample = ""//This should be Empty String, mean no error in Adapter side. 
  val statusExample = Status(errorCodeExample, inboundStatusMessagesExample)
  val inboundAuthInfoExample = InboundAuthInfo(cbsToken=cbsTokenExample.value, sessionId = sessionIdExample.value)



  val inboundAccountSept2018Example = InboundAccountSept2018(
    cbsErrorCodeExample.value,
    cbsToken = cbsTokenExample.value,
    bankId = bankIdExample.value,
    branchId = branchIdExample.value,
    accountId = accountIdExample.value,
    accountNumber = accountNumberExample.value,
    accountType = accountTypeExample.value,
    balanceAmount = balanceAmountExample.value,
    balanceCurrency = currencyExample.value,
    owners = owner1Example.value :: owner1Example.value :: Nil,
    viewsToGenerate = "_Public" :: "Accountant" :: "Auditor" :: Nil,
    bankRoutingScheme = bankRoutingSchemeExample.value,
    bankRoutingAddress = bankRoutingAddressExample.value,
    branchRoutingScheme = branchRoutingSchemeExample.value,
    branchRoutingAddress = branchRoutingAddressExample.value,
    accountRoutingScheme = accountRoutingSchemeExample.value,
    accountRoutingAddress = accountRoutingAddressExample.value,
    accountRouting = Nil,
    accountRules = Nil)



  messageDocs += MessageDoc(
    process = s"obp.getAdapterInfo",
    messageFormat = messageFormat,
    description = "Gets information about the active general (non bank specific) Adapter that is responding to messages sent by OBP.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetAdapterInfo.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetAdapterInfo.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetAdapterInfo(date = DateWithSecondsExampleString)
    ),
    exampleInboundMessage = (
      InboundAdapterInfo(
        InboundAdapterInfoInternal(
          errorCodeExample,
          inboundStatusMessagesExample,
          name = "Obp-Kafka-South",
          version = "Sept2018",
          git_commit = gitCommitExample.value,
          date = DateWithSecondsExampleString
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetAdapterInfo]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundAdapterInfoInternal]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  
  override def getAdapterInfo(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
    val req = OutboundGetAdapterInfo(DateWithSecondsExampleString)
    processRequest[InboundAdapterInfo](req) map { inbound =>
      inbound.map(_.data).map(inboundAdapterInfoInternal =>(inboundAdapterInfoInternal, callContext))
    }
  }

  messageDocs += MessageDoc(
    process = "obp.getUser",
    messageFormat = messageFormat,
    description = "Gets the User as identifiedgetAdapterInfo by the the credentials (username and password) supplied.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetUserByUsernamePassword.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetUserByUsernamePassword.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetUserByUsernamePassword(
        authInfoExample,
        password = "2b78e8"
      )
    ),
    exampleInboundMessage = (
      InboundGetUserByUsernamePassword(
        inboundAuthInfoExample,
        InboundValidatedUser(
          errorCodeExample,
          inboundStatusMessagesExample,
          email = "susan.uk.29@example.com",
          displayName = "susan"
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetUserByUsernamePassword]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetUserByUsernamePassword]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("User", 1))

  )
  //TODO This method  is not used in api level, so not CallContext here for now..
  override def getUser(username: String, password: String): Box[InboundUser] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(userTTL second) {

        val req = OutboundGetUserByUsernamePassword(AuthInfo("", username, ""), password = password)
        val InboundFuture = processRequest[InboundGetUserByUsernamePassword](req) map { inbound =>
          inbound.map(_.data).map(inboundValidatedUser =>(InboundUser(inboundValidatedUser.email, password, inboundValidatedUser.displayName)))
        }
        getValueFromFuture(InboundFuture)
      }
    }
  }("getUser")


  messageDocs += MessageDoc(
    process = s"obp.getBanks",
    messageFormat = messageFormat,
    description = "Gets the banks list on this OBP installation.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetBanks.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetBanks.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetBanks(authInfoExample)
    ),
    exampleInboundMessage = (
      InboundGetBanks(
        inboundAuthInfoExample,
        Status(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample),
        InboundBank(
          bankId = bankIdExample.value,
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )  :: Nil
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBanks]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBanks]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 2))
  )
  override def getBanksLegacy(callContext: Option[CallContext]) = saveConnectorMetric {
    getValueFromFuture(getBanks(callContext: Option[CallContext]))
  }("getBanks")

  override def getBanks(callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val req = OutboundGetBanks(AuthInfo())

        logger.debug(s"Kafka getBanksFuture says: req is: $req")

        processRequest[InboundGetBanks](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              Full((inboundData.data.map((new Bank2(_)))))
            case Full(inbound) if (inbound.status.hasError) =>
              Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }

          (boxedResult, callContext)
        }
      }}}("getBanks")

  messageDocs += MessageDoc(
    process = "obp.getBank",
    messageFormat = messageFormat,
    description = "Get a specific Bank as specified by bankId",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetBank.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetBank.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetBank(authInfoExample,"bankId")
    ),
    exampleInboundMessage = (
      InboundGetBank(
        inboundAuthInfoExample,
        Status(
          errorCodeExample,
          inboundStatusMessagesExample),
        InboundBank(
          bankId = bankIdExample.value,
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBank]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBank]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 5))
  )
  override def getBankLegacy(bankId: BankId, callContext: Option[CallContext]) =  saveConnectorMetric {
    getValueFromFuture(getBank(bankId: BankId, callContext: Option[CallContext]))
  }("getBank")

  override def getBank(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(bankTTL second) {
        val req = OutboundGetBank(
          authInfo = AuthInfo(),
          bankId = bankId.toString
        )
        logger.debug(s"Kafka getBank Req says:  is: $req")

        processRequest[InboundGetBank](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              Full(new Bank2(inboundData.data))
            case Full(inbound) if (inbound.status.hasError) =>
              Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult, callContext)
        }
      }
    }
  }("getBank")

  messageDocs += MessageDoc(
    process = "obp.getBankAccountsForUser",
    messageFormat = messageFormat,
    description = "Gets the list of accounts available to the User. This call sends authInfo including username.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetAccounts.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(InboundGetAccounts.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetAccounts(
        authInfoExample,
        InternalBasicCustomers(customers =List(internalBasicCustomerExample)))
    ),
    exampleInboundMessage = (
      InboundGetAccounts(
        inboundAuthInfoExample, 
        statusExample,
        inboundAccountSept2018Example :: Nil)
    ),
    adapterImplementation = Some(AdapterImplementation("Accounts", 5))
  )
  override def getBankAccountsForUserLegacy(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = saveConnectorMetric{
    getValueFromFuture(getBankAccountsForUser(username: String, callContext: Option[CallContext]))
  }("getBankAccounts")

  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]):  Future[Box[(List[InboundAccountSept2018], Option[CallContext])]] = saveConnectorMetric{
     /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountsTTL second) {

        val req = OutboundGetAccounts(
          getAuthInfoFirstCbsCall(username, callContext).openOrThrowException(s"$attemptedToOpenAnEmptyBox getBankAccountsFuture.callContext is Empty !"),
          InternalBasicCustomers(Nil)
        )
        logger.debug(s"Kafka getBankAccountsFuture says: req is: $req")

        processRequest[InboundGetAccounts](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              Full(inboundData.data)
            case Full(inbound) if (inbound.status.hasError) =>
              Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult, callContext)
        }
      }
    }
  }("getBankAccountsFuture")
  
  messageDocs += MessageDoc(
    process = "obp.getBankAccount",
    messageFormat = messageFormat,
    description = "Get a single Account as specified by the bankId and accountId.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetAccountbyAccountID.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetAccountbyAccountID.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetAccountbyAccountID(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = (
      InboundGetAccountbyAccountID(
        inboundAuthInfoExample,
        statusExample,
        Some(inboundAccountSept2018Example))),
      adapterImplementation = Some(AdapterImplementation("Accounts", 7))
  )
  override def getBankAccountLegacy(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext]) = saveConnectorMetric {
      getValueFromFuture(getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext]))._1.map(bankAccount =>(bankAccount, callContext))
  }("getBankAccount")

  override def getBankAccount(bankId : BankId, accountId : AccountId, callContext: Option[CallContext])  = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){

        val req = OutboundGetAccountbyAccountID(
          authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
          bankId.value,
          accountId.value
        )
        logger.debug(s"Kafka getBankAccountFuture says: req is: $req")
        
        processRequest[InboundGetAccountbyAccountID](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              Full(new BankAccountSept2018(inboundData.data.get))
            case Full(inbound) if (inbound.status.hasError) =>
              Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult, callContext)
        }

      }
    }
  }("getBankAccount")

  messageDocs += MessageDoc(
    process = "obp.getBankAccountsHeld",
    messageFormat = messageFormat,
    description = "Get Accounts held by the current User if even the User has not been assigned the owner View yet.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetBankAccountsHeld.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetBankAccountsHeld.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetBankAccountsHeld(
        authInfoExample,
        List(
          BankIdAccountId(BankId(bankIdExample.value),
          AccountId(accountIdExample.value))
        )
      )),
    exampleInboundMessage = (
      InboundGetBankAccountsHeld(
        inboundAuthInfoExample,
        statusExample,
        List(AccountHeld(
          accountIdExample.value,
          bankIdExample.value,
          number = accountNumberExample.value,
          accountRoutings =List(accountRoutingExample)
          
        )))),
    adapterImplementation = Some(AdapterImplementation("Accounts", 1))
  )
  override def getBankAccountsHeld(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext])  = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountsTTL second){

        val req = OutboundGetBankAccountsHeld(
          authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
          bankIdAccountIds
        )
        logger.debug(s"Kafka getBankAccountsHeldFuture says: req is: $req")

        processRequest[InboundGetBankAccountsHeld](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              Full(inboundData.data)
            case Full(inbound) if (inbound.status.hasError) =>
              Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult, callContext)
        }
      }
    }
  }

  messageDocs += MessageDoc(
    process = "obp.checkBankAccountExists",
    messageFormat = messageFormat,
    description = "Check a bank Account exists - as specified by bankId and accountId.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundCheckBankAccountExists.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundCheckBankAccountExists.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundCheckBankAccountExists(
        authInfoExample,
        bankIdExample.value,
        accountIdExample.value
      )
    ),
    exampleInboundMessage = (
      InboundCheckBankAccountExists(
        inboundAuthInfoExample,
        statusExample,
        Some(inboundAccountSept2018Example))
    ),
  adapterImplementation = Some(AdapterImplementation("Accounts", 4))
  )
  override def checkBankAccountExistsLegacy(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext])= saveConnectorMetric {
    getValueFromFuture(checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]))._1.map(bankAccount =>(bankAccount, callContext))
  }("getBankAccount")

  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) = {
  /**
    * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
    * is just a temporary value filed with UUID values in order to prevent any ambiguity.
    * The real value will be assigned by Macro during compile time at this line of a code:
    * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
    */
  var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
  CacheKeyFromArguments.buildCacheKey {
    Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
      val req = OutboundCheckBankAccountExists(
        authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
        bankId = bankId.toString,
        accountId = accountId.value
        )       
      
      logger.debug(s"Kafka checkBankAccountExists says: req is: $req")
        
      processRequest[InboundCheckBankAccountExists](req) map { inbound =>
      val boxedResult = inbound match {
        case Full(inboundData) if (inboundData.status.hasNoError) =>
          Full((new BankAccountSept2018(inboundData.data.head)))
        case Full(inbound) if (inbound.status.hasError) =>
          Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
        case failureOrEmpty: Failure => failureOrEmpty
      }
         
      (boxedResult, callContext)
    }
  }}}
  
  messageDocs += MessageDoc(
    process = "obp.getCoreBankAccounts",
    messageFormat = messageFormat,
    description = "Get bank Accounts available to the User (without Metadata)",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCoreBankAccounts.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCoreBankAccounts.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCoreBankAccounts(
        authInfoExample,
        List(BankIdAccountId(BankId(bankIdExample.value),
        AccountId(accountIdExample.value))
      )
    )),
    exampleInboundMessage = (
      InboundGetCoreBankAccounts(
        inboundAuthInfoExample,
        List(InternalInboundCoreAccount(
          errorCodeExample, 
          inboundStatusMessagesExample,
          accountIdExample.value,
          labelExample.value,
          bankIdExample.value,
          accountTypeExample.value,
          List(accountRoutingExample)
          )))),
    adapterImplementation = Some(AdapterImplementation("Accounts", 1))
  )
  override def getCoreBankAccountsLegacy(bankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])]  = saveConnectorMetric{
    getValueFromFuture(getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]))
  }("getBankAccounts")

  override def getCoreBankAccounts(bankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
        val authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext)
        val req = OutboundGetCoreBankAccounts(
          authInfo = authInfo,
          bankIdAccountIds
        )
        processRequest[InboundGetCoreBankAccounts](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.data.head.errorCode=="") =>
              Full(inboundData.data.map(x =>CoreAccount(x.id,x.label,x.bankId,x.accountType, x.accountRoutings)))
            case Full(inboundData) if (inboundData.data.head.errorCode != "") =>
              Failure("INTERNAL-"+ inboundData.data.head.errorCode+". + CoreBank-Status:" + inboundData.data.head.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult, callContext)
        }
      }
    }
  }("getCoreBankAccountsFuture")


  val exampleInternalTransactionSept2018 = InternalTransaction_vSept2018(
    transactionId = transactionIdExample.value,
    accountId = accountIdExample.value,
    amount = transactionAmountExample.value,
    bankId = bankIdExample.value,
    completedDate = transactionCompletedDateExample.value,
    counterpartyId = counterpartyIdExample.value,
    counterpartyName = counterpartyNameExample.value,
    currency = currencyExample.value,
    description = transactionDescriptionExample.value,
    newBalanceAmount = balanceAmountExample.value,
    newBalanceCurrency = currencyExample.value,
    postedDate = transactionPostedDateExample.value,
    `type` = transactionTypeExample.value,
    userId = userIdExample.value)



  messageDocs += MessageDoc(
    process = "obp.getTransactions",
    messageFormat = messageFormat,
    description = "Get Transactions for an Account specified by bankId and accountId. Pagination is achieved with limit, fromDate and toDate.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetTransactions.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetTransactions.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetTransactions(
        authInfo = authInfoExample,
        bankId = bankIdExample.value,
        accountId = accountIdExample.value,
        limit =100,
        fromDate="DateWithSecondsExampleObject",
        toDate="DateWithSecondsExampleObject"
      )
    ),
    exampleInboundMessage = (
      InboundGetTransactions(
        inboundAuthInfoExample,
        statusExample,
        exampleInternalTransactionSept2018::Nil)),
    adapterImplementation = Some(AdapterImplementation("Transactions", 10))
  )
  // TODO Get rid on these param lookups and document.
  override def getTransactionsLegacy(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]) = saveConnectorMetric {
    val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => date.toString }.headOption.getOrElse(APIUtil.DefaultFromDate.toString)
    val toDate = queryParams.collect { case OBPToDate(date) => date.toString }.headOption.getOrElse(APIUtil.DefaultToDate.toString)

    // TODO What about offset?
    val req = OutboundGetTransactions(
      authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
      bankId = bankId.toString,
      accountId = accountId.value,
      limit = limit,
      fromDate = fromDate,
      toDate = toDate
    )

    //Note: because there is `queryParams: List[OBPQueryParam]` in getTransactions, so create the getTransactionsCached to cache data.
    def getTransactionsCached(req: OutboundGetTransactions): Future[(Box[List[Transaction]], Option[CallContext])] = {
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionsTTL second) {
          logger.debug(s"Kafka getTransactions says: req is: $req")

          processRequest[InboundGetTransactions](req) map { inbound =>
            val boxedResult = inbound match {
              case Full(inboundData) if (inboundData.status.hasNoError) =>
                val bankAccountAndCallContext = checkBankAccountExistsLegacy(BankId(inboundData.data.head.bankId), AccountId(inboundData.data.head.accountId), callContext)

                val res = for {
                  internalTransaction <- inboundData.data
                  thisBankAccount <- bankAccountAndCallContext.map(_._1) ?~! ErrorMessages.BankAccountNotFound
                  transaction <- createInMemoryTransaction(thisBankAccount, internalTransaction)
                } yield {
                  transaction
                }
                Full(res)
              case Full(inboundData) if (inboundData.status.hasError) =>
                Failure("INTERNAL-"+ inboundData.status.errorCode+". + CoreBank-Status:" + inboundData.status.backendMessages)
              case failureOrEmpty: Failure => failureOrEmpty
            }
            (boxedResult, callContext)
          }
        }
      }
    }
    getValueFromFuture(getTransactionsCached(req))._1.map(bankAccount =>(bankAccount, callContext))
  }("getTransactions")
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, queryParams:  List[OBPQueryParam], callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionsTTL second) {
      
    val limit = queryParams.collect { case OBPLimit(value) => value}.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => date.toString}.headOption.getOrElse(APIUtil.DefaultFromDate.toString)
    val toDate = queryParams.collect { case OBPToDate(date) => date.toString}.headOption.getOrElse(APIUtil.DefaultToDate.toString)
  
    val req = OutboundGetTransactions(
      authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
      bankId = bankId.toString,
      accountId = accountId.value,
      limit = limit,
      fromDate = fromDate,
      toDate = toDate
    )
    logger.debug(s"Kafka getTransactions says: req is: $req")

    processRequest[InboundGetTransactions](req) map { inbound =>
      val boxedResult: Box[List[TransactionCore]] = inbound match {
        case Full(inboundGetTransactions) if (inboundGetTransactions.status.hasNoError) =>
          for{
            (thisBankAccount, callContext) <- checkBankAccountExistsLegacy(BankId(inboundGetTransactions.data.head.bankId), AccountId(inboundGetTransactions.data.head.accountId), callContext) ?~! ErrorMessages.BankAccountNotFound
            transaction <- createInMemoryTransactionsCore(thisBankAccount, inboundGetTransactions.data)
          } yield {
            (transaction)
          }
        case Full(inbound) if (inbound.status.hasError) =>
          Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
        case failureOrEmpty: Failure => failureOrEmpty
      }
      (boxedResult, callContext)
    }}}}("getTransactions")
  
  messageDocs += MessageDoc(
    process = "obp.getTransaction",
    messageFormat = messageFormat,
    description = "Get a single Transaction specified by bankId, accountId and transactionId",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetTransaction.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetTransaction.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetTransaction(
        authInfoExample,
        "bankId",
        "accountId",
        "transactionId"
      )
    ),
    exampleInboundMessage = (
      InboundGetTransaction(inboundAuthInfoExample, statusExample, Some(exampleInternalTransactionSept2018))
    ),
    adapterImplementation = Some(AdapterImplementation("Transactions", 11))
  )
  override def getTransactionLegacy(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = saveConnectorMetric{
    Await.result(getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]), TIMEOUT)._1.map(bankAccount =>(bankAccount, callContext))
  }("getTransaction")

  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionTTL second) {

        val authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext)
        val req = OutboundGetTransaction(authInfo, bankId.value, accountId.value, transactionId.value)
        processRequest[InboundGetTransaction](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              for {
                (bankAccount, callContext) <- checkBankAccountExistsLegacy(BankId(inboundData.data.get.bankId), AccountId(inboundData.data.get.accountId), callContext) ?~! ErrorMessages.BankAccountNotFound
                transaction: Transaction <- createInMemoryTransaction(bankAccount, inboundData.data.get)
              } yield {
                (transaction, callContext)
              }
            case Full(inboundData) if (inboundData.status.hasError) =>
              Failure("INTERNAL-" + inboundData.status.errorCode + ". + CoreBank-Status:" + inboundData.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult)
        }
      }
    }}("getTransaction")
  
  messageDocs += MessageDoc(
    process = "obp.createChallenge",
    messageFormat = messageFormat,
    description = "Create a Security Challenge that may be used to complete a Transaction Request.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundCreateChallengeSept2018.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundCreateChallengeSept2018.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundCreateChallengeSept2018(
        authInfoExample,
        bankId = bankIdExample.value,
        accountId = accountIdExample.value,
        userId = userIdExample.value,
        username = usernameExample.value,
        transactionRequestType = "SANDBOX_TAN",
        transactionRequestId = "1234567"
      )
    ),
    exampleInboundMessage = (
      InboundCreateChallengeSept2018(
        inboundAuthInfoExample,
        InternalCreateChallengeSept2018(
          errorCodeExample,
          inboundStatusMessagesExample,
          "1234"
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundCreateChallengeSept2018]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundCreateChallengeSept2018]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("Payments", 20))
  )
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, scaMethod: Option[SCA], callContext: Option[CallContext]) = {
    val authInfo = getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox)
    val req = OutboundCreateChallengeSept2018(
      authInfo = authInfo, 
      bankId = bankId.value,
      accountId = accountId.value,
      userId = userId,
      username = AuthUser.getCurrentUserUsername,
      transactionRequestType = transactionRequestType.value,
      transactionRequestId = transactionRequestId
    )
    
    logger.debug(s"Kafka createChallenge Req says:  is: $req")
    
    val future = for {
     res <- processToFuture[OutboundCreateChallengeSept2018](req) map {
       f =>
         try {
           f.extract[InboundCreateChallengeSept2018]
         } catch {
           case e: Exception =>
             val received = liftweb.json.compactRender(f)
             val expected = SchemaFor[InboundCreateChallengeSept2018]().toString(false)
             val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundCreateChallengeSept2018 class with the Message Doc : You received this ($received). We expected this ($expected)"
             sendOutboundAdapterError(error)
             throw new MappingException(error, e)
         }
       } map { x => (x.inboundAuthInfo, x.data) }
    } yield {
     Full(res)
    }
    
    val res = future map {
      case Full((authInfo,x)) if (x.errorCode=="")  =>
        (Full(x.answer), callContext)
      case Full((authInfo, x)) if (x.errorCode!="") =>
        (Failure("INTERNAL-"+ x.errorCode+". + CoreBank-Status:"+ x.backendMessages), callContext)
      case _ =>
        (Failure(ErrorMessages.UnknownError), callContext)
    }
    res
  }
  
  messageDocs += MessageDoc(
    process = "obp.createCounterparty",
    messageFormat = messageFormat,
    description = "Create Counterparty",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundCreateCounterparty.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundCreateCounterparty.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundCreateCounterparty(
        authInfoExample,
        OutboundCounterparty(
          name = "name",
          description = "description",
          createdByUserId = "createdByUserId",
          thisBankId = "thisBankId",
          thisAccountId = "thisAccountId",
          thisViewId = "thisViewId",
          otherAccountRoutingScheme = "otherAccountRoutingScheme",
          otherAccountRoutingAddress = "otherAccountRoutingAddress",
          otherAccountSecondaryRoutingScheme = "otherAccountSecondaryRoutingScheme",
          otherAccountSecondaryRoutingAddress = "otherAccountSecondaryRoutingAddress",
          otherBankRoutingScheme = "otherBankRoutingScheme",
          otherBankRoutingAddress = "otherBankRoutingAddress",
          otherBranchRoutingScheme = "otherBranchRoutingScheme",
          otherBranchRoutingAddress = "otherBranchRoutingAddress",
          isBeneficiary = true,
          // Why is this not a list as in inbound?
          bespoke = CounterpartyBespoke("key","value") ::Nil
        )
      )
    ),
    exampleInboundMessage = (
      InboundCreateCounterparty(
        inboundAuthInfoExample, 
        statusExample,
        Some(InternalCounterparty(
          createdByUserId= "String", 
          name= "String", 
          thisBankId= "String", 
          thisAccountId= "String",
          thisViewId= "String",
          counterpartyId= "String", 
          otherAccountRoutingScheme= "String", 
          otherAccountRoutingAddress= "String", 
          otherBankRoutingScheme= "String", 
          otherBankRoutingAddress= "String",
          otherBranchRoutingScheme= "String", 
          otherBranchRoutingAddress= "String", 
          isBeneficiary = false,
          description= "String",
          otherAccountSecondaryRoutingScheme= "String", 
          otherAccountSecondaryRoutingAddress= "String",
          bespoke =  List(CounterpartyBespoke(
                          key = "String",
                          value = "String"
                        )))))
    ),
    adapterImplementation = Some(AdapterImplementation("Payments", 5))
  )
  override def createCounterparty(
    name: String,
    description: String,
    createdByUserId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    isBeneficiary:Boolean,
    bespoke: List[CounterpartyBespoke], 
    callContext: Option[CallContext] = None) = {
  
     val  authInfo = getAuthInfo(callContext).openOrThrowException(s"$NoCallContext for createCounterparty method")
     val  req  = OutboundCreateCounterparty(
        authInfo = authInfo,
        counterparty = OutboundCounterparty(
        name: String,
        description: String,
        createdByUserId: String,
        thisBankId: String,
        thisAccountId: String,
        thisViewId: String,
        otherAccountRoutingScheme: String,
        otherAccountRoutingAddress: String,
        otherAccountSecondaryRoutingScheme: String,
        otherAccountSecondaryRoutingAddress: String,
        otherBankRoutingScheme: String,
        otherBankRoutingAddress: String,
        otherBranchRoutingScheme: String,
        otherBranchRoutingAddress: String,
        isBeneficiary:Boolean,
        bespoke: List[CounterpartyBespoke])
      )

    val counterpartyFuture = processRequest[InboundCreateCounterparty](req) map { inbound =>
      val boxedResult = inbound match {
        case Full(inboundDate) if (inboundDate.status.hasNoError) =>
          Full(inboundDate.data.get)
        case Full(inboundDate) if (inboundDate.status.hasError) =>
          Failure("INTERNAL-" + inboundDate.status.errorCode + ". + CoreBank-Status:" + inboundDate.status.backendMessages)
        case failureOrEmpty: Failure => failureOrEmpty
      }
      (boxedResult)
    }
      getValueFromFuture(counterpartyFuture).map(counterparty => (counterparty, callContext))
      
  }
  
  messageDocs += MessageDoc(
    process = "obp.getTransactionRequests210",
    messageFormat = messageFormat,
    description = "Get Transaction Requests",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetTransactionRequests210.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetTransactionRequests210.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetTransactionRequests210(
        authInfoExample,
        OutboundTransactionRequests(
          "accountId: String",
          "accountType: String",
          "currency: String",
          "iban: String",
          "number: String",
          "bankId: BankId",
          "branchId: String",
          "accountRoutingScheme: String",
          "accountRoutingAddress: String"
        )
      )
    ),
    exampleInboundMessage = (
      InboundGetTransactionRequests210(
        inboundAuthInfoExample, 
        statusExample,
        List(
          TransactionRequest(
          id = TransactionRequestId("id"),
          `type` = "String",
          from = TransactionRequestAccount("10", "12"),
          body = SwaggerDefinitionsJSON.transactionRequestBodyAllTypes,
          transaction_ids = "",
          status = "COMPLETED",
          start_date = DateWithSecondsExampleObject,
          end_date = DateWithSecondsExampleObject,
          challenge = TransactionRequestChallenge("", 0, ""),
          charge = TransactionRequestCharge(
            "", 
            AmountOfMoney(
              currencyExample.value,
              transactionAmountExample.value)
          ),
          charge_policy = "",
          counterparty_id = CounterpartyId(""),
          name = "name",
          this_bank_id = BankId("10"),
          this_account_id = AccountId("1"),
          this_view_id = ViewId(""),
          other_account_routing_scheme = "",
          other_account_routing_address = "",
          other_bank_routing_scheme = "",
          other_bank_routing_address = "",
          is_beneficiary = false)
        )
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Payments", 10))
  )
  override def getTransactionRequests210(user : User, fromAccount : BankAccount, callContext: Option[CallContext] = None)  = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionRequests210TTL second){

        val box = for {
          authInfo <- getAuthInfo(callContext)
          req = OutboundGetTransactionRequests210(
            authInfo = authInfo,
            counterparty = OutboundTransactionRequests(
            accountId = fromAccount.accountId.value,
            accountType = fromAccount.accountType,
            currency = fromAccount.currency,
            iban = fromAccount.iban.getOrElse(""),
            number = fromAccount.number,
            bankId = fromAccount.bankId.value,
            branchId = fromAccount.bankId.value,
            accountRoutingScheme = fromAccount.accountRoutingScheme,
            accountRoutingAddress= fromAccount.accountRoutingAddress)
          )
          _ <- Full(logger.debug(s"Kafka getTransactionRequests210 Req says: is: $req"))
          kafkaMessage <- processToBox(req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetTransactionRequests210]().toString(false)
          inboundGetTransactionRequests210 <- tryo{kafkaMessage.extract[InboundGetTransactionRequests210]} ?~! s"$InvalidConnectorResponseForGetTransactionRequests210, $InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetTransactionRequests210 class with the Message Doc : You received this ($received). We expected this ($expected)"
          (internalGetTransactionRequests, status) <- Full(inboundGetTransactionRequests210.data, inboundGetTransactionRequests210.status)
        } yield{
          (internalGetTransactionRequests, status)
        }
        logger.debug(s"Kafka getTransactionRequests210 Res says: is: $box")

        val res = box match {
          case Full((data, status)) if (status.errorCode=="")  =>
            //For consistency with sandbox mode, we need combine obp transactions in database and adapter transactions
            val transactionRequest = for{
              adapterTransactionRequests <- Full(data)
              //TODO, this will cause performance issue, we need limit the number of transaction requests.
              obpTransactionRequests <- LocalMappedConnector.getTransactionRequestsImpl210(fromAccount) ?~! s"$InvalidConnectorResponse, error on LocalMappedConnector.getTransactionRequestsImpl210"
            } yield {
              adapterTransactionRequests ::: obpTransactionRequests
            }
            transactionRequest.map(transactionRequests =>(transactionRequests, callContext))
          case Full((data, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.InvalidConnectorResponse)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        res
      }
    }
  }("getTransactionRequests210")
  
  messageDocs += MessageDoc(
    process = "obp.getCounterparties",
    messageFormat = messageFormat,
    description = "Get Counterparties available to the View on the Account specified by thisBankId, thisAccountId and viewId.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCounterparties.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCounterparties.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCounterparties(
        authInfoExample,
        InternalOutboundGetCounterparties(
          thisBankId = "String",
          thisAccountId = "String",
          viewId = "String"
        )
      )
    ),
    exampleInboundMessage = (
      InboundGetCounterparties(inboundAuthInfoExample, statusExample,
        InternalCounterparty(
          createdByUserId = "",
          name = "",
          thisBankId = "",
          thisAccountId = "",
          thisViewId = "",
          counterpartyId = "",
          otherAccountRoutingScheme = "",
          otherAccountRoutingAddress = "",
          otherBankRoutingScheme = "",
          otherBankRoutingAddress = "",
          otherBranchRoutingScheme = "",
          otherBranchRoutingAddress = "",
          isBeneficiary = true,
          description = "",
          otherAccountSecondaryRoutingScheme = "",
          otherAccountSecondaryRoutingAddress = "",
          bespoke =  List(
            CounterpartyBespoke(key = "key", value = "value"))
        ) :: Nil
    )
  ),
    adapterImplementation = Some(AdapterImplementation("Payments", 0))
  )

  override def getCounterpartiesLegacy(thisBankId: BankId, thisAccountId: AccountId, viewId :ViewId, callContext: Option[CallContext] = None) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(counterpartiesTTL second){
        val box = for {
          authInfo <- getAuthInfo(callContext)
          req = OutboundGetCounterparties(
            authInfo = authInfo,
            counterparty = InternalOutboundGetCounterparties(
            thisBankId = thisBankId.value,
            thisAccountId = thisAccountId.value,
            viewId = viewId.value)
          )
          _<-Full(logger.debug(s"Kafka getCounterparties Req says: is: $req"))
          kafkaMessage <- processToBox(req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetCounterparties]().toString(false)
          inboundGetCounterparties <- tryo{kafkaMessage.extract[InboundGetCounterparties]} ?~! {
            val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetCounterparties class with the Message Doc : You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          (internalCounterparties, status) <- Full(inboundGetCounterparties.data, inboundGetCounterparties.status)
        } yield{
          (internalCounterparties, status)
        }
        logger.debug(s"Kafka getCounterparties Res says: is: $box")

        val res = box match {
          case Full((data, status)) if (status.errorCode=="")  =>
            Full((data,callContext))
          case Full((data, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.InvalidConnectorResponse)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        res
      }
    }
  }("getCounterparties")
  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {
    (getCounterpartiesLegacy(thisBankId, thisAccountId, viewId, callContext) map (i => i._1), callContext)
  }
  
  messageDocs += MessageDoc(
    process = "obp.getCounterpartyByCounterpartyId",
    messageFormat = messageFormat,
    description = "Get a Counterparty by its counterpartyId.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCounterpartyByCounterpartyId.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCounterpartyByCounterpartyId.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCounterpartyByCounterpartyId(
        authInfoExample,
        OutboundGetCounterpartyById(
          counterpartyId = "String"
        )
      )
    ),
    exampleInboundMessage = (
      InboundGetCounterparty(inboundAuthInfoExample, statusExample, Some(InternalCounterparty(createdByUserId = "String", name = "String", thisBankId = "String", thisAccountId = "String", thisViewId = "String", counterpartyId = "String", otherAccountRoutingScheme = "String", otherAccountRoutingAddress = "String", otherBankRoutingScheme = "String", otherBankRoutingAddress = "String", otherBranchRoutingScheme = "String", otherBranchRoutingAddress = "String", isBeneficiary = true, description = "String", otherAccountSecondaryRoutingScheme = "String", otherAccountSecondaryRoutingAddress = "String", bespoke = Nil)))
    ),
    adapterImplementation = Some(AdapterImplementation("Payments", 1))
  )
  override def getCounterpartyByCounterpartyId(counterpartyId: CounterpartyId, callContext: Option[CallContext])= saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(counterpartyByCounterpartyIdTTL second) {
       val req = OutboundGetCounterpartyByCounterpartyId(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), OutboundGetCounterpartyById(counterpartyId.value))
        logger.debug(s"Kafka getCounterpartyByCounterpartyId Req says: is: $req")
        
       val future = for {
         res <- processToFuture[OutboundGetCounterpartyByCounterpartyId](req) map {
           f =>
             try {
               f.extract[InboundGetCounterparty]
             } catch {
               case e: Exception =>
                 val received = liftweb.json.compactRender(f)
                 val expected = SchemaFor[InboundGetCounterparty]().toString(false)
                 val err = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetCounterparty class with the Message Doc : You received this ($received). We expected this ($expected)"
                 sendOutboundAdapterError(err)
                 throw new MappingException(err, e)
             }
         } map { x => (x.inboundAuthInfo, x.data, x.status) }
       } yield {
         Full(res)
       }
       logger.debug(s"Kafka getCounterpartyByCounterpartyId Res says: is: $future")

        val res = future map {
          case Full((authInfo, Some(data), status)) if (status.errorCode == "") =>
            (Full(data), callContext)
          case Full((authInfo, data, status)) if (status.errorCode != "") =>
            (Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages), callContext)
          case _ =>
            (Failure(ErrorMessages.UnknownError), callContext)
        }
        res
      }
    }
  }("getCounterpartyByCounterpartyId")


  messageDocs += MessageDoc(
    process = "obp.getCounterpartyTrait",
    messageFormat = messageFormat,
    description = "Get a Counterparty by its bankId, accountId and counterpartyId",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCounterparty.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCounterparty.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCounterparty(
        authInfoExample,
        "BankId",
        "AccountId",
        "counterpartyId"
      )
      ),
    exampleInboundMessage = (
      InboundGetCounterparty(inboundAuthInfoExample, 
        statusExample, 
        Some(InternalCounterparty(createdByUserId = "String", name = "String", thisBankId = "String", thisAccountId = "String", thisViewId = "String", counterpartyId = "String", otherAccountRoutingScheme = "String", otherAccountRoutingAddress = "String", otherBankRoutingScheme = "String", otherBankRoutingAddress = "String", otherBranchRoutingScheme = "String", otherBranchRoutingAddress = "String", isBeneficiary = true, description = "String", otherAccountSecondaryRoutingScheme = "String", otherAccountSecondaryRoutingAddress = "String", bespoke = Nil)))
      ),
    adapterImplementation = Some(AdapterImplementation("Payments", 1))
  )
  override def getCounterpartyTrait(thisBankId: BankId, thisAccountId: AccountId, counterpartyId: String, callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    val req = OutboundGetCounterparty(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), thisBankId.value, thisAccountId.value, counterpartyId)
    logger.debug(s"Kafka getCounterpartyTrait Req says: is: $req")

    val future = for {
     res <- processToFuture[OutboundGetCounterparty](req) map {
       f =>
         try {
           f.extract[InboundGetCounterparty]
         } catch {
           case e: Exception =>
             val received = liftweb.json.compactRender(f)
             val expected = SchemaFor[InboundGetCounterparty]().toString(false)
             val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetCounterparty class with the Message Doc : You received this ($received). We expected this ($expected)"
             sendOutboundAdapterError(error)
             throw new MappingException(error, e)
         }
     } map { x => (x.inboundAuthInfo, x.data, x.status) }
   } yield {
     Full(res)
   }
   logger.debug(s"Kafka getCounterpartyTrait Res says: is: $future")
    
    val res = future map {
      case Full((authInfo, Some(data), status)) if (status.errorCode=="")  =>
        (Full(data), callContext)
      case Full((authInfo, data, status)) if (status.errorCode!="") =>
        (Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages), callContext)
      case _ =>
        (Failure(ErrorMessages.UnknownError), callContext)
    }
    res
  }("getCounterpartyTrait")
  
  
  messageDocs += MessageDoc(
    process = "obp.getCustomersByUserIdFuture",
    messageFormat = messageFormat,
    description = "Get Customers represented by the User.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCustomersByUserId.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCustomersByUserId.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCustomersByUserId(
        authInfoExample
      )
    ),
    exampleInboundMessage = (
      InboundGetCustomersByUserId(
        inboundAuthInfoExample,
        statusExample,
        InternalCustomer(
          customerId = "String", bankId = bankIdExample.value, number = "String",
          legalName = "String", mobileNumber = "String", email = "String",
          faceImage = CustomerFaceImage(date = DateWithSecondsExampleObject, url = "String"),
          dateOfBirth = DateWithSecondsExampleObject, relationshipStatus = "String",
          dependents = 1, dobOfDependents = List(DateWithSecondsExampleObject),
          highestEducationAttained = "String", employmentStatus = "String",
          creditRating = CreditRating(rating = "String", source = "String"),
          creditLimit = CreditLimit(currency = "String", amount = "String"),
          kycStatus = false, lastOkDate = DateWithSecondsExampleObject
        ) :: Nil
      )
    ),
    outboundAvroSchema = None,
    inboundAvroSchema = None,
    adapterImplementation = Some(AdapterImplementation("Customer", 0))
  )

  override def getCustomersByUserId(userId: String, @CacheKeyOmit callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]] = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(customersByUserIdTTL second) {

        val req = OutboundGetCustomersByUserId(getAuthInfo(callContext).openOrThrowException(NoCallContext))
        logger.debug(s"Kafka getCustomersByUserIdFuture Req says: is: $req")

        val future = processRequest[InboundGetCustomersByUserId](req)
        logger.debug(s"Kafka getCustomersByUserIdFuture Res says: is: $future")

        future map {
          case Full(inbound) if (inbound.status.hasNoError) =>
            Full(JsonFactory_vJune2017.createObpCustomers(inbound.data))
          case Full(inbound) if (inbound.status.hasError) =>
            Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
          case failureOrEmpty => failureOrEmpty
        } map {it =>
          (it.asInstanceOf[Box[List[Customer]]], callContext)
        }
      }
    }
  }("getCustomersByUserIdFuture")
  
  
  messageDocs += MessageDoc(
    process = "obp.getCheckbookOrdersFuture",
    messageFormat = messageFormat,
    description = "Get the status of CheckbookOrders for an Account.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCheckbookOrderStatus.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCheckbookOrderStatus.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCheckbookOrderStatus(
        authInfoExample,
        bankId = bankIdExample.value,
        accountId ="accountId", 
        originatorApplication ="String", 
        originatorStationIP = "String", 
        primaryAccount =""//TODO not sure for now.
      )
    ),
    exampleInboundMessage = (
      InboundGetChecksOrderStatus(
        inboundAuthInfoExample,
        statusExample,
        SwaggerDefinitionsJSON.checkbookOrdersJson
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Misc", 1))
  )

  override def getCheckbookOrders(
    bankId: String, 
    accountId: String, 
    @CacheKeyOmit callContext: Option[CallContext]
  )= saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(statusOfCheckbookOrders second) {

        val req = OutboundGetCheckbookOrderStatus(
          authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext), 
          bankId = bankId, 
          accountId =accountId, 
          originatorApplication = "String", 
          originatorStationIP = "String", 
          primaryAccount = ""
        )
        logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCheckbookOrdersFuture Req says: is: $req")

        val future = for {
          res <- processToFuture[OutboundGetCheckbookOrderStatus](req) map {
            f =>
              try {
                f.extract[InboundGetChecksOrderStatus]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetChecksOrderStatus]().toString(false)
                  val error = s"correlationId(${req.authInfo.correlationId}): $InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetCheckbookOrderStatus class with the Message Doc : You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {x => (x.data, x.status)}
        } yield{
          res
        }
        
        val res = future map {
          case (checksOrderStatusResponseDetails, status) if (status.errorCode=="") =>
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCheckbookOrdersFuture Res says: is: $checksOrderStatusResponseDetails")
            Full(checksOrderStatusResponseDetails, callContext)
          case (accountDetails, status) if (status.errorCode!="") =>
            val errorMessage = "INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCheckbookOrdersFuture Res says: is: $errorMessage")
            Failure(errorMessage)
          case _ =>
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCheckbookOrdersFuture Res says: is: $UnknownError")
            Failure(UnknownError)
        }
        res
      }
    }
  }("getCheckbookOrdersFuture")
  
  
  messageDocs += MessageDoc(
    process = "obp.getStatusOfCreditCardOrderFuture",
    messageFormat = messageFormat,
    description = "Get the status of CreditCardOrders",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetCreditCardOrderStatus.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetCreditCardOrderStatus.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetCreditCardOrderStatus(
        authInfoExample,
        bankId = bankIdExample.value,
        accountId = accountIdExample.value,
        originatorApplication = "String", 
        originatorStationIP = "String", 
        primaryAccount = ""
      )
    ),
    exampleInboundMessage = (
      InboundGetCreditCardOrderStatus(
        inboundAuthInfoExample,
        statusExample,
        List(InboundCardDetails(
          "OrderId",
          "CreditCardType" ,
          "CardDescription",
          "UseType",
          "OrderDate",
          "DeliveryStatus",
          "StatusDate",
          "Branch"
        )
        )
    )),
    adapterImplementation = Some(AdapterImplementation("Misc", 1))
  )

  override def getStatusOfCreditCardOrder(
    bankId: String, 
    accountId: String, 
    @CacheKeyOmit callContext: Option[CallContext]
  ) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(statusOfCreditcardOrders second) {

        val req = OutboundGetCreditCardOrderStatus(
          authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext), 
          bankId = bankId, 
          accountId =accountId, 
          originatorApplication ="String", 
          originatorStationIP = "String", 
          primaryAccount =""//TODO not sure for now.
        )
        logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCreditCardOrderFuture Req says: is: $req")

        val future = for {
          res <- processToFuture[OutboundGetCreditCardOrderStatus](req) map {
            f =>
              try {
                f.extract[InboundGetCreditCardOrderStatus]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundCardDetails]().toString(false)
                  val error = s"correlationId(${req.authInfo.correlationId}): $InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetCreditCardOrderStatus class with the Message Doc : You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {x => (x.data, x.status)}
        } yield{
          res
        }
        
        val res = future map {
          case (checksOrderStatusResponseDetails, status) if (status.errorCode=="") =>
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCreditCardOrderFuture Res says: is: $checksOrderStatusResponseDetails")
            Full(checksOrderStatusResponseDetails.map(
              card =>CardObjectJson(
                card_type= card.creditCardType,
                card_description = card.cardDescription,
                use_type= card.creditCardType
              )), callContext)
          case (accountDetails, status) if (status.errorCode!="") =>
            val errorMessage = "INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCreditCardOrderFuture Res says: is: $errorMessage")
            Failure(errorMessage)
          case _ =>
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCreditCardOrderFuture Res says: is: $UnknownError")
            Failure(UnknownError)
        }
        res
      }
    }
  }("getStatusOfCreditCardOrderFuture")
    
  /////////////////////////////////////////////////////////////////////////////
  // Helper for creating a transaction
  def createInMemoryTransaction(bankAccount: BankAccount,internalTransaction: InternalTransaction_vSept2018): Box[Transaction] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(memoryTransactionTTL second) {
        for {
          datePosted <- tryo {
            new SimpleDateFormat(DateWithDay2).parse(internalTransaction.postedDate)
          } ?~! s"$InvalidConnectorResponseForGetTransaction Wrong posteDate format should be $DateWithDay2, current is ${internalTransaction.postedDate}"
          dateCompleted <- tryo {
            new SimpleDateFormat(DateWithDay2).parse(internalTransaction.completedDate)
          } ?~! s"$InvalidConnectorResponseForGetTransaction Wrong completedDate format should be $DateWithDay2, current is ${internalTransaction.completedDate}"

          counterpartyName <- tryo {
            internalTransaction.counterpartyName
          } ?~! s"$InvalidConnectorResponseForGetTransaction. Can not get counterpartyName from Adapter. "
          //2018-07-18, here we can not get enough data from Adapter, so we only use counterpartyName set to otherAccountRoutingScheme and otherAccountRoutingAddress. 
          counterpartyId <- Full(APIUtil.createImplicitCounterpartyId(bankAccount.bankId.value, bankAccount.accountId.value, counterpartyName,counterpartyName,counterpartyName))
          counterparty <- createInMemoryCounterparty(bankAccount, counterpartyName, counterpartyId)

        } yield {
          // Create new transaction
          new Transaction(
            internalTransaction.transactionId, // uuid:String
            TransactionId(internalTransaction.transactionId), // id:TransactionId
            bankAccount, // thisAccount:BankAccount
            counterparty, // otherAccount:OtherBankAccount
            internalTransaction.`type`, // transactionType:String
            BigDecimal(internalTransaction.amount), // val amount:BigDecimal
            bankAccount.currency, // currency:String
            Some(internalTransaction.description), // description:Option[String]
            datePosted, // startDate:Date
            dateCompleted, // finishDate:Date
            BigDecimal(internalTransaction.newBalanceAmount) // balance:BigDecimal)
          )
        }
      }
    }
  }

  def createInMemoryTransactionsCore(bankAccount: BankAccount,internalTransactions: List[InternalTransaction_vSept2018]): Box[List[TransactionCore]] = {
    //first loop all the items in the list, and return all the boxed back. it may contains the Full, Failure, Empty. 
    val transactionCoresBoxes: List[Box[TransactionCore]] = internalTransactions.map(createInMemoryTransactionCore(bankAccount, _))
    
    //check the Failure in the List, if it contains any Failure, than throw the Failure back, it is 0. Then run the 
    transactionCoresBoxes.filter(_.isInstanceOf[Failure]).length match {
      case 0 =>
        tryo {transactionCoresBoxes.filter(_.isDefined).map(_.openOrThrowException(attemptedToOpenAnEmptyBox))}
      case _ => 
        transactionCoresBoxes.filter(_.isInstanceOf[Failure]).head.asInstanceOf[Failure]
    }
  }
  def createInMemoryTransactionCore(bankAccount: BankAccount,internalTransaction: InternalTransaction_vSept2018): Box[TransactionCore] = {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(memoryTransactionTTL second) {
        for {
          datePosted <- tryo {
            new SimpleDateFormat(DateWithDay2).parse(internalTransaction.postedDate)
          } ?~! s"$InvalidConnectorResponseForGetTransaction Wrong posteDate format should be $DateWithDay2, current is ${internalTransaction.postedDate}"
          dateCompleted <- tryo {
            new SimpleDateFormat(DateWithDay2).parse(internalTransaction.completedDate)
          } ?~! s"$InvalidConnectorResponseForGetTransaction Wrong completedDate format should be $DateWithDay2, current is ${internalTransaction.completedDate}"
          counterpartyCore <- Full(CounterpartyCore(
            //2018-07-18, here we can not get enough data from Adapter, so we only use counterpartyName set to otherAccountRoutingScheme and otherAccountRoutingAddress. 
            counterpartyId = APIUtil.createImplicitCounterpartyId(bankAccount.bankId.value, bankAccount.accountId.value, internalTransaction.counterpartyName,
                                                                  internalTransaction.counterpartyName,internalTransaction.counterpartyName),
            counterpartyName = internalTransaction.counterpartyName,
            kind = null,
            thisBankId = BankId(""),
            thisAccountId = AccountId(""),
            otherBankRoutingScheme = "",
            otherBankRoutingAddress = None,
            otherAccountRoutingScheme = "",
            otherAccountRoutingAddress = None,
            otherAccountProvider = "",
            isBeneficiary = true
          ))
        } yield {
          // Create new transaction
          TransactionCore(
            TransactionId(internalTransaction.transactionId), // id:TransactionId
            bankAccount, // thisAccount:BankAccount
            counterpartyCore, // otherAccount:OtherBankAccount
            internalTransaction.`type`, // transactionType:String
            BigDecimal(internalTransaction.amount), // val amount:BigDecimal
            bankAccount.currency, // currency:String
            Some(internalTransaction.description), // description:Option[String]
            datePosted, // startDate:Date
            dateCompleted, // finishDate:Date
            BigDecimal(internalTransaction.newBalanceAmount) // balance:BigDecimal)
          )
        }
      }
    }
  }

  // Helper for creating other bank account, this will not create it in database, only in scala code.
  //Note, we have a method called createCounterparty in this connector, so named it here. 
  def createInMemoryCounterparty(bankAccount: BankAccount, counterpartyName: String, counterpartyId: String): Box[Counterparty] = {
     /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(memoryCounterpartyTTL second){
        Full(
          Counterparty(
            thisBankId = BankId(bankAccount.bankId.value),
            thisAccountId = bankAccount.accountId,
            counterpartyId = counterpartyId,
            counterpartyName = counterpartyName,

            otherBankRoutingAddress = None,
            otherAccountRoutingAddress = None,
            otherBankRoutingScheme = null,
            otherAccountRoutingScheme = null,
            otherAccountProvider = null,
            isBeneficiary = true,

            kind = null,
            nationalIdentifier = null
          )
        )
      }
    }
  }

  messageDocs += MessageDoc(
    process = "obp.getBranches",
    messageFormat = messageFormat,
    description = "Get Branches fora Bank specified by bankId",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetBranches.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetBranches.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetBranches(authInfoExample,"bankid")
    ),
    exampleInboundMessage = (
      InboundGetBranches(
        inboundAuthInfoExample,
        Status("",
        inboundStatusMessagesExample),
        InboundBranchVSept2018(
          branchId = BranchId(""),
          bankId = BankId(bankIdExample.value),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          lobbyString = None,
          driveUpString = None,
          meta = Meta(License("","")),
          branchRouting = None,
          lobby = Some(Lobby(monday = List(OpeningTimes("","")),
            tuesday = List(OpeningTimes("","")),
            wednesday = List(OpeningTimes("","")),
            thursday = List(OpeningTimes("","")),
            friday = List(OpeningTimes("","")),
            saturday = List(OpeningTimes("","")),
            sunday = List(OpeningTimes("",""))
          )),
          driveUp = None,
          // Easy access for people who use wheelchairs etc.
          isAccessible = Some(true),
          accessibleFeatures = None,
          branchType  = Some(""),
          moreInfo = Some(""),
          phoneNumber = Some(""),
          isDeleted = Some(false)
        )  :: Nil
      )

    ),
    adapterImplementation = Some(AdapterImplementation("Open Data", 1))
  )

  override def getBranches(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(branchesTTL second){
        val req = OutboundGetBranches(AuthInfo(), bankId.toString)
        logger.debug(s"Kafka getBranchesFuture Req is: $req")

        val future: Future[(List[InboundBranchVSept2018], Status)] = for {
          res <- processToFuture[OutboundGetBranches](req) map {
            f =>
              try {
                f.extract[InboundGetBranches]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetBranches]().toString(false)
                  val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetBranches class with the Message Doc : You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {
            d => (d.data, d.status)
          }
        } yield {
          res
        }

        logger.debug(s"Kafka getBranchFuture Res says:  is: $future")
        future map {
          case (branches, status) if (status.errorCode=="") =>
            Full(branches, callContext)
          case (_, status) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBranchesFuture")

  messageDocs += MessageDoc(
    process = "obp.getBranch",
    messageFormat = messageFormat,
    description = "Get a Branch as specified by bankId and branchId",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetBranch.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetBranch.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetBranch(authInfoExample,"bankid", "branchid")
    ),
    exampleInboundMessage = (
      InboundGetBranch(
        inboundAuthInfoExample,
        Status("",
          inboundStatusMessagesExample),
        Some(InboundBranchVSept2018(
          branchId = BranchId(""),
          bankId = BankId(bankIdExample.value),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          lobbyString = None,
          driveUpString = None,
          meta = Meta(License("","")),
          branchRouting = None,
          lobby = Some(Lobby(monday = List(OpeningTimes("","")),
            tuesday = List(OpeningTimes("","")),
            wednesday = List(OpeningTimes("","")),
            thursday = List(OpeningTimes("","")),
            friday = List(OpeningTimes("","")),
            saturday = List(OpeningTimes("","")),
            sunday = List(OpeningTimes("",""))
          )),
          driveUp = None,
          // Easy access for people who use wheelchairs etc.
          isAccessible = Some(true),
          accessibleFeatures = None,
          branchType  = Some(""),
          moreInfo = Some(""),
          phoneNumber = Some(""),
          isDeleted = Some(false)
        ))
      )

    ),
    adapterImplementation = Some(AdapterImplementation("Open Data", 1))
  )

  override def getBranch(bankId : BankId, branchId: BranchId, callContext: Option[CallContext])  = saveConnectorMetric {

    logger.debug("Enter getBranch for: " + branchId)
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(branchTTL second){
        val req = OutboundGetBranch(AuthInfo(), bankId.toString, branchId.toString)
        logger.debug(s"Kafka getBranchFuture Req is: $req")

        val future: Future[(Option[InboundBranchVSept2018], Status)] = for {
          res <- processToFuture[OutboundGetBranch](req) map {
            f =>
              try {
                f.extract[InboundGetBranch]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetBranch]().toString(false)
                  val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetBranch class with the Message Doc : You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {
            d => (d.data, d.status)
          }
        } yield {
          res
        }

        logger.debug(s"Kafka getBranchFuture Res says:  is: $future")
        future map {
          case (Some(branch), status) if (status.errorCode=="") =>
            Full(branch, callContext)
          case (_, status) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBranchFuture")


  messageDocs += MessageDoc(
    process = "obp.getAtms",
    messageFormat = messageFormat,
    description = "Get ATMs for a bank specified by bankId",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetAtms.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetAtms.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetAtms(authInfoExample,"bankid")
    ),
    exampleInboundMessage = (
      InboundGetAtms(
        inboundAuthInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        InboundAtmSept2018(
          atmId = AtmId("333"),
          bankId = BankId(bankIdExample.value),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          meta = Meta(License(id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")),
          OpeningTimeOnMonday = Some(""),
          ClosingTimeOnMonday = Some(""),

          OpeningTimeOnTuesday = Some(""),
          ClosingTimeOnTuesday = Some(""),

          OpeningTimeOnWednesday = Some(""),
          ClosingTimeOnWednesday = Some(""),

          OpeningTimeOnThursday = Some(""),
          ClosingTimeOnThursday = Some(""),

          OpeningTimeOnFriday = Some(""),
          ClosingTimeOnFriday = Some(""),

          OpeningTimeOnSaturday  = Some(""),
          ClosingTimeOnSaturday = Some(""),

          OpeningTimeOnSunday = Some(""),
          ClosingTimeOnSunday = Some(""),
          isAccessible = Some(true),

          locatedAt = Some(""),
          moreInfo = Some(""),
          hasDepositCapability = Some(true)
        )  :: Nil
      )

    ),
    adapterImplementation = Some(AdapterImplementation("Open Data", 1))
  )

  override def getAtms(bankId: BankId, callContext: Option[CallContext], queryParams: List[OBPQueryParam]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(atmsTTL second){
        val req = OutboundGetAtms(AuthInfo(), bankId.value)
        logger.debug(s"Kafka getAtmsFuture Req is: $req")

        val future = for {
          res <- processToFuture[OutboundGetAtms](req) map {
            f =>
              try {
                f.extract[InboundGetAtms]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetAtms]().toString(false)
                  val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetAtms class with the Message Doc : You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {
            d => (d.data, d.status)
          }
        } yield {
          res
        }

        logger.debug(s"Kafka getAtmsFuture Res says:  is: $future")
        future map {
          case (atms, status) if (status.errorCode=="") =>
            Full(atms, callContext)
          case (_, status) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getAtmsFuture")

  messageDocs += MessageDoc(
    process = "obp.getAtm",
    messageFormat = messageFormat,
    description = "Get an ATM as specified by bankId and atmId.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetAtm.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetAtm.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundGetAtm(authInfoExample,"bankId", "atmId")
    ),
    exampleInboundMessage = (
      InboundGetAtm(
        inboundAuthInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        Some(InboundAtmSept2018(
          atmId = AtmId("333"),
          bankId = BankId(bankIdExample.value),
          name = "",
          address =  Address(line1 = "",
            line2 = "",
            line3 = "",
            city = "",
            county = Some(""),
            state = "",
            postCode = "",
            //ISO_3166-1_alpha-2
            countryCode = ""),
          location = Location(11,11, None,None),
          meta = Meta(License(id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")),
          OpeningTimeOnMonday = Some(""),
          ClosingTimeOnMonday = Some(""),

          OpeningTimeOnTuesday = Some(""),
          ClosingTimeOnTuesday = Some(""),

          OpeningTimeOnWednesday = Some(""),
          ClosingTimeOnWednesday = Some(""),

          OpeningTimeOnThursday = Some(""),
          ClosingTimeOnThursday = Some(""),

          OpeningTimeOnFriday = Some(""),
          ClosingTimeOnFriday = Some(""),

          OpeningTimeOnSaturday  = Some(""),
          ClosingTimeOnSaturday = Some(""),

          OpeningTimeOnSunday = Some(""),
          ClosingTimeOnSunday = Some(""),
          isAccessible = Some(true),

          locatedAt = Some(""),
          moreInfo = Some(""),
          hasDepositCapability = Some(true)
        )
      ))
    ),
    adapterImplementation = Some(AdapterImplementation("Open Data", 1))
  )

  override def getAtm(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(atmTTL second){
        val req = OutboundGetAtm(AuthInfo(), bankId.value, atmId.value)
        logger.debug(s"Kafka getAtmFuture Req is: $req")

        val future: Future[(Option[InboundAtmSept2018], Status)] = for {
          res <- processToFuture[OutboundGetAtm](req) map {
            f =>
              try {
                f.extract[InboundGetAtm]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetAtm]().toString(false)
                  val error = s"$InvalidConnectorResponse Please check your to.obp.api.1.caseclass.$OutboundGetAtm class with the Message Doc : You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {
            d => (d.data, d.status)
          }
        } yield {
          res
        }

        logger.debug(s"Kafka getAtmFuture Res says:  is: $future")
        future map {
          case (Some(atm), status) if (status.errorCode=="") =>
            Full(atm, callContext)
          case (_, status) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getAtmFuture")

  messageDocs += MessageDoc(
    process = "obp.getChallengeThreshold",
    messageFormat = messageFormat,
    description = "Get Challenge Threshold",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetChallengeThreshold.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetChallengeThreshold.getClass.getSimpleName).response),
    exampleOutboundMessage = (OutboundGetChallengeThreshold(
      authInfoExample,
      bankId = bankIdExample.value,
      accountId = accountIdExample.value,
      viewId = SYSTEM_OWNER_VIEW_ID,
      transactionRequestType = "SEPA",
      currency ="EUR",
      userId = userIdExample.value,
      userName =usernameExample.value
      )),
    exampleInboundMessage = (
      InboundGetChallengeThreshold(
          inboundAuthInfoExample, 
          Status(errorCodeExample, inboundStatusMessagesExample), 
          AmountOfMoney(
            currencyExample.value,
            transactionAmountExample.value)
        )
    ),
    adapterImplementation = Some(AdapterImplementation("Payments", 1))
  )
  
  override def getChallengeThreshold(
    bankId: String,
    accountId: String,
    viewId: String,
    transactionRequestType: String,
    currency: String,
    userId: String,
    userName: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[AmountOfMoney]] = saveConnectorMetric {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(atmTTL second){
        val authInfo = getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox)
        val req = OutboundGetChallengeThreshold(authInfo, bankId, accountId, viewId, transactionRequestType, currency, userId, userName)
        logger.debug(s"Kafka getChallengeThresholdFuture Req is: $req")

        processRequest[InboundGetChallengeThreshold](req) map { inbound =>
          val boxedResult = inbound match {
            case Full(inboundData) if (inboundData.status.hasNoError) =>
              Full(inboundData.data)
            case Full(inboundData) if (inboundData.status.hasError) =>
              Failure("INTERNAL-"+ inboundData.status.errorCode+". + CoreBank-Status:" + inboundData.status.backendMessages)
            case failureOrEmpty: Failure => failureOrEmpty
          }
          (boxedResult, callContext)
        }
      }
    }
  }("getChallengeThreshold")
  
  messageDocs += MessageDoc(
    process = "obp.makePaymentv210",
    messageFormat = messageFormat,
    description = "Make payment (create transaction).",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundCreateTransaction.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundCreateTransaction.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutboundCreateTransaction(
        authInfoExample,
        // fromAccount
        fromAccountBankId =bankIdExample.value,
        fromAccountId =accountIdExample.value,
        
        // transaction details
        transactionRequestType ="SEPA",
        transactionChargePolicy ="SHARE",
        transactionRequestCommonBody = TransactionRequestBodyCommonJSON(
          AmountOfMoneyJsonV121(
            currencyExample.value,
            transactionAmountExample.value),
          transactionDescriptionExample.value),
        
        // toAccount or toCounterparty
        toCounterpartyId = counterpartyIdExample.value,
        toCounterpartyName = counterpartyNameExample.value,
        toCounterpartyCurrency = currencyExample.value,
        toCounterpartyRoutingAddress = accountRoutingAddressExample.value,
        toCounterpartyRoutingScheme = accountRoutingSchemeExample.value,
        toCounterpartyBankRoutingAddress = bankRoutingSchemeExample.value,
        toCounterpartyBankRoutingScheme = bankRoutingAddressExample.value)),
    exampleInboundMessage = (
      InboundCreateTransactionId(
        inboundAuthInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        InternalTransactionId(transactionIdExample.value)
      )
    ),
    adapterImplementation = Some(AdapterImplementation("Payments", 1))
  )
  override def makePaymentv210(
    fromAccount: BankAccount,
    toAccount: BankAccount,
    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
    amount: BigDecimal,
    description: String,
    transactionRequestType: TransactionRequestType,
    chargePolicy: String,
    callContext: Option[CallContext]
  ): OBPReturnType[Box[TransactionId]]= {
    
    val req = OutboundCreateTransaction(
      authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
      
      // fromAccount
      fromAccountId = fromAccount.accountId.value,
      fromAccountBankId = fromAccount.bankId.value,
      
      // transaction details
      transactionRequestType = transactionRequestType.value,
      transactionChargePolicy = chargePolicy,
      transactionRequestCommonBody = transactionRequestCommonBody,
      
      // toAccount or toCounterparty
      toCounterpartyId = toAccount.accountId.value,
      toCounterpartyName = toAccount.name,
      toCounterpartyCurrency = toAccount.currency,
      toCounterpartyRoutingAddress = toAccount.accountId.value,
      toCounterpartyRoutingScheme = "OBP",
      toCounterpartyBankRoutingAddress = toAccount.bankId.value,
      toCounterpartyBankRoutingScheme = "OBP"
    )
    
    processRequest[InboundCreateTransactionId](req) map { inbound =>
      val boxedResult = inbound match {
        case Full(inboundData) if (inboundData.status.hasNoError) =>
          Full(TransactionId(inboundData.data.id))
        case Full(inboundData) if (inboundData.status.hasError) =>
          Failure("INTERNAL-"+ inboundData.status.errorCode+". + CoreBank-Status:" + inboundData.status.backendMessages)
        case failureOrEmpty: Failure => failureOrEmpty
      }
      (boxedResult, callContext)
    }
  }

  messageDocs += MessageDoc(
    process = "obp.createMeeting",
    messageFormat = messageFormat,
    description = "Create Meeting",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateMeeting.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateMeeting.getClass.getSimpleName).request),
    exampleOutboundMessage = (
      OutBoundCreateMeeting(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        bankId= BankId(value="string"),
        staffUser= UserCommons(userPrimaryKey= UserPrimaryKey(value=123),
          userId="string",
          idGivenByProvider="string",
          provider="string",
          emailAddress="string",
          name="string"),
        customerUser= UserCommons(userPrimaryKey= UserPrimaryKey(value=123),
          userId="string",
          idGivenByProvider="string",
          provider="string",
          emailAddress="string",
          name="string"),
        providerId="string",
        purposeId="string",
        when=new Date(),
        sessionId="string",
        customerToken="string",
        staffToken="string",
        creator= ContactDetails(name="string",
          phone="string",
          email="string"),
        invitees=List( Invitee(contactDetails= ContactDetails(name="string",
          phone="string",
          email="string"),
          status="string")))
      ),
    exampleInboundMessage = (
      InBoundCreateMeeting(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data= MeetingCommons(meetingId="string",
          providerId="string",
          purposeId="string",
          bankId="string",
          present= MeetingPresent(staffUserId="string",
            customerUserId="string"),
          keys= MeetingKeys(sessionId="string",
            customerToken="string",
            staffToken="string"),
          when=new Date(),
          creator= ContactDetails(name="string",
            phone="string",
            email="string"),
          invitees=List( Invitee(contactDetails= ContactDetails(name="string",
            phone="string",
            email="string"),
            status="string"))))
      ),
    adapterImplementation = Some(AdapterImplementation("- Meeting", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycCheck",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Check",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycCheck.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycCheck.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundCreateOrUpdateKycCheck(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        bankId="string",
        customerId="string",
        id="string",
        customerNumber="string",
        date=new Date(),
        how="string",
        staffUserId="string",
        mStaffName="string",
        mSatisfied=true,
        comments="string")
      ),
    exampleInboundMessage = (
      InBoundCreateOrUpdateKycCheck(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data= KycCheckCommons(bankId="string",
          customerId="string",
          idKycCheck="string",
          customerNumber="string",
          date=new Date(),
          how="string",
          staffUserId="string",
          staffName="string",
          satisfied=true,
          comments="string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycDocument",
    messageFormat = messageFormat,
    description = "Create Or Update KYC Document",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycDocument.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycDocument.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundCreateOrUpdateKycDocument(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        bankId="string",
        customerId="string",
        id="string",
        customerNumber="string",
        `type`="string",
        number="string",
        issueDate=new Date(),
        issuePlace="string",
        expiryDate=new Date())
      ),
    exampleInboundMessage = (
      InBoundCreateOrUpdateKycDocument(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data= KycDocumentCommons(bankId="string",
          customerId="string",
          idKycDocument="string",
          customerNumber="string",
          `type`="string",
          number="string",
          issueDate=new Date(),
          issuePlace="string",
          expiryDate=new Date()))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycMedia",
    messageFormat = messageFormat,
    description = "Create Or Update KYC Media",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycMedia.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycMedia.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundCreateOrUpdateKycMedia(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        bankId="string",
        customerId="string",
        id="string",
        customerNumber="string",
        `type`="string",
        url="string",
        date=new Date(),
        relatesToKycDocumentId="string",
        relatesToKycCheckId="string")
      ),
    exampleInboundMessage = (
      InBoundCreateOrUpdateKycMedia(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data= KycMediaCommons(bankId="string",
          customerId="string",
          idKycMedia="string",
          customerNumber="string",
          `type`="string",
          url="string",
          date=new Date(),
          relatesToKycDocumentId="string",
          relatesToKycCheckId="string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycStatus",
    messageFormat = messageFormat,
    description = "Create Or Update KYC Status",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycStatus.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycStatus.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundCreateOrUpdateKycStatus(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        bankId="string",
        customerId="string",
        customerNumber="string",
        ok=true,
        date=new Date())
      ),
    exampleInboundMessage = (
      InBoundCreateOrUpdateKycStatus(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data= KycStatusCommons(bankId="string",
          customerId="string",
          customerNumber="string",
          ok=true,
          date=new Date()))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.getKycDocuments",
    messageFormat = messageFormat,
    description = "Get KYC Documents",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycDocuments.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycDocuments.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundGetKycDocuments(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        customerId="string")
      ),
    exampleInboundMessage = (
      InBoundGetKycDocuments(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data=List( KycDocumentCommons(bankId="string",
          customerId="string",
          idKycDocument="string",
          customerNumber="string",
          `type`="string",
          number="string",
          issueDate=new Date(),
          issuePlace="string",
          expiryDate=new Date())))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.getKycMedias",
    messageFormat = messageFormat,
    description = "Get KYC Medias",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycMedias.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycMedias.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundGetKycMedias(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        customerId="string")
      ),
    exampleInboundMessage = (
      InBoundGetKycMedias(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data=List( KycMediaCommons(bankId="string",
          customerId="string",
          idKycMedia="string",
          customerNumber="string",
          `type`="string",
          url="string",
          date=new Date(),
          relatesToKycDocumentId="string",
          relatesToKycCheckId="string")))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.getKycStatuses",
    messageFormat = messageFormat,
    description = "Get KYC Statuses",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycStatuses.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycStatuses.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundGetKycStatuses(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        customerId="string")
      ),
    exampleInboundMessage = (
      InBoundGetKycStatuses(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data=List( KycStatusCommons(bankId="string",
          customerId="string",
          customerNumber="string",
          ok=true,
          date=new Date())))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.getKycChecks",
    messageFormat = messageFormat,
    description = "Get KYC Checks",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycChecks.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycChecks.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundGetKycChecks(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        customerId="string")
      ),
    exampleInboundMessage = (
      InBoundGetKycChecks(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data=List( KycCheckCommons(bankId="string",
          customerId="string",
          idKycCheck="string",
          customerNumber="string",
          date=new Date(),
          how="string",
          staffUserId="string",
          staffName="string",
          satisfied=true,
          comments="string")))
      ),
    adapterImplementation = Some(AdapterImplementation("- KYC", 1))
  )

  messageDocs += MessageDoc(
    process = "obp.createMessage",
    messageFormat = messageFormat,
    description = "Create Message",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateMessage.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateMessage.getClass.getSimpleName).response),
    exampleOutboundMessage = (
      OutBoundCreateMessage(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        consumerId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string"))),
        outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
          username=Option("string"),
          linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
            customerNumber="string",
            legalName="string"))),
          userAuthContext=Option(List( BasicUserAuthContext(key="string",
            value="string"))),
          authViews=Option(List( AuthView(view= ViewBasic(id="string",
            name="string",
            description="string"),
            account= AccountBasic(id="string",
              accountRoutings=List( AccountRouting(scheme="string",
                address="string")),
              customerOwners=List( InternalBasicCustomer(bankId="string",
                customerId="string",
                customerNumber="string",
                legalName="string",
                dateOfBirth=new Date())),
              userOwners=List( InternalBasicUser(userId="string",
                emailAddress="string",
                name="string"))))))))),
        user= UserCommons(userPrimaryKey= UserPrimaryKey(value=123),
          userId="string",
          idGivenByProvider="string",
          provider="string",
          emailAddress="string",
          name="string"),
        bankId= BankId(value="string"),
        message="string",
        fromDepartment="string",
        fromPerson="string")
      ),
    exampleInboundMessage = (
      InBoundCreateMessage(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
        sessionId=Option("string"),
        generalContext=Option(List( BasicGeneralContext(key="string",
          value="string")))),
        status= Status(errorCode="",
          backendMessages=List( InboundStatusMessage(source="string",
            status="string",
            errorCode="",
            text="string"))),
        data= CustomerMessageCommons(messageId="string",
          date=new Date(),
          message="string",
          fromDepartment="string",
          fromPerson="string"))
      ),
    adapterImplementation = Some(AdapterImplementation("- Customer", 1))
  )









//---------------- dynamic start -------------------please don't modify this line
// ---------- create on Mon May 13 22:38:20 CST 2019

  messageDocs += MessageDoc(
    process = "obp.createBankAccount",
    messageFormat = messageFormat,
    description = "Create Bank Account",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateBankAccount.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateBankAccount.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCreateBankAccount(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"),
      accountId= AccountId(value="string"),
      accountType="string",
      accountLabel="string",
      currency="string",
      initialBalance=BigDecimal("123.321"),
      accountHolderName="string",
      branchId="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string")
    ),
    exampleInboundMessage = (
     InBoundCreateBankAccount(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data= BankAccountCommons(accountId= AccountId(value="string"),
      accountType="string",
      balance=BigDecimal("123.321"),
      currency="string",
      name="string",
      label="string",
      iban=Option("string"),
      number="string",
      bankId= BankId(value="string"),
      lastUpdate=new Date(),
      branchId="string",
      accountRoutingScheme="string",
      accountRoutingAddress="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      accountRules=List( AccountRule(scheme="string",
      value="string")),
      accountHolder="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("Account", 1))
  )
  override def createBankAccount(bankId: BankId, accountId: AccountId, accountType: String, accountLabel: String, currency: String, initialBalance: BigDecimal, accountHolderName: String, branchId: String, accountRoutingScheme: String, accountRoutingAddress: String, callContext: Option[CallContext]): OBPReturnType[Box[BankAccount]] = {
    import com.openbankproject.commons.dto.{OutBoundCreateBankAccount => OutBound, InBoundCreateBankAccount => InBound}

    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, accountId, accountType, accountLabel, currency, initialBalance, accountHolderName, branchId, accountRoutingScheme, accountRoutingAddress)
    logger.debug(s"Kafka createBankAccount Req is: $req")
    processRequest[InBound](req) map (convertToTuple(callContext))
  }
    
    
  messageDocs += MessageDoc(
    process = "obp.createCustomer",
    messageFormat = messageFormat,
    description = "Create Customer",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateCustomer.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateCustomer.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCreateCustomer(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId= BankId(value="string"),
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
      url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      kycStatus=true,
      lastOkDate=new Date(),
      creditRating=Option( CreditRating(rating="string",
      source="string")),
      creditLimit=Option( AmountOfMoney(currency="string",
      amount="string")),
      title="string",
      branchId="string",
      nameSuffix="string")
    ),
    exampleInboundMessage = (
     InBoundCreateCustomer(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data= CustomerCommons(customerId="string",
      bankId="string",
      number="string",
      legalName="string",
      mobileNumber="string",
      email="string",
      faceImage= CustomerFaceImage(date=new Date(),
      url="string"),
      dateOfBirth=new Date(),
      relationshipStatus="string",
      dependents=123,
      dobOfDependents=List(new Date()),
      highestEducationAttained="string",
      employmentStatus="string",
      creditRating= CreditRating(rating="string",
      source="string"),
      creditLimit= CreditLimit(currency="string",
      amount="string"),
      kycStatus=true,
      lastOkDate=new Date(),
      title="string",
      branchId="string",
      nameSuffix="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("Customer", 1))
  )
  override def createCustomer(bankId: BankId, legalName: String, mobileNumber: String, email: String, faceImage: CustomerFaceImageTrait, dateOfBirth: Date, relationshipStatus: String, dependents: Int, dobOfDependents: List[Date], highestEducationAttained: String, employmentStatus: String, kycStatus: Boolean, lastOkDate: Date, creditRating: Option[CreditRatingTrait], creditLimit: Option[AmountOfMoneyTrait], title: String, branchId: String, nameSuffix: String, callContext: Option[CallContext]): OBPReturnType[Box[Customer]] = {
    import com.openbankproject.commons.dto.{OutBoundCreateCustomer => OutBound, InBoundCreateCustomer => InBound}

    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, legalName, mobileNumber, email, faceImage, dateOfBirth, relationshipStatus, dependents, dobOfDependents, highestEducationAttained, employmentStatus, kycStatus, lastOkDate, creditRating, creditLimit, title, branchId, nameSuffix)
    logger.debug(s"Kafka createCustomer Req is: $req")
    processRequest[InBound](req) map (convertToTuple(callContext))
  }
    
    
  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycCheck",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Check",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycCheck.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycCheck.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycCheck(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId="string",
      customerId="string",
      id="string",
      customerNumber="string",
      date=new Date(),
      how="string",
      staffUserId="string",
      mStaffName="string",
      mSatisfied=true,
      comments="string")
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycCheck(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data= KycCheckCommons(bankId="string",
      customerId="string",
      idKycCheck="string",
      customerNumber="string",
      date=new Date(),
      how="string",
      staffUserId="string",
      staffName="string",
      satisfied=true,
      comments="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def createOrUpdateKycCheck(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String, callContext: Option[CallContext]): OBPReturnType[Box[KycCheck]] = {
    import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycCheck => OutBound, InBoundCreateOrUpdateKycCheck => InBound}

    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, customerId, id, customerNumber, date, how, staffUserId, mStaffName, mSatisfied, comments)
    logger.debug(s"Kafka createOrUpdateKycCheck Req is: $req")
    processRequest[InBound](req) map (convertToTuple(callContext))
  }
    
    
  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycDocument",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Document",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycDocument.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycDocument.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycDocument(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId="string",
      customerId="string",
      id="string",
      customerNumber="string",
      `type`="string",
      number="string",
      issueDate=new Date(),
      issuePlace="string",
      expiryDate=new Date())
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycDocument(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data= KycDocumentCommons(bankId="string",
      customerId="string",
      idKycDocument="string",
      customerNumber="string",
      `type`="string",
      number="string",
      issueDate=new Date(),
      issuePlace="string",
      expiryDate=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def createOrUpdateKycDocument(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycDocument]] = {
    import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycDocument => OutBound, InBoundCreateOrUpdateKycDocument => InBound}

    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, customerId, id, customerNumber, `type`, number, issueDate, issuePlace, expiryDate)
    logger.debug(s"Kafka createOrUpdateKycDocument Req is: $req")
    processRequest[InBound](req) map (convertToTuple(callContext))
  }
    
    
  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycMedia",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Media",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycMedia.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycMedia.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycMedia(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId="string",
      customerId="string",
      id="string",
      customerNumber="string",
      `type`="string",
      url="string",
      date=new Date(),
      relatesToKycDocumentId="string",
      relatesToKycCheckId="string")
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycMedia(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data= KycMediaCommons(bankId="string",
      customerId="string",
      idKycMedia="string",
      customerNumber="string",
      `type`="string",
      url="string",
      date=new Date(),
      relatesToKycDocumentId="string",
      relatesToKycCheckId="string"))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def createOrUpdateKycMedia(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String, callContext: Option[CallContext]): OBPReturnType[Box[KycMedia]] = {
    import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycMedia => OutBound, InBoundCreateOrUpdateKycMedia => InBound}

    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, customerId, id, customerNumber, `type`, url, date, relatesToKycDocumentId, relatesToKycCheckId)
    logger.debug(s"Kafka createOrUpdateKycMedia Req is: $req")
    processRequest[InBound](req) map (convertToTuple(callContext))
  }
    
    
  messageDocs += MessageDoc(
    process = "obp.createOrUpdateKycStatus",
    messageFormat = messageFormat,
    description = "Create Or Update Kyc Status",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycStatus.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundCreateOrUpdateKycStatus.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundCreateOrUpdateKycStatus(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      bankId="string",
      customerId="string",
      customerNumber="string",
      ok=true,
      date=new Date())
    ),
    exampleInboundMessage = (
     InBoundCreateOrUpdateKycStatus(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data= KycStatusCommons(bankId="string",
      customerId="string",
      customerNumber="string",
      ok=true,
      date=new Date()))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def createOrUpdateKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date, callContext: Option[CallContext]): OBPReturnType[Box[KycStatus]] = {
    import com.openbankproject.commons.dto.{OutBoundCreateOrUpdateKycStatus => OutBound, InBoundCreateOrUpdateKycStatus => InBound}

    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , bankId, customerId, customerNumber, ok, date)
    logger.debug(s"Kafka createOrUpdateKycStatus Req is: $req")
    processRequest[InBound](req) map (convertToTuple(callContext))
  }
    
    
  messageDocs += MessageDoc(
    process = "obp.getKycChecks",
    messageFormat = messageFormat,
    description = "Get Kyc Checks",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycChecks.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycChecks.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetKycChecks(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      customerId="string")
    ),
    exampleInboundMessage = (
     InBoundGetKycChecks(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data=List( KycCheckCommons(bankId="string",
      customerId="string",
      idKycCheck="string",
      customerNumber="string",
      date=new Date(),
      how="string",
      staffUserId="string",
      staffName="string",
      satisfied=true,
      comments="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def getKycChecks(customerId: String, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[List[KycCheck]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetKycChecks => OutBound, InBoundGetKycChecks => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , customerId)
        logger.debug(s"Kafka getKycChecks Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getKycChecks")
    
    
  messageDocs += MessageDoc(
    process = "obp.getKycDocuments",
    messageFormat = messageFormat,
    description = "Get Kyc Documents",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycDocuments.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycDocuments.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetKycDocuments(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      customerId="string")
    ),
    exampleInboundMessage = (
     InBoundGetKycDocuments(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data=List( KycDocumentCommons(bankId="string",
      customerId="string",
      idKycDocument="string",
      customerNumber="string",
      `type`="string",
      number="string",
      issueDate=new Date(),
      issuePlace="string",
      expiryDate=new Date())))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def getKycDocuments(customerId: String, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[List[KycDocument]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetKycDocuments => OutBound, InBoundGetKycDocuments => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , customerId)
        logger.debug(s"Kafka getKycDocuments Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getKycDocuments")
    
    
  messageDocs += MessageDoc(
    process = "obp.getKycMedias",
    messageFormat = messageFormat,
    description = "Get Kyc Medias",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycMedias.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycMedias.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetKycMedias(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      customerId="string")
    ),
    exampleInboundMessage = (
     InBoundGetKycMedias(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data=List( KycMediaCommons(bankId="string",
      customerId="string",
      idKycMedia="string",
      customerNumber="string",
      `type`="string",
      url="string",
      date=new Date(),
      relatesToKycDocumentId="string",
      relatesToKycCheckId="string")))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def getKycMedias(customerId: String, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[List[KycMedia]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetKycMedias => OutBound, InBoundGetKycMedias => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , customerId)
        logger.debug(s"Kafka getKycMedias Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getKycMedias")
    
    
  messageDocs += MessageDoc(
    process = "obp.getKycStatuses",
    messageFormat = messageFormat,
    description = "Get Kyc Statuses",
    outboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycStatuses.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutBoundGetKycStatuses.getClass.getSimpleName).response),
    exampleOutboundMessage = (
     OutBoundGetKycStatuses(outboundAdapterCallContext= OutboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      consumerId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string"))),
      outboundAdapterAuthInfo=Option( OutboundAdapterAuthInfo(userId=Option("string"),
      username=Option("string"),
      linkedCustomers=Option(List( BasicLinkedCustomer(customerId="string",
      customerNumber="string",
      legalName="string"))),
      userAuthContext=Option(List( BasicUserAuthContext(key="string",
      value="string"))),
      authViews=Option(List( AuthView(view= ViewBasic(id="string",
      name="string",
      description="string"),
      account= AccountBasic(id="string",
      accountRoutings=List( AccountRouting(scheme="string",
      address="string")),
      customerOwners=List( InternalBasicCustomer(bankId="string",
      customerId="string",
      customerNumber="string",
      legalName="string",
      dateOfBirth=new Date())),
      userOwners=List( InternalBasicUser(userId="string",
      emailAddress="string",
      name="string"))))))))),
      customerId="string")
    ),
    exampleInboundMessage = (
     InBoundGetKycStatuses(inboundAdapterCallContext= InboundAdapterCallContext(correlationId="string",
      sessionId=Option("string"),
      generalContext=Option(List( BasicGeneralContext(key="string",
      value="string")))),
      status= Status(errorCode="",
      backendMessages=List( InboundStatusMessage(source="string",
      status="string",
      errorCode="",
      text="string"))),
      data=List( KycStatusCommons(bankId="string",
      customerId="string",
      customerNumber="string",
      ok=true,
      date=new Date())))
    ),
    adapterImplementation = Some(AdapterImplementation("KYC", 1))
  )
  override def getKycStatuses(customerId: String, @CacheKeyOmit callContext: Option[CallContext]): OBPReturnType[Box[List[KycStatus]]] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountTTL second) {
        import com.openbankproject.commons.dto.{OutBoundGetKycStatuses => OutBound, InBoundGetKycStatuses => InBound}

        val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).get , customerId)
        logger.debug(s"Kafka getKycStatuses Req is: $req")
        processRequest[InBound](req) map (convertToTuple(callContext))
      }
        
    }
  }("getKycStatuses")
    
    
//---------------- dynamic end ---------------------please don't modify this line
}


object KafkaMappedConnector_vSept2018 extends KafkaMappedConnector_vSept2018{
  
}


