package code.bankconnectors.vJune2017

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE Ltd

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
import java.util.{Date, Locale}

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, getSecondsCache, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util._
import code.api.util.APIUtil._
import com.openbankproject.commons.model.{AccountV310Json, CardObjectJson, CheckbookOrdersJson}
import code.bankconnectors._
import code.bankconnectors.vMar2017._

import code.customer._
import code.kafka.KafkaHelper
import code.model._
import code.model.dataAccess._
import code.transactionrequests.TransactionRequests._
import code.util.Helper.MdcLoggable
import com.google.common.cache.CacheBuilder
import com.openbankproject.commons.model.{CounterpartyTrait, _}
import com.sksamuel.avro4s.SchemaFor
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb.common.{Box, _}
import net.liftweb.json
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Extraction, MappingException, parse}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait KafkaMappedConnector_vJune2017 extends Connector with KafkaHelper with MdcLoggable {
  
  implicit override val nameOfConnector = KafkaMappedConnector_vJune2017.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "June2017"

  //This is special method, it is only used for the first cbs call. cbsToken can be empty here.
  def getAuthInfoFirstCbsCall (username: String, callContext: Option[CallContext]): Box[AuthInfo]=
    for{
      cc <- tryo {callContext.get} ?~! NoCallContext
      gatewayLoginRequestPayLoad <- cc.gatewayLoginRequestPayload
      isFirst <- Full(gatewayLoginRequestPayLoad.is_first)
      correlationId <- Full(cc.correlationId)
      sessionId <- Full(cc.sessionId.getOrElse(""))
    } yield{
      AuthInfo("",username, "", isFirst, correlationId, sessionId)
    }
  
  def getAuthInfo (callContext: Option[CallContext]): Box[AuthInfo]=
    for{
      cc <- tryo {callContext.get} ?~! NoCallContext
      user <- cc.user
      username <- Full(user.name)
      currentResourceUserId <- Some(user.userId)
      gatewayLoginRequestPayLoad <- cc.gatewayLoginRequestPayload
      cbs_token <- gatewayLoginRequestPayLoad.cbs_token.orElse(Full(""))
      isFirst <- Full(gatewayLoginRequestPayLoad.is_first)
      correlationId <- Full(cc.correlationId)
      sessionId <- Full(cc.sessionId.getOrElse(""))
    } yield{
      AuthInfo(currentResourceUserId,username, cbs_token, isFirst,correlationId, sessionId)
    }

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val inboundStatusMessagesExample = List(InboundStatusMessage("ESB", "Success", "0", "OK"))
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."
  val statusExample = Status(errorCodeExample, inboundStatusMessagesExample)
  
  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "getAdapterInfo from Adapter, just for testing kafka and Adapter setting.  ",
    exampleOutboundMessage = (
      OutboundGetAdapterInfo(authInfoExample, date = DateWithSecondsExampleString)
    ),
    exampleInboundMessage = (
      InboundAdapterInfo(
        authInfoExample,
        InboundAdapterInfoInternal(
          errorCodeExample,
          inboundStatusMessagesExample,
          name = "Obp-Kafka-South",
          version = "June2017",
          git_commit = "...",
          date = DateWithSecondsExampleString
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetAdapterInfo]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundAdapterInfoInternal]().toString(true)))
  )
  override def getAdapterInfoFuture(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
    val req = OutboundGetAdapterInfo(
      AuthInfo(sessionId = callContext.get.correlationId),
      DateWithSecondsExampleString
    )

    logger.debug(s"Kafka getAdapterInfoFuture Req says:  is: $req")

    val future = for {
      res <- processToFuture[OutboundGetAdapterInfo](req) map {
        f =>
          try {
            f.extract[InboundAdapterInfo]
          } catch {
            case e: Exception =>
              val received = json.compactRender(f)
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
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "getBanks",
    exampleOutboundMessage = (
      OutboundGetBanks(authInfoExample)
    ),
    exampleInboundMessage = (
      InboundGetBanks(
        authInfoExample,
        Status(
          errorCode = errorCodeExample,
          inboundStatusMessagesExample),
        InboundBank(
          bankId = "gh.29.uk",
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )  :: Nil
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBanks]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBanks]().toString(true)))
  )
  override def getBanks(callContext: Option[CallContext])= saveConnectorMetric {
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

        val box = for {
         _ <- Full(logger.debug("Enter GetBanks BOX1: prekafka") )
          kafkaMessage <- processToBox[OutboundGetBanks](req)
         _ <- Full(logger.debug(s"Enter GetBanks BOX2: postkafka: $kafkaMessage") )
         inboundGetBanks <- tryo{kafkaMessage.extract[InboundGetBanks]} ?~! s"$InboundGetBanks extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
         _ <- Full(logger.debug(s"Enter GetBanks BOX3 : $inboundGetBanks") )
         (authInfo, inboundBanks, status) <- Full(inboundGetBanks.authInfo, inboundGetBanks.data, inboundGetBanks.status)
         _ <- Full(logger.debug(s"Enter GetBanks BOX4: $inboundBanks") )
        } yield {
          (authInfo, inboundBanks, status)
        }

        logger.debug(s"Kafka getBanks Res says:  is: $box")
        val res = box match {
          case Full((authInfo, banks, status)) if (status.errorCode=="") =>
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            Full((banks.map(Bank2(_)), callContextUpdated))
          case Full((authInfo, banks, status)) if (status.errorCode!="") =>
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
                case e: Exception => throw new MappingException(s"$InboundGetBanks extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
              }
          } map {
            (x => (x.authInfo, x.data, x.status))
          }
        } yield {
          Full(res)
        }

        val res = future map {
          case Full((authInfo, banks, status)) if (status.errorCode=="") =>
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            val banksResponse =  banks map (new Bank2(_))
            logger.debug(s"Kafka getBanksFuture Res says:  is: $banksResponse")
            Full((banksResponse, callContextUpdated))
          case Full((authInfo, banks, status)) if (status.errorCode!="") =>
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
    description = "getBank from kafka ",
    exampleOutboundMessage = (
      OutboundGetBank(authInfoExample,"bankId")
    ),
    exampleInboundMessage = (
      InboundGetBank(
        authInfoExample,
        Status(
          errorCodeExample,
          inboundStatusMessagesExample),
        InboundBank(
          bankId = "gh.29.uk",
          name = "sushan",
          logo = "TESOBE",
          url = "https://tesobe.com/"
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBank]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBank]().toString(true)))
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

        val box= for {
          kafkaMessage <- processToBox[OutboundGetBank](req)
          inboundGetBank <- tryo {
            kafkaMessage.extract[InboundGetBank]
          } ?~! s"$InboundGetBank extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
          (authInfo, inboundBank, status) <- Full(inboundGetBank.authInfo, inboundGetBank.data, inboundGetBank.status)
        } yield {
          (authInfo, inboundBank, status)
        }


        logger.debug(s"Kafka getBank Res says:  is: $box")

        box match {
          case Full((authInfo, bank, status)) if (status.errorCode == "") =>
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            Full((new Bank2(bank), callContextUpdated))
          case Full((_, _, status)) if (status.errorCode != "") =>
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
  
  override def getBankFuture(bankId: BankId , callContext: Option[CallContext]) = saveConnectorMetric {
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
                case e: Exception => throw new MappingException(s"$InboundGetBank extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
              }
          } map {
            (x => (x.authInfo, x.data, x.status))
          }
        } yield {
          Full(res)
        }

        val res = future map {
          case Full((authInfo, bank, status)) if (status.errorCode=="") =>
            val bankResponse = (new Bank2(bank))
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            logger.debug(s"Kafka getBankFuture Res says:  is: $bankResponse")
            Full((bankResponse, callContextUpdated))
          case Full((authInfo, bank, status)) if (status.errorCode!="") =>
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
    process = "obp.get.Accounts",
    messageFormat = messageFormat,
    description = "getBankAccounts from kafka",
    exampleOutboundMessage = (
      OutboundGetAccounts(
        authInfoExample,
        InternalBasicCustomers(customers =List(
          InternalBasicCustomer(
            bankId="bankId",
            customerId = "customerId",
            customerNumber = "customerNumber",
            legalName = "legalName",
            dateOfBirth = DateWithSecondsExampleObject
          ))))
    ),
    exampleInboundMessage = (
      InboundGetAccounts(authInfoExample, statusExample, InboundAccountJune2017("", cbsToken ="cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil) :: Nil)
    )
  )
  override def getBankAccountsForUser(username: String, callContext: Option[CallContext]): Box[(List[InboundAccount], Option[CallContext])] = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext], internalCustomers: InternalBasicCustomers) = {
      val box = for {
        authInfo <- getAuthInfoFirstCbsCall(username, callContext)
        req = OutboundGetAccounts(authInfo, internalCustomers)
        kafkaMessage <- processToBox[OutboundGetAccounts](req)
        inboundGetAccounts <- tryo {
          kafkaMessage.extract[InboundGetAccounts]
        } ?~! s"$InboundGetAccounts extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
        (inboundAccountJune2017, status) <- Full(inboundGetAccounts.data, inboundGetAccounts.status)
      } yield {
        (inboundAccountJune2017, status, callContext)
      }
      logger.debug(s"Kafka getBankAccounts says res is $box")
      
      box
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountsTTL second) {
        //TODO, these Customers should not be get from here, it make some side effects. It is better get it from Parameters.
        val currentResourceUserId = AuthUser.getCurrentResourceUserUserId
        val customerList :List[Customer]= Customer.customerProvider.vend.getCustomersByUserId(currentResourceUserId)
        val internalCustomers = JsonFactory_vJune2017.createCustomersJson(customerList)

        val box = callAdapter(callContext, internalCustomers)
        
        box match {
          case Full((data, status, callerContext)) if (status.errorCode=="") =>
            Full(data, callerContext)
          case Full((data, status, callerContext)) if (status.errorCode!="") =>
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

  override def getBankAccountsForUserFuture(username: String, callContext: Option[CallContext]) = saveConnectorMetric{
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
        val internalCustomers = JsonFactory_vJune2017.createCustomersJson(customerList)

        def callAdapter(callContext: Option[CallContext], internalCustomers: InternalBasicCustomers) = {
          val req = OutboundGetAccounts(getAuthInfoFirstCbsCall(username, callContext).openOrThrowException(s"$attemptedToOpenAnEmptyBox getBankAccountsFuture.callContext is Empty !"), internalCustomers)
          logger.debug(s"Kafka getBankAccountsFuture says: req is: $req")
    
          val future = for {
            res <- processToFuture[OutboundGetAccounts](req) map {
              f =>
                try {
                  f.extract[InboundGetAccounts]
                } catch {
                  case e: Exception => throw new MappingException(s"$InboundGetAccounts extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
                }
            } map {
              (x => (x.data, x.status, callContext))
            }
          } yield {
            res
          }
          logger.debug(s"Kafka getBankAccounts says res is $future")
          future
        }
        
        val future: Future[(List[InboundAccountJune2017], Status, Option[CallContext])] = callAdapter(callContext, internalCustomers)

        future map {
          case (data, status, callContext ) if (status.errorCode=="") =>
            Full(data, callContext)
          case (data, status, callContext) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case (List(), status, callContext) =>
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
    description = "getBankAccount from kafka",
    exampleOutboundMessage = (
      OutboundGetAccountbyAccountID(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = (
      InboundGetAccountbyAccountID(
        authInfoExample,
        statusExample,
        Some(InboundAccountJune2017("", cbsToken = "cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil))))
  )
  override def getBankAccount(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext])= saveConnectorMetric {
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
          inboundGetAccountbyAccountID <- tryo{kafkaMessage.extract[InboundGetAccountbyAccountID]} ?~! s"$InboundGetAccountbyAccountID extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
          (inboundAccountJune2017, status) <- Full(inboundGetAccountbyAccountID.data, inboundGetAccountbyAccountID.status)
        } yield{
          (inboundAccountJune2017, status)
        }

        logger.debug(s"Kafka getBankAccount says res is $box")
        box match {
          case Full((Some(data), status)) if (status.errorCode=="") =>
            Full(new BankAccountJune2017(data), callContext)
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
    description = "checkBankAccountExists from kafka",
    exampleOutboundMessage = (
      OutboundCheckBankAccountExists(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = (
      InboundCheckBankAccountExists(
        authInfoExample,
        statusExample,
        Some(InboundAccountJune2017("", cbsToken = "cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil)))
    )
  )
  override def checkBankAccountExists(bankId: BankId, accountId: AccountId, @CacheKeyOmit callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext]) = {
      val box = for {
        authInfo <- getAuthInfo(callContext)
        req = OutboundCheckBankAccountExists(
          authInfo = authInfo,
          bankId = bankId.toString,
          accountId = accountId.value
        )
        _ <- Full(logger.debug(s"Kafka checkBankAccountExists says: req is: $req"))
        kafkaMessage <- processToBox[OutboundCheckBankAccountExists](req)
        inboundCheckBankAccountExists <- tryo {
          kafkaMessage.extract[InboundCheckBankAccountExists]
        } ?~! s"$InboundCheckBankAccountExists extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
        (inboundAccountJune2017, status) <- Full(inboundCheckBankAccountExists.data, inboundCheckBankAccountExists.status)
      } yield {
        (inboundAccountJune2017, status, callContext)
      }

      logger.debug(s"Kafka checkBankAccountExists says res is $box")
      box
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
        val box = callAdapter(callContext)

        box match {
          case Full((Some(data), status, callContext)) if (status.errorCode=="") =>
            Full(new BankAccountJune2017(data), callContext)
          case Full((data,status,_)) if (status.errorCode!="") =>
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
    description = "getCoreBankAccounts from kafka",
    exampleOutboundMessage = (
      OutboundGetAccountbyAccountID(
        authInfoExample,
        "bankId",
        "accountId"
      )
    ),
    exampleInboundMessage = (
      InboundGetAccountbyAccountID(
        authInfoExample,
        statusExample, 
        Some(InboundAccountJune2017("", cbsToken = "cbsToken", bankId = "gh.29.uk", branchId = "222", accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", accountNumber = "123", accountType = "AC", balanceAmount = "50", balanceCurrency = "EUR", owners = "Susan" :: " Frank" :: Nil, viewsToGenerate = "Public" :: "Accountant" :: "Auditor" :: Nil, bankRoutingScheme = "iban", bankRoutingAddress = "bankRoutingAddress", branchRoutingScheme = "branchRoutingScheme", branchRoutingAddress = " branchRoutingAddress", accountRoutingScheme = "accountRoutingScheme", accountRoutingAddress = "accountRoutingAddress", accountRouting = Nil, accountRules = Nil))))
  )
  override def getCoreBankAccounts(BankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext]) = {
      val box = for {
        authInfo <- getAuthInfo(callContext)
        req = OutboundGetCoreBankAccounts(
          authInfo = authInfo,
          BankIdAccountIds
        )
        _ <- Full(logger.debug(s"Kafka getCoreBankAccounts says: req is: $req"))
        kafkaMessage <- processToBox[OutboundGetCoreBankAccounts](req)
        inboundGetCoreBankAccounts <- tryo {
          kafkaMessage.extract[InboundGetCoreBankAccounts]
        } ?~! s"$InboundGetCoreBankAccounts extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
        internalInboundCoreAccounts <- Full(inboundGetCoreBankAccounts.data)
      } yield {
        (internalInboundCoreAccounts,callContext)
      }
      logger.debug(s"Kafka getCoreBankAccounts says res is $box")
      box
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(accountTTL second){
        val box= callAdapter(callContext)

        box match {
          case Full((list, callContext)) if (list.head.errorCode=="") =>
            Full(list.map( x => CoreAccount(x.id,x.label,x.bankId,x.accountType, x.accountRoutings)), callContext)
          case Full((f,_)) if (f.head.errorCode!="") =>
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

  override def getCoreBankAccountsFuture(BankIdAccountIds: List[BankIdAccountId], @CacheKeyOmit callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext]) = {
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
              case e: Exception => throw new MappingException(s"$InboundGetCoreBankAccounts extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
            }
        } map {
          (_.data)
        }
      } yield {
        (res, callContext)
      }
      logger.debug(s"Kafka getCoreBankAccountsFuture says res is $future")
      future
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(accountsTTL second){

        val future = callAdapter(callContext)

        future map {
          case (list, callContext) if (list.head.errorCode=="") =>
            Full(list.map( x => CoreAccount(x.id,x.label,x.bankId,x.accountType, x.accountRoutings)), callContext)
          case (list,_) if (list.head.errorCode!="") =>
            Failure("INTERNAL-"+ list.head.errorCode+". + CoreBank-Status:"+ list.head.backendMessages)
          case (List(),_) =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
      }
    }
  }("getCoreBankAccountsFuture")
  
  messageDocs += MessageDoc(
    process = "obp.get.Transactions",
    messageFormat = messageFormat,
    description = "getTransactions from kafka",
    exampleOutboundMessage = (
      OutboundGetTransactions(
        authInfo = authInfoExample,
        bankId = "bankId",
        accountId = "accountId",
        limit =100,
        fromDate="DateWithSecondsExampleObject",
        toDate="DateWithSecondsExampleObject"
      )
    ),
    exampleInboundMessage = (
      InboundGetTransactions(
        authInfoExample,
        statusExample,
        InternalTransaction_vJune2017(
          transactionId = "String",
          accountId = "String",
          amount = "String",
          bankId = "String",
          completedDate = "String", 
          counterpartyId = "String", 
          counterpartyName = "String", 
          currency = "String", 
          description = "String", 
          newBalanceAmount = "String",
          newBalanceCurrency = "String", 
          postedDate = "String", 
          `type` = "String", 
          userId = "String")::Nil))
  )
  // TODO Get rid on these param lookups and document.
  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = saveConnectorMetric {
    val limit = queryParams.collect { case OBPLimit(value) => value }.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => date.toString }.headOption.getOrElse(APIUtil.DefaultFromDate.toString)
    val toDate = queryParams.collect { case OBPToDate(date) => date.toString }.headOption.getOrElse(APIUtil.DefaultToDate.toString)

    

    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCached to cache data.
    def getTransactionsCached(bankId: BankId, accountId: AccountId, limit: Int, fromDate: String, toDate: String, callContext: Option[CallContext]): Box[(List[Transaction], Option[CallContext])] = {
      
      val req = OutboundGetTransactions(
        authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
        bankId = bankId.toString,
        accountId = accountId.value,
        limit = limit,
        fromDate = fromDate,
        toDate = toDate
      )
      
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        def callAdapter(callContext: Option[CallContext]) = {
          val box = for {
            kafkaMessage <- processToBox[OutboundGetTransactions](req)
            inboundGetTransactions <- tryo {
              kafkaMessage.extract[InboundGetTransactions]
            } ?~! s"$InvalidConnectorResponseForGetTransactions $InboundGetTransactions extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
            (internalTransactions, status) <- Full(inboundGetTransactions.data, inboundGetTransactions.status)
          } yield {
            (internalTransactions, status, callContext)
          }
          logger.debug(s"Kafka getTransactions says: res is: $box")
          box
        }

        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionsTTL second) {
          logger.debug(s"Kafka getTransactions says: req is: $req")
          val box = callAdapter(callContext)

          box match {
            case Full((data, status, _)) if (status.errorCode != "") =>
              Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages)
            case Full((data, status,_)) if (!data.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)) =>
              Failure(InvalidConnectorResponseForGetTransactions)
            case Full((data, status,callContext)) if (status.errorCode == "") =>
              val bankAccountAndcallContext1 = checkBankAccountExists(BankId(data.head.bankId), AccountId(data.head.accountId), callContext)

              val res = for {
                internalTransaction <- data
                thisBankAccount <- bankAccountAndcallContext1.map(_._1) ?~! ErrorMessages.BankAccountNotFound
                transaction <- createInMemoryTransaction(thisBankAccount, internalTransaction)
              } yield {
                transaction
              }
              Full((res, bankAccountAndcallContext1.map(_._2).openOrThrowException(attemptedToOpenAnEmptyBox)))
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
    getTransactionsCached(bankId: BankId, accountId: AccountId, limit: Int, fromDate: String, toDate: String, callContext: Option[CallContext])

  }("getTransactions")
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = saveConnectorMetric{
    val limit = queryParams.collect { case OBPLimit(value) => value}.headOption.getOrElse(100)
    val fromDate = queryParams.collect { case OBPFromDate(date) => date.toString}.headOption.getOrElse(APIUtil.DefaultFromDate.toString)
    val toDate = queryParams.collect { case OBPToDate(date) => date.toString}.headOption.getOrElse(APIUtil.DefaultToDate.toString)
  
    
    
    //Note: because there is `queryParams: OBPQueryParam*` in getTransactions, so create the getTransactionsCoreCached to cache data.
    //Note: getTransactionsCoreCached and getTransactionsCached have the same parameters,but the different method name.
    //TODO, here the cache need to be fixed, no sense use callContext as the cache key here.
    def getTransactionsCoreCached(bankId: BankId, accountId: AccountId, limit: Int,fromDate :String, toDate: String,  callContext: Option[CallContext]): Box[(List[TransactionCore], Option[CallContext])] = {
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        def callAdapter(callContext: Option[CallContext]) = {
          val req = OutboundGetTransactions(
            authInfo = getAuthInfo(callContext).openOrThrowException(NoCallContext),
            bankId = bankId.toString,
            accountId = accountId.value,
            limit = limit,
            fromDate = fromDate,
            toDate = toDate
          )

          logger.debug(s"Kafka getTransactions says: req is: $req")
          val box = for {
            kafkaMessage <- processToBox[OutboundGetTransactions](req)
            inboundGetTransactions <- tryo {
              kafkaMessage.extract[InboundGetTransactions]
            } ?~! s"$InvalidConnectorResponseForGetTransactions $InboundGetTransactions extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
            (internalTransactions, status) <- Full(inboundGetTransactions.data, inboundGetTransactions.status)
          } yield {
            (internalTransactions, status, callContext)
          }
          logger.debug(s"Kafka getTransactions says: res is: $box")
          box
        }

        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionsTTL second) {
          val box = callAdapter(callContext)

          box match {
            case Full((data, status,callContext)) if (status.errorCode != "") =>
              Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages)
            case Full((data, status,callContext)) if (!data.forall(x => x.accountId == accountId.value && x.bankId == bankId.value)) =>
              Failure(InvalidConnectorResponseForGetTransactions)
            case Full((data, status,callContext)) if (status.errorCode == "") =>
              val bankAccountAndCallContext = checkBankAccountExists(BankId(data.head.bankId), AccountId(data.head.accountId), callContext)

              val res = for {
                internalTransaction <- data
                thisBankAccount <- bankAccountAndCallContext.map(_._1) ?~! ErrorMessages.BankAccountNotFound
                transaction <- createInMemoryTransactionCore(thisBankAccount, internalTransaction)
              } yield {
                transaction
              }
              Full(res, bankAccountAndCallContext.map(_._2).openOrThrowException(s"$attemptedToOpenAnEmptyBox. getTransactionsCoreCached.bankAccountAndCallContext.map(_._2)."))
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
    getTransactionsCoreCached(bankId: BankId, accountId: AccountId, limit: Int,fromDate :String, toDate: String,  callContext: Option[CallContext])
    
  }("getTransactions")
  
  messageDocs += MessageDoc(
    process = "obp.get.Transaction",
    messageFormat = messageFormat,
    description = "getTransaction from kafka ",
    exampleOutboundMessage = (
      OutboundGetTransaction(
        authInfoExample,
        "bankId",
        "accountId",
        "transactionId"
      )
    ),
    exampleInboundMessage = (
      InboundGetTransaction(authInfoExample, statusExample, Some(InternalTransaction_vJune2017(
                transactionId = "String",
                accountId = "String",
                amount = "String",
                bankId = "String",
                completedDate = "String",
                counterpartyId = "String",
                counterpartyName = "String",
                currency = "String",
                description = "String",
                newBalanceAmount = "String",
                newBalanceCurrency = "String",
                postedDate = "String",
                `type` = "String",
                userId = "String"
              )))
    )
  )
  override def getTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId, callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext]) = {
      val box = for {
        authInfo <- getAuthInfo(callContext)
        req = OutboundGetTransaction(authInfo, bankId.value, accountId.value, transactionId.value)
        _ <- Full(logger.debug(s"Kafka getTransaction Req says:  is: $req"))
        kafkaMessage <- processToBox[OutboundGetTransaction](req)
        inboundGetTransaction <- tryo {
          kafkaMessage.extract[InboundGetTransaction]
        } ?~! s"$InvalidConnectorResponseForGetTransaction $InboundGetTransaction extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
        (internalTransaction, status) <- Full(inboundGetTransaction.data, inboundGetTransaction.status)
      } yield {
        (internalTransaction, status, callContext)
      }
      logger.debug(s"Kafka getTransaction Res says: is: $box")
      box
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionTTL second){
        val box = callAdapter(callContext)

        box match {
          // Check does the response data match the requested data
          case Full((Some(data), status, callContext)) if (transactionId.value != data.transactionId) =>
            Failure(s"$InvalidConnectorResponseForGetTransaction")
          case Full((data,status, callContext)) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages)
          case Full((Some(data), status, callContext)) if (transactionId.value == data.transactionId && status.errorCode=="") =>
            for {
              (bankAccount, callContext) <- checkBankAccountExists(BankId(data.bankId), AccountId(data.accountId),callContext) ?~! ErrorMessages.BankAccountNotFound
              transaction: Transaction <- createInMemoryTransaction(bankAccount,data)
            } yield {
              (transaction,callContext)
            }
          case Full((data,status,callContext)) if (status.errorCode!="") =>
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
    description = "CreateChallenge from kafka ",
    exampleOutboundMessage = (
      OutboundChallengeBase(
        action = "obp.create.Challenge",
        messageFormat = messageFormat,
        bankId = "gh.29.uk",
        accountId = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
        userId = "c7b6cb47-cb96-4441-8801-35b57456753a",
        username = "susan.uk.29@example.com",
        transactionRequestType = "SANDBOX_TAN",
        transactionRequestId = "1234567"
      )
    ),
    exampleInboundMessage = (
      InboundCreateChallengeJune2017(
        authInfoExample,
        InternalCreateChallengeJune2017(
          errorCodeExample,
          inboundStatusMessagesExample,
          "1234"
        )
      )
    ),
    outboundAvroSchema = Some(parse(SchemaFor[OutboundCreateChallengeJune2017]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundCreateChallengeJune2017]().toString(true)))
  )
  override def createChallenge(bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String, callContext: Option[CallContext]) = {
    
    val authInfo = getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox)
    val req = OutboundCreateChallengeJune2017(
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
     res <- processToFuture[OutboundCreateChallengeJune2017](req) map {
       f =>
         try {
           f.extract[InboundCreateChallengeJune2017]
         } catch {
           case e: Exception => throw new MappingException(s"$InboundCreateChallengeJune2017 extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
         }
       } map { x => (x.authInfo, x.data) }
    } yield {
     Full(res)
    }
    
    val res = future map {
      case Full((authInfo,x)) if (x.errorCode=="")  =>
        val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
        (Full(x.answer), callContextUpdated)
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
    description = "createCounterparty from kafka ",
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
          bespoke = CounterpartyBespoke("key","value") ::Nil
        )
      )
    ),
    exampleInboundMessage = (
      InboundCreateCounterparty(
        authInfoExample, 
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
    )
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
    callContext: Option[CallContext]) = {
  
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
      (authInfo, internalCounterparty, status) <- Full(inboundCreateCounterparty.authInfo, inboundCreateCounterparty.data, inboundCreateCounterparty.status)
    } yield{
      (authInfo, internalCounterparty, status)
    }
    logger.debug(s"Kafka createCounterparty Res says: is: $box")
    
    val res = box match {
      case Full((authInfo, Some(data), status)) if (status.errorCode=="")  =>
        val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
        Full(data, callContextUpdated)
      case Full((authInfo, data, status)) if (status.errorCode!="") =>
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
    description = "getTransactionRequests210 from kafka ",
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
        authInfoExample, 
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
            AmountOfMoney("ILS", "0")
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
    )
    
  )
  override def getTransactionRequests210(user : User, fromAccount : BankAccount, callContext: Option[CallContext] = None)  = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext]) = {
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
            accountRoutingAddress = fromAccount.accountRoutingAddress)
        )
        _ <- Full(logger.debug(s"Kafka getTransactionRequests210 Req says: is: $req"))
        kafkaMessage <- processToBox[OutboundGetTransactionRequests210](req)
        inboundGetTransactionRequests210 <- tryo {
          kafkaMessage.extract[InboundGetTransactionRequests210]
        } ?~! s"$InvalidConnectorResponseForGetTransactionRequests210, $InboundGetTransactionRequests210 extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
        (internalGetTransactionRequests, status) <- Full(inboundGetTransactionRequests210.data, inboundGetTransactionRequests210.status)
      } yield {
        (internalGetTransactionRequests, status, callContext)
      }
      logger.debug(s"Kafka getTransactionRequests210 Res says: is: $box")
      box
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(transactionRequests210TTL second){

        val box = callAdapter(callContext)

        val res = box match {
          case Full((data, status, callContext)) if (status.errorCode=="")  =>
            //For consistency with sandbox mode, we need combine obp transactions in database and adapter transactions
            val transactionRequests = for{
              adapterTransactionRequests <- Full(data)
              //TODO, this will cause performance issue, we need limit the number of transaction requests.
              obpTransactionRequests <- LocalMappedConnector.getTransactionRequestsImpl210(fromAccount) ?~! s"$ConnectorEmptyResponse, error on LocalMappedConnector.getTransactionRequestsImpl210"
            } yield {
              adapterTransactionRequests ::: obpTransactionRequests
            }
            transactionRequests.map(transactionRequests =>(transactionRequests, callContext))
          case Full((data, status, _)) if (status.errorCode!="") =>
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
    description = "getCounterparties from kafka ",
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
      InboundGetCounterparties(authInfoExample, statusExample,
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
  )
  )

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId,viewId :ViewId, callContext: Option[CallContext] ) = saveConnectorMetric{
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
          inboundGetCounterparties <- tryo{kafkaMessage.extract[InboundGetCounterparties]} ?~! s"$InboundGetCounterparties extract error. Both check API and Adapter Inbound Case Classes need be the same ! "
          (authInfo, internalCounterparties, status) <- Full(inboundGetCounterparties.authInfo, inboundGetCounterparties.data, inboundGetCounterparties.status)
        } yield{
          (authInfo, internalCounterparties, status)
        }
        logger.debug(s"Kafka getCounterparties Res says: is: $box")

        val res = box match {
          case Full((authInfo, data, status)) if (status.errorCode=="")  =>
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            Full((data, callContextUpdated))
          case Full((authInfo, data, status)) if (status.errorCode!="") =>
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
    description = "getCounterpartyByCounterpartyId from kafka ",
    exampleOutboundMessage = (
      OutboundGetCounterpartyByCounterpartyId(
        authInfoExample,
        OutboundGetCounterpartyById(
          counterpartyId = "String"
        )
      )
    ),
    exampleInboundMessage = (
      InboundGetCounterparty(authInfoExample, statusExample, Some(InternalCounterparty(createdByUserId = "String", name = "String", thisBankId = "String", thisAccountId = "String", thisViewId = "String", counterpartyId = "String", otherAccountRoutingScheme = "String", otherAccountRoutingAddress = "String", otherBankRoutingScheme = "String", otherBankRoutingAddress = "String", otherBranchRoutingScheme = "String", otherBranchRoutingAddress = "String", isBeneficiary = true, description = "String", otherAccountSecondaryRoutingScheme = "String", otherAccountSecondaryRoutingAddress = "String", bespoke = Nil)))
    )
  )
  override def getCounterpartyByCounterpartyIdFuture(counterpartyId: CounterpartyId, callContext: Option[CallContext]) = saveConnectorMetric{
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
               case e: Exception => throw new MappingException(s"$InboundGetCounterparty extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
             }
         } map { x => (x.authInfo, x.data, x.status) }
       } yield {
         Full(res)
       }
       logger.debug(s"Kafka getCounterpartyByCounterpartyId Res says: is: $future")

        val res = future map {
          case Full((authInfo, Some(data), status)) if (status.errorCode == "") =>
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            (Full(data), callContextUpdated)
          case Full((authInfo, data, status)) if (status.errorCode != "") =>
            (Failure("INTERNAL-" + status.errorCode + ". + CoreBank-Status:" + status.backendMessages), callContext)
          case _ =>
            (Failure(ErrorMessages.UnknownError), callContext)
        }
        res
      }
    }
  }("getCounterpartyByCounterpartyId")


  override def getCounterpartyTrait(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String, callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(counterpartyTrait second){
         
        val req = OutboundGetCounterparty(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), thisBankId.value, thisAccountId.value, couterpartyId)
        logger.debug(s"Kafka getCounterpartyTrait Req says: is: $req")

        val future = for {
         res <- processToFuture[OutboundGetCounterparty](req) map {
           f =>
             try {
               f.extract[InboundGetCounterparty]
             } catch {
               case e: Exception => throw new MappingException(s"$InboundGetCounterparty extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
             }
         } map { x => (x.authInfo, x.data, x.status) }
       } yield {
         Full(res)
       }
       logger.debug(s"Kafka getCounterpartyTrait Res says: is: $future")
        
        val res = future map {
          case Full((authInfo, Some(data), status)) if (status.errorCode=="")  =>
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            (Full(data), callContextUpdated)
          case Full((authInfo, data, status)) if (status.errorCode!="") =>
            (Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:"+ status.backendMessages), callContext)
          case _ =>
            (Failure(ErrorMessages.UnknownError), callContext)
        }
        res
      }
    }
  }("getCounterpartyTrait")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.CustomersByUserIdBox",
    messageFormat = messageFormat,
    description = "getCustomersByUserIdBox from kafka ",
    exampleOutboundMessage = (
      OutboundGetCustomersByUserId(
        authInfoExample
      )
    ),
    exampleInboundMessage = (
      InboundGetCustomersByUserId(
        authInfoExample,
        statusExample,
        InternalCustomer(
          customerId = "String", bankId = "String", number = "String",
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
    inboundAvroSchema = None
  )

  override def getCustomersByUserIdFuture(userId: String , @CacheKeyOmit callContext: Option[CallContext]) = saveConnectorMetric{
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)

    def callAdapter(callContext: Option[CallContext]) = {
      val req = OutboundGetCustomersByUserId(getAuthInfo(callContext).openOrThrowException(NoCallContext))
      logger.debug(s"Kafka getCustomersByUserIdFuture Req says: is: $req")

      val future = for {
        res <- processToFuture[OutboundGetCustomersByUserId](req) map {
          f =>
            try {
              f.extract[InboundGetCustomersByUserId]
            } catch {
              case e: Exception => throw new MappingException(s"$InboundGetCustomersByUserId extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
            }
        } map { x => (x.data, x.status, callContext) }
      } yield {
        res
      }
      logger.debug(s"Kafka getCustomersByUserIdFuture Res says: is: $future")
      future
    }

    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(customersByUserIdBoxTTL second) {

        val future: Future[(List[InternalCustomer], Status, Option[CallContext])] = callAdapter(callContext)

        val res = future map {
          case (list, status, callerContext1) if (status.errorCode=="") =>
            Full(JsonFactory_vJune2017.createObpCustomers(list), callerContext1)
          case (_, status, _) if (status.errorCode!="") =>
            Failure("INTERNAL-"+ status.errorCode+". + CoreBank-Status:" + status.backendMessages)
          case (List(),status, _) =>
            Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
          case _ =>
            Failure(ErrorMessages.UnknownError)
        }
        res
      }
    }
  }("getCustomersByUserIdFuture")
  
  
  messageDocs += MessageDoc(
    process = "obp.get.getStatusOfCheckbookOrdersFuture",
    messageFormat = messageFormat,
    description = "getStatusOfCheckbookOrdersFuture from kafka ",
    exampleOutboundMessage = (
      OutboundGetCheckbookOrderStatus(
        authInfoExample,
        bankId = "bankId", 
        accountId ="accountId", 
        originatorApplication ="String", 
        originatorStationIP = "String", 
        primaryAccount =""//TODO not sure for now.
      )
    ),
    exampleInboundMessage = (
      InboundGetChecksOrderStatus(
        authInfoExample,
        statusExample,
        SwaggerDefinitionsJSON.checkbookOrdersJson
      )
    )
  )

  override def getCheckbookOrdersFuture(
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
                case e: Exception => throw new MappingException(s"correlationId(${req.authInfo.correlationId}): $InboundGetChecksOrderStatus extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
              }
          } map {x => (x.authInfo, x.data, x.status)}
        } yield{
          res
        }
        
        val res = future map {
          case (authInfo, checksOrderStatusResponseDetails, status) if (status.errorCode=="") =>
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCheckbookOrdersFuture Res says: is: $checksOrderStatusResponseDetails")
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            Full((checksOrderStatusResponseDetails, callContextUpdated))
          case (authInfo, accountDetails, status) if (status.errorCode!="") =>
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
    process = "obp.get.getStatusOfCreditCardOrderFuture",
    messageFormat = messageFormat,
    description = "getStatusOfCreditCardOrderFuture from kafka ",
    exampleOutboundMessage = (
      OutboundGetCreditCardOrderStatus(
        authInfoExample,
        bankId = "bankId", 
        accountId ="accountId", 
        originatorApplication = "String", 
        originatorStationIP = "String", 
        primaryAccount = ""
      )
    ),
    exampleInboundMessage = (
      InboundGetCreditCardOrderStatus(
        authInfoExample,
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
    ))
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
                case e: Exception => throw new MappingException(s"correlationId(${req.authInfo.correlationId}): $InboundCardDetails extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
              }
          } map {x => (x.authInfo, x.data, x.status)}
        } yield{
          res
        }
        
        val res = future map {
          case (authInfo, checksOrderStatusResponseDetails, status) if (status.errorCode=="") =>
            logger.debug(s"correlationId(${req.authInfo.correlationId}): Kafka getStatusOfCreditCardOrderFuture Res says: is: $checksOrderStatusResponseDetails")
            val callContextUpdated = ApiSession.updateSessionId(callContext, authInfo.sessionId)
            Full(checksOrderStatusResponseDetails.map(
              card =>CardObjectJson(
                card_type= card.creditCardType,
                card_description = card.cardDescription,
                use_type= card.creditCardType
              )), callContextUpdated)
          case (authInfo, accountDetails, status) if (status.errorCode!="") =>
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
  def createInMemoryTransaction(bankAccount: BankAccount,internalTransaction: InternalTransaction_vJune2017): Box[Transaction] = {
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

  def createInMemoryTransactionCore(bankAccount: BankAccount,internalTransaction: InternalTransaction_vJune2017): Box[TransactionCore] = {
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
    description = "getBranches",
    exampleOutboundMessage = (
      OutboundGetBranches(authInfoExample,"bankid")
    ),
    exampleInboundMessage = (
      InboundGetBranches(
        authInfoExample,
        Status("",
        inboundStatusMessagesExample),
        InboundBranchVJune2017(
          branchId = BranchId(""),
          bankId = BankId(""),
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

    )
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
        val req = OutboundGetBranches(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), bankId.toString)
        logger.debug(s"Kafka getBranchesFuture Req is: $req")

        val future: Future[(List[InboundBranchVJune2017], Status)] = for {
          res <- processToFuture[OutboundGetBranches](req) map {
            f =>
              try {
                f.extract[InboundGetBranches]
              } catch {
                case e: Exception => throw new MappingException(s"$InboundGetBranches extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
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
    description = "getBranch",
    exampleOutboundMessage = (
      OutboundGetBranch(authInfoExample,"bankid", "branchid")
    ),
    exampleInboundMessage = (
      InboundGetBranch(
        authInfoExample,
        Status("",
          inboundStatusMessagesExample),
        Some(InboundBranchVJune2017(
          branchId = BranchId(""),
          bankId = BankId(""),
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

    )
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
        val req = OutboundGetBranch(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), bankId.toString, branchId.toString)
        logger.debug(s"Kafka getBranchFuture Req is: $req")

        val future: Future[(Option[InboundBranchVJune2017], Status)] = for {
          res <- processToFuture[OutboundGetBranch](req) map {
            f =>
              try {
                f.extract[InboundGetBranch]
              } catch {
                case e: Exception => throw new MappingException(s"$InboundGetBranch extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
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
    description = "getAtms",
    exampleOutboundMessage = (
      OutboundGetAtms(authInfoExample,"bankid")
    ),
    exampleInboundMessage = (
      InboundGetAtms(
        authInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        InboundAtmJune2017(
          atmId = AtmId("333"),
          bankId = BankId("10"),
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

    )
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
        val req = OutboundGetAtms(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), bankId.value)
        logger.debug(s"Kafka getAtmsFuture Req is: $req")

        val future = for {
          res <- processToFuture[OutboundGetAtms](req) map {
            f =>
              try {
                f.extract[InboundGetAtms]
              } catch {
                case e: Exception => throw new MappingException(s"$InboundGetAtms extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
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
    description = "getAtm",
    exampleOutboundMessage = (
      OutboundGetAtm(authInfoExample,"bankId", "atmId")
    ),
    exampleInboundMessage = (
      InboundGetAtm(
        authInfoExample,
        Status(errorCodeExample, inboundStatusMessagesExample),
        Some(InboundAtmJune2017(
          atmId = AtmId("333"),
          bankId = BankId("10"),
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

    )
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
        val req = OutboundGetAtm(getAuthInfo(callContext).openOrThrowException(attemptedToOpenAnEmptyBox), bankId.value, atmId.value)
        logger.debug(s"Kafka getAtmFuture Req is: $req")

        val future: Future[(Option[InboundAtmJune2017], Status)] = for {
          res <- processToFuture[OutboundGetAtm](req) map {
            f =>
              try {
                f.extract[InboundGetAtm]
              } catch {
                case e: Exception => throw new MappingException(s"$InboundGetAtm extract error. Both check API and Adapter Inbound Case Classes need be the same ! ", e)
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


}


object KafkaMappedConnector_vJune2017 extends KafkaMappedConnector_vJune2017{
  
}