package code.bankconnectors.vSept2018

/*
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.text.SimpleDateFormat
import java.util.UUID.randomUUID

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
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
import com.openbankproject.commons.model.{CounterpartyTrait, _}
import com.sksamuel.avro4s.SchemaFor
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb
import net.liftweb.common.{Box, _}
import net.liftweb.json.{MappingException, parse}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait KafkaMappedConnector_vSept2018 extends Connector with KafkaHelper with MdcLoggable {
  
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
      isFirst <- Full(gatewayLoginRequestPayLoad.is_first)
      correlationId <- Full(cc.correlationId)
      sessionId <- Full(cc.sessionId.getOrElse(""))
      //Here, need separate the GatewayLogin and other Types, because of for Gatewaylogin, there is no user here. Others, need sign up user in OBP side. 
      basicUserAuthContexts <- cc.gatewayLoginRequestPayload match {
        case None => 
          for{
            user <- Users.users.vend.getUserByUserName(username)
            userAuthContexts<- UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(user.userId)
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
      cc <- tryo {callContext.get} ?~! NoCallContext
      user <- cc.user
      username <- tryo(user.name)
      currentResourceUserId <- Some(user.userId)
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
      isFirst <- tryo(gatewayLoginPayLoad.is_first)
      correlationId <- tryo(cc.correlationId)
      sessionId <- tryo(cc.sessionId.getOrElse(""))
      permission <- Views.views.vend.getPermissionForUser(user)
      views <- tryo(permission.views)
      linkedCustomers <- tryo(Customer.customerProvider.vend.getCustomersByUserId(user.userId))
      likedCustomersBasic = JsonFactory_vSept2018.createBasicCustomerJson(linkedCustomers)
      userAuthContexts<- UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(user.userId) 
      basicUserAuthContexts = JsonFactory_vSept2018.createBasicUserAuthContextJson(userAuthContexts)
      authViews<- Full(
        for{
          view <- views              //TODO, need double check whether these data come from OBP side or Adapter.
          (account, callContext )<- code.bankconnectors.LocalMappedConnector.getBankAccount(view.bankId, view.accountId, Some(cc)) ?~! {BankAccountNotFound}
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
      )
    } yield{
      AuthInfo(currentResourceUserId, username, cbs_token, isFirst, correlationId, sessionId, likedCustomersBasic, basicUserAuthContexts, authViews)
    }
  
  val viewBasicExample = ViewBasic("owner","Owner", "This is the owner view")

  val internalBasicCustomerExample = InternalBasicCustomer(
    bankId = bankIdExample.value,
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
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: Something went wrong."
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
    viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil,
    bankRoutingScheme = bankRoutingSchemeExample.value,
    bankRoutingAddress = bankRoutingAddressExample.value,
    branchRoutingScheme = branchRoutingSchemeExample.value,
    branchRoutingAddress = branchRoutingAddressExample.value,
    accountRoutingScheme = accountRoutingSchemeExample.value,
    accountRoutingAddress = accountRoutingAddressExample.value,
    accountRouting = Nil,
    accountRules = Nil)



  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
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
  
  override def getAdapterInfoFuture(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
    val req = OutboundGetAdapterInfo(DateWithSecondsExampleString)

    logger.debug(s"Kafka getAdapterInfoFuture Req says:  is: $req")

    val future = for {
      res <- processToFuture[OutboundGetAdapterInfo](req) map {
        f =>
          try {
            f.extract[InboundAdapterInfo]
          } catch {
            case e: Exception =>
              val received = liftweb.json.compactRender(f)
              val expected = SchemaFor[InboundAdapterInfo]().toString(false)
              val err = s"Extraction Failed: You received this ($received). We expected this ($expected)"
              sendOutboundAdapterError(err)
              throw new MappingException(err, e)
          }
      } map {
        x => x.data
      }
    } yield {
      Full(res)
    }

    val res = future map {
      case Full(list) if (list.errorCode=="") =>
        Full(list, callContext)
      case Full(list) if (list.errorCode!="") =>
        Failure("INTERNAL-"+ list.errorCode+". + CoreBank-Status:"+ list.backendMessages)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    logger.debug(s"Kafka getAdapterInfoFuture says res is $res")
    res
  }

  messageDocs += MessageDoc(
    process = "obp.get.User",
    messageFormat = messageFormat,
    description = "Gets the User as identified by the the credentials (username and password) supplied.",
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
        //Note: Here we omit the userId and cbsToken here, we do not use it in Adapter sie.
        val req = OutboundGetUserByUsernamePassword(AuthInfo("", username, ""), password = password)

        logger.debug(s"Kafka getUser Req says:  is: $req")

        val box = for {
          kafkaMessage <- processToBox[OutboundGetUserByUsernamePassword](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetUserByUsernamePassword]().toString(false)
          inboundGetUserByUsernamePassword <- tryo{kafkaMessage.extract[InboundGetUserByUsernamePassword]} ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          inboundValidatedUser <- Full(inboundGetUserByUsernamePassword.data)
        } yield{
          inboundValidatedUser
        }

        logger.debug(s"Kafka getUser Res says:  is: $box")

        val res = box match {
          case Full(list) if (list.errorCode=="" && username == list.displayName) =>
            Full(new InboundUser(username, password, username))
          case Full(list) if (list.errorCode!="") =>
            Failure("INTERNAL-"+ list.errorCode+". + CoreBank-Status:"+ list.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }

        res

      }
    }
  }("getUser")


  messageDocs += MessageDoc(
    process = "obp.get.Banks",
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
        logger.debug(s"Kafka getBanks Req is: $req")

        val box: Box[(List[InboundBank], Status)] = for {
         _ <- Full(logger.debug("Enter GetBanks BOX1: prekafka") )
          kafkaMessage <- processToBox[OutboundGetBanks](req)
         _ <- Full(logger.debug(s"Enter GetBanks BOX2: postkafka: $kafkaMessage") )
         inboundGetBanks <- tryo{kafkaMessage.extract[InboundGetBanks]} ?~! s"$InboundGetBanks extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
         _ <- Full(logger.debug(s"Enter GetBanks BOX3 : $inboundGetBanks") )
         (inboundBanks, status) <- Full(inboundGetBanks.data, inboundGetBanks.status)
         _ <- Full(logger.debug(s"Enter GetBanks BOX4: $inboundBanks") )
        } yield {
          (inboundBanks, status)
        }

        logger.debug(s"Kafka getBanks Res says:  is: $box")
        val res = box match {
          case Full((banks, status)) if (status.errorCode=="") =>
            Full((banks map (new Bank2(_)),callContext))
          case Full((banks, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        logger.debug(s"Kafka getBanks says res is $res")
        res
      }
    }
  }("getBanks")

  override def getBanksFuture(callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val req = OutboundGetBanks(AuthInfo())
        logger.debug(s"Kafka getBanksFuture Req is: $req")

        val future = for {
          res <- processToFuture[OutboundGetBanks](req) map {
            f =>
              try {
                f.extract[InboundGetBanks]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetBanks]().toString(false)
                  val err = s"Extraction Failed: You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(err)
                  throw new MappingException(err, e)
              }
          } map {
            (x => (x.data, x.status))
          }
        } yield {
          Full(res)
        }

        val res = future map {
          case Full((banks, status)) if (status.errorCode=="") =>
            val banksResponse =  banks map (new Bank2(_))
            logger.debug(s"Kafka getBanksFuture Res says:  is: $banksResponse")
            Full((banksResponse, callContext))
          case Full((banks, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        logger.debug(s"Kafka getBanksFuture says res is $res")
        res
      }
    }
  }("getBanks")

  messageDocs += MessageDoc(
    process = "obp.get.Bank",
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
  override def getBank(bankId: BankId, callContext: Option[CallContext]) =  saveConnectorMetric {
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

        val box: Box[(InboundBank, Status)] = for {
          kafkaMessage <- processToBox[OutboundGetBank](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetBank]().toString(false)
          inboundGetBank <- tryo {
            kafkaMessage.extract[InboundGetBank]
          } ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          (inboundBank, status) <- Full(inboundGetBank.data, inboundGetBank.status)
        } yield {
          (inboundBank, status)
        }


        logger.debug(s"Kafka getBank Res says:  is: $box")

        box match {
          case Full((bank, status)) if (status.errorCode == "") =>
            Full((new Bank2(bank), callContext))
          case Full((_, status)) if (status.errorCode != "") =>
            Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse)
          case Failure(msg, e, c) =>
            logger.error(msg, e)
            logger.error(msg)
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }

      }
    }
  }("getBank")

  override def getBankFuture(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric {
     /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        val req = OutboundGetBank(authInfo = AuthInfo(), bankId.toString)
        logger.debug(s"Kafka getBankFuture Req is: $req")

        val future = for {
          res <- processToFuture[OutboundGetBank](req) map {
            f =>
              try {
                f.extract[InboundGetBank]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetBank]().toString(false)
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {
            (x => (x.data, x.status))
          }
        } yield {
          Full(res)
        }

        val res = future map {
          case Full((bank, status)) if (status.errorCode=="") =>
            val bankResponse =  (new Bank2(bank))
            logger.debug(s"Kafka getBankFuture Res says:  is: $bankResponse")
            Full((bankResponse, callContext))
          case Full((bank, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        logger.debug(s"Kafka getBankFuture says res is $res")
        res
      }
    }
  }("getBank")

  messageDocs += MessageDoc(
    process = "obp.get.BankAccountsForUser",
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
  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountsTTL second) {
        //TODO, these Customers should not be get from here, it make some side effects. It is better get it from Parameters.
        val currentResourceUserId = AuthUser.getCurrentResourceUserUserId
        val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
        val internalCustomers = JsonFactory_vSept2018.createCustomersJson(customerList)
      
        val box = for {
          authInfo <- getAuthInfoFirstCbsCall(username, callContext)
          req = OutboundGetAccounts(authInfo, internalCustomers)
          kafkaMessage <- processToBox[OutboundGetAccounts](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetAccounts]().toString(false)
          inboundGetAccounts <- tryo{kafkaMessage.extract[InboundGetAccounts]} ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          (inboundAccountSept2018, status) <- Full(inboundGetAccounts.data, inboundGetAccounts.status)
        } yield{
          (inboundAccountSept2018, status)
        }
        logger.debug(s"Kafka getBankAccounts says res is $box")

        box match {
          case Full((data, status)) if (status.errorCode=="") =>
            Full(data, callContext)
          case Full((data, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBankAccounts")

  override def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]):  Future[Box[(List[InboundAccountSept2018], Option[CallContext])]] = saveConnectorMetric{
     /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountsTTL second) {
        //TODO, these Customers should not be get from here, it make some side effects. It is better get it from Parameters.
        val currentResourceUserId = AuthUser.getCurrentResourceUserUserId
        val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
        val internalCustomers = JsonFactory_vSept2018.createCustomersJson(customerList)

        //TODO we maybe have an issue here, we set the `cbsToken = Empty`, this method will get the cbkToken back. 
        val req = OutboundGetAccounts(
          getAuthInfoFirstCbsCall(username, callContext).openOrThrowException(s"$attemptedToOpenAnEmptyBox getBankAccountsFuture.callContext is Empty !"),
          internalCustomers
        )
        logger.debug(s"Kafka getBankAccountsFuture says: req is: $req")

        val future = for {
          res <- processToFuture[OutboundGetAccounts](req) map {
            f =>
              try {
                f.extract[InboundGetAccounts]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetAccounts]().toString(false)
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {
            (x => (x.data, x.status))
          }
        } yield {
          res
        }
        logger.debug(s"Kafka getBankAccounts says res is $future")

        future map {
          case (data, status) if (status.errorCode=="") =>
            Full(data,callContext)
          case (data, status) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case (List(), status) =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBankAccountsFuture")
  
  messageDocs += MessageDoc(
    process = "obp.get.Account",
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
  override def getBankAccount(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
        val box = for {
          authInfo <- getAuthInfo(callContext)
          req = OutboundGetAccountbyAccountID(authInfo, bankId.toString, accountId.value)
          _ <- Full(logger.debug(s"Kafka getBankAccount says: req is: $req"))
          kafkaMessage <- processToBox[OutboundGetAccountbyAccountID](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetAccountbyAccountID]().toString(false)
          inboundGetAccountbyAccountID <- tryo{kafkaMessage.extract[InboundGetAccountbyAccountID]} ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          (inboundAccountSept2018, status) <- Full(inboundGetAccountbyAccountID.data, inboundGetAccountbyAccountID.status)
        } yield{
          (inboundAccountSept2018, status)
        }

        logger.debug(s"Kafka getBankAccount says res is $box")
        box match {
          case Full((Some(data), status)) if (status.errorCode=="") =>
            Full(new BankAccountSept2018(data), callContext)
          case Full((data, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBankAccount")
  
  messageDocs += MessageDoc(
    process = "obp.check.BankAccountExists",
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
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext])= saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
        val box = for {
          authInfo <- getAuthInfo(callContext)
            req = OutboundCheckBankAccountExists(
            authInfo = authInfo,
            bankId = bankId.toString,
            accountId = accountId.value
          )
          _ <- Full(logger.debug(s"Kafka checkBankAccountExists says: req is: $req"))
          kafkaMessage <- processToBox[OutboundCheckBankAccountExists](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundCheckBankAccountExists]().toString(false)
          inboundCheckBankAccountExists <- tryo{kafkaMessage.extract[InboundCheckBankAccountExists]} ?~! s"$InboundCheckBankAccountExists extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
          (inboundAccountSept2018, status) <- Full(inboundCheckBankAccountExists.data, inboundCheckBankAccountExists.status)
        } yield{
          (inboundAccountSept2018, status)
        }

        logger.debug(s"Kafka checkBankAccountExists says res is $box")
        box match {
          case Full((Some(data), status)) if (status.errorCode=="") =>
            Full(new BankAccountSept2018(data), callContext)
          case Full((data,status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBankAccount")

  override def checkBankAccountExistsFuture(bankId: BankId, accountId: AccountId, callContext: Option[CallContext]) =
    Future {
      checkBankAccountExists(bankId, accountId, callContext)
    }
  
  messageDocs += MessageDoc(
    process = "obp.get.coreBankAccounts",
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
  override def getCoreBankAccounts(BankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]) : Box[(List[CoreAccount], Option[CallContext])]  = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
        val box = for {
          authInfo <- getAuthInfo(callContext)
          req = OutboundGetCoreBankAccounts(
            authInfo = authInfo,
            BankIdAccountIds
          )
          _<-Full(logger.debug(s"Kafka getCoreBankAccounts says: req is: $req"))
          kafkaMessage <- processToBox[OutboundGetCoreBankAccounts](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetCoreBankAccounts]().toString(false)
          inboundGetCoreBankAccounts <- tryo{kafkaMessage.extract[InboundGetCoreBankAccounts]} ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          internalInboundCoreAccounts <- Full(inboundGetCoreBankAccounts.data)
        } yield{
          internalInboundCoreAccounts
        }
        logger.debug(s"Kafka getCoreBankAccounts says res is $box")

        box match {
          case Full(f) if (f.head.errorCode=="") =>
            Full(f.map( x => CoreAccount(x.id,x.label,x.bankId,x.accountType, x.accountRoutings)),callContext)
          case Full(f) if (f.head.errorCode!="") =>
            Failure("INTERNAL-"+ f.head.errorCode+". + CoreBank-Status:"+ f.head.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getBankAccounts")

  override def getCoreBankAccountsFuture(BankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountsTTL second){

        val req = OutboundGetCoreBankAccounts(
          authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
          BankIdAccountIds
        )
        logger.debug(s"Kafka getCoreBankAccountsFuture says: req is: $req")

        val future = for {
          res <- processToFuture[OutboundGetCoreBankAccounts](req) map {
            f =>
              try {
                f.extract[InboundGetCoreBankAccounts]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetCoreBankAccounts]().toString(false)
                  val err = s"Extraction Failed: You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(err)
                  throw new MappingException(err, e)
              }
          } map {
            _.data
          }
        } yield {
          res
        }
        logger.debug(s"Kafka getCoreBankAccountsFuture says res is $future")

        future map {
          case list if (list.head.errorCode=="") =>
            Full(list.map( x => CoreAccount(x.id,x.label,x.bankId,x.accountType, x.accountRoutings)), callContext)
          case list if (list.head.errorCode!="") =>
            Failure("INTERNAL-"+ list.head.errorCode+". + CoreBank-Status:"+ list.head.backendMessages)
          case List() =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case _ =>
            Failure(ErrorMessages.UnknownError)
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
    process = "obp.get.Transactions",
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
  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = saveConnectorMetric {
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

    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCached to cache data.
    def getTransactionsCached(req: OutboundGetTransactions): Box[(List[Transaction],Option[CallContext])] = {
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
          val box = for {
            kafkaMessage <- processToBox[OutboundGetTransactions](req)
            received = liftweb.json.compactRender(kafkaMessage)
            expected = SchemaFor[InboundGetTransactions]().toString(false)
            inboundGetTransactions <- tryo {
              kafkaMessage.extract[InboundGetTransactions]
            } ?~! s"$InvalidConnectorResponseForGetTransactions Extraction Failed: You received this ($received). We expected this ($expected)"
            (internalTransactions, status) <- Full(inboundGetTransactions.data, inboundGetTransactions.status)
          } yield {
            (internalTransactions, status)
          }
          logger.debug(s"Kafka getTransactions says: res is: $box")

          box match {
            case Full((data, status)) if (status.errorCode != "") =>
              Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages)
            case Full((data, status)) if (!data.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)) =>
              Failure(InvalidConnectorResponseForGetTransactions)
            case Full((data, status)) if (status.errorCode == "") =>
              val bankAccountAndCallContext = checkBankAccountExists(BankId(data.head.bankId), AccountId(data.head.accountId), callContext)

              val res = for {
                internalTransaction <- data
                thisBankAccount <- bankAccountAndCallContext.map(_._1) ?~! ErrorMessages.BankAccountNotFound
                transaction <- createInMemoryTransaction(thisBankAccount, internalTransaction)
              } yield {
                transaction
              }
              Full((res, bankAccountAndCallContext.map(_._2).openOrThrowException(attemptedToOpenAnEmptyBox)))
            case Empty =>
              Failure(ErrorMessages.ConnectorEmptyResponse)
            case Failure(msg, e, c) =>
              Failure(msg, e, c)
            case _ =>
              Failure(ErrorMessages.UnknownError)
          }
        }
      }
    }
    getTransactionsCached(req)

  }("getTransactions")
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = saveConnectorMetric{
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
    
    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCoreCached to cache data.
    //Note: getTransactionsCoreCached and getTransactionsCached have the same parameters,but the different method name.
    def getTransactionsCoreCached(req:OutboundGetTransactions): Box[(List[TransactionCore], Option[CallContext])] = {
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
          val box = for {
            kafkaMessage <- processToBox[OutboundGetTransactions](req)
            received = liftweb.json.compactRender(kafkaMessage)
            expected = SchemaFor[InboundGetTransactions]().toString(false)
            inboundGetTransactions <- tryo {
              kafkaMessage.extract[InboundGetTransactions]
            } ?~! s"$InvalidConnectorResponseForGetTransactions Extraction Failed: You received this ($received). We expected this ($expected)"
            (internalTransactions, status) <- Full(inboundGetTransactions.data, inboundGetTransactions.status)
          } yield {
            (internalTransactions, status)
          }
          logger.debug(s"Kafka getTransactions says: res is: $box")

          box match {
            case Full((data, status)) if (status.errorCode != "") =>
              Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages)
            case Full((data, status)) if (!data.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)) =>
              Failure(InvalidConnectorResponseForGetTransactions)
            case Full((data, status)) if (status.errorCode == "") =>
              for{
                (thisBankAccount, callContext) <- checkBankAccountExists(BankId(data.head.bankId), AccountId(data.head.accountId), callContext) ?~! ErrorMessages.BankAccountNotFound
                transaction <- createInMemoryTransactionsCore(thisBankAccount, data)
              } yield {
                (transaction, callContext)
              }
            case Empty =>
              Failure(ErrorMessages.ConnectorEmptyResponse)
            case Failure(msg, e, c) =>
              Failure(msg, e, c)
            case _ =>
              Failure(ErrorMessages.UnknownError)
          }
        }
      }
    }
    getTransactionsCoreCached(req)
    
  }("getTransactions")
  
  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
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
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionTTL second){
        val box = for {
          authInfo <- getAuthInfo(callContext)
          req =  OutboundGetTransaction(authInfo,bankId.value, accountId.value, transactionId.value)
          _ <- Full(logger.debug(s"Kafka getTransaction Req says:  is: $req"))
          kafkaMessage <- processToBox[OutboundGetTransaction](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetTransaction]().toString(false)
          inboundGetTransaction <- tryo{kafkaMessage.extract[InboundGetTransaction]} ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
            sendOutboundAdapterError(error)
            error
          }
          (internalTransaction, status) <- Full(inboundGetTransaction.data, inboundGetTransaction.status)
        } yield{
          (internalTransaction, status)
        }
        logger.debug(s"Kafka getTransaction Res says: is: $box")

        box match {
          // Check does the response data match the requested data
          case Full((Some(data), status)) if (transactionId.value != data.transactionId) =>
            Failure(s"$InvalidConnectorResponseForGetTransaction")
          case Full((data,status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Full((Some(data), status)) if (transactionId.value == data.transactionId && status.errorCode=="") =>
            for {
              (bankAccount, callContext) <- checkBankAccountExists(BankId(data.bankId), AccountId(data.accountId),callContext) ?~! ErrorMessages.BankAccountNotFound
              transaction: Transaction <- createInMemoryTransaction(bankAccount,data)
            } yield {
              (transaction,callContext)
            }
          case Full((data,status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }}("getTransaction")
  
  messageDocs += MessageDoc(
    process = "obp.create.Challenge",
    messageFormat = messageFormat,
    description = "Create a Security Challenge that may be used to complete a Transaction Request.",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundChallengeBase.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundChallengeBase.getClass.getSimpleName).response),
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
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]) = {
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
             val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.create.Counterparty",
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
  
    val box = for {
      authInfo <- getAuthInfo(callContext)
        req  = OutboundCreateCounterparty(
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
      _<- Full(logger.debug(s"Kafka createCounterparty Req says: is: $req"))
      kafkaMessage <- processToBox[OutboundCreateCounterparty](req)
      inboundCreateCounterparty <- tryo{kafkaMessage.extract[InboundCreateCounterparty]} ?~! s"$InboundCreateCounterparty extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
      (internalCounterparty, status) <- Full(inboundCreateCounterparty.data, inboundCreateCounterparty.status)
    } yield{
      (internalCounterparty, status)
    }
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
    
    val res = box match {
      case Full((Some(data), status)) if (status.errorCode=="")  =>
        Full((data, callContext))
      case Full((data, status)) if (status.errorCode!="") =>
        Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
      case Empty =>
        Failure(ErrorMessages.ConnectorEmptyResponse)
      case Failure(msg, e, c) =>
        Failure(msg, e, c)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
    res
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.transactionRequests210",
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
          kafkaMessage <- processToBox[OutboundGetTransactionRequests210](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetTransactionRequests210]().toString(false)
          inboundGetTransactionRequests210 <- tryo{kafkaMessage.extract[InboundGetTransactionRequests210]} ?~! s"$InvalidConnectorResponseForGetTransactionRequests210, Extraction Failed: You received this ($received). We expected this ($expected)"
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
              obpTransactionRequests <- LocalMappedConnector.getTransactionRequestsImpl210(fromAccount) ?~! s"$ConnectorEmptyResponse, error on LocalMappedConnector.getTransactionRequestsImpl210"
            } yield {
              adapterTransactionRequests ::: obpTransactionRequests
            }
            transactionRequest.map(transactionRequests =>(transactionRequests, callContext))
          case Full((data, status)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Empty =>
            Failure(ErrorMessages.ConnectorEmptyResponse)
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
    process = "obp.get.counterparties",
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

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId, callContext: Option[CallContext] = None) = saveConnectorMetric{
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
          kafkaMessage <- processToBox[OutboundGetCounterparties](req)
          received = liftweb.json.compactRender(kafkaMessage)
          expected = SchemaFor[InboundGetCounterparties]().toString(false)
          inboundGetCounterparties <- tryo{kafkaMessage.extract[InboundGetCounterparties]} ?~! {
            val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
            Failure(ErrorMessages.ConnectorEmptyResponse)
          case Failure(msg, e, c) =>
            Failure(msg, e, c)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        res
      }
    }
  }("getCounterparties")
  override def getCounterpartiesFuture(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId, callContext: Option[CallContext] = None): OBPReturnType[Box[List[CounterpartyTrait]]] = Future {
    (getCounterparties(thisBankId, thisAccountId, viewId, callContext) map (i => i._1), callContext)
  }
  
  messageDocs += MessageDoc(
    process = "obp.get.CounterpartyByCounterpartyId",
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
  override def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext])= saveConnectorMetric{
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
                 val err = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.CounterpartyTrait",
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
             val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.CustomersByUserIdFuture",
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
    adapterImplementation = Some(AdapterImplementation("Accounts", 0))
  )

  override def getCustomersByUserIdFuture(userId: String , @CacheKeyOmit callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]] = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(customersByUserIdBoxTTL second) {

        val req = OutboundGetCustomersByUserId(getAuthInfo(callContext).openOrThrowException(NoCallContext))
        logger.debug(s"Kafka getCustomersByUserIdFuture Req says: is: $req")

        val future = for {
          res <- processToFuture[OutboundGetCustomersByUserId](req) map {
            f =>
              try {
                f.extract[InboundGetCustomersByUserId]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetCustomersByUserId]().toString(false)
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
                  sendOutboundAdapterError(error)
                  throw new MappingException(error, e)
              }
          } map {x => (x.data, x.status)}
        } yield{
          res
        }
        logger.debug(s"Kafka getCustomersByUserIdFuture Res says: is: $future")

        val res = future map {
          case (list, status) if (status.errorCode=="") =>
            Full(JsonFactory_vJune2017.createObpCustomers(list), callContext)
          case (list, status) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:" + status.backendMessages)
          case (List(),status) =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        res
      }
    }
  }("getCustomersByUserIdFuture")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.CheckbookOrdersFuture",
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

  override def getCheckbookOrdersFuture(
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
                  val error = s"correlationId(${req.authInfo.correlationId}): Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.StatusOfCreditCardOrderFuture",
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

  override def getStatusOfCreditCardOrderFuture(
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
                  val error = s"correlationId(${req.authInfo.correlationId}): Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.Branches",
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

  override def getBranchesFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = saveConnectorMetric {
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
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.Branch",
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

  override def getBranchFuture(bankId : BankId, branchId: BranchId, callContext: Option[CallContext])  = saveConnectorMetric {

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
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.Atms",
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

  override def getAtmsFuture(bankId: BankId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = saveConnectorMetric {
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
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.Atm",
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

  override def getAtmFuture(bankId : BankId, atmId: AtmId, callContext: Option[CallContext]) = saveConnectorMetric {
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
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
    process = "obp.get.getChallengeThreshold",
    messageFormat = messageFormat,
    description = "Get Challenge Threshold",
    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetChallengeThreshold.getClass.getSimpleName).request),
    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetChallengeThreshold.getClass.getSimpleName).response),
    exampleOutboundMessage = (OutboundGetChallengeThreshold(
      authInfoExample,
      bankId = bankIdExample.value,
      accountId = accountIdExample.value,
      viewId = "owner",
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

        val future = for {
          res <- processToFuture[OutboundGetChallengeThreshold](req) map {
            f =>
              try {
                f.extract[InboundGetChallengeThreshold]
              } catch {
                case e: Exception =>
                  val received = liftweb.json.compactRender(f)
                  val expected = SchemaFor[InboundGetChallengeThreshold]().toString(false)
                  val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
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
          case (amountOfMoney, status) if (status.errorCode=="") =>
            (Full(amountOfMoney), callContext)
          case (_, status) if (status.errorCode!="") =>
            (Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages), callContext)
          case _ =>
            (Failure(ErrorMessages.UnknownError), callContext)
        }
      }
    }
  }("getChallengeThreshold")
  
  messageDocs += MessageDoc(
    process = "obp.create.makePaymentv210",
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
    
    // Since result is single account, we need only first list entry
    val future = for {
      res <- processToFuture[OutboundCreateTransaction](req) map {
        f =>
          try {
            f.extract[InboundCreateTransactionId]
          } catch {
            case e: Exception =>
              val received = liftweb.json.compactRender(f)
              val expected = SchemaFor[InboundCreateTransactionId]().toString(false)
              val error = s"Extraction Failed: You received this ($received). We expected this ($expected)"
              sendOutboundAdapterError(error)
              throw new MappingException(error, e)
          }
      } map {
        (x => (x.inboundAuthInfo, x.status,  x.data))
      }
    } yield {
      Full(res)
    }
    
    val res = future map {
      case Full((authInfo, status,  data )) if (status.errorCode=="") =>
        (Full(TransactionId(data.id)), callContext)
      case Full((authInfo, status,  data )) if (status.errorCode!="") =>
        (Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Error:"+ status.backendMessages), callContext)
      case _ =>
        (Failure(ErrorMessages.UnknownError), callContext)
    }
    
    res
  }
  
}


object KafkaMappedConnector_vSept2018 extends KafkaMappedConnector_vSept2018{
  
}


