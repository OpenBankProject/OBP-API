package code.bankconnectors

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
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID.randomUUID

import code.accountholders.AccountHolders
import code.api.cache.Caching
import code.api.util.APIUtil.saveConnectorMetric
import code.api.util.ErrorMessages._
import code.api.util._
import code.atms.{Atms, MappedAtm}
import code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017
import code.branches.Branches.Branch
import code.fx.FXRate
import code.kafka.KafkaHelper
import code.management.ImporterAPI.ImporterTransaction
import code.metadata.comments.Comments
import code.metadata.narrative.MappedNarrative
import code.metadata.tags.Tags
import code.metadata.transactionimages.TransactionImages
import code.metadata.wheretags.WhereTags
import code.model._
import code.model.dataAccess._
import com.openbankproject.commons.model.Product
import code.transaction.MappedTransaction
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.transactionrequests.TransactionRequests._
import code.transactionrequests.{MappedTransactionRequestTypeCharge, TransactionRequestTypeCharge, TransactionRequestTypeChargeMock, TransactionRequests}
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.model.{CounterpartyTrait, _}
import com.tesobe.CacheKeyFromArguments
import com.tesobe.obp.transport.Pager
import com.tesobe.obp.transport.spi.{DefaultPager, DefaultSorter, TimestampFilter}
import net.liftweb.common._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.MappingException
import net.liftweb.mapper._
import net.liftweb.util.Helpers._

import scala.collection.immutable.{List, Seq}
import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.language.postfixOps

object KafkaMappedConnector_JVMcompatible extends Connector with KafkaHelper with MdcLoggable {

  implicit override val nameOfConnector = KafkaMappedConnector_JVMcompatible.getClass.getSimpleName
  
  // Maybe we should read the date format from props?
  val DATE_FORMAT = APIUtil.DateWithMs

  val getBankTTL                            = APIUtil.getPropsValue("connector.cache.ttl.seconds.getBank", "0").toInt * 1000 // Miliseconds
  val getBanksTTL                           = APIUtil.getPropsValue("connector.cache.ttl.seconds.getBanks", "0").toInt * 1000 // Miliseconds
  val getUserTTL                            = APIUtil.getPropsValue("connector.cache.ttl.seconds.getUser", "0").toInt * 1000 // Miliseconds
  val updateUserAccountViewsTTL             = APIUtil.getPropsValue("connector.cache.ttl.seconds.updateUserAccountViews", "0").toInt * 1000 // Miliseconds
  val getAccountTTL                         = APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccount", "0").toInt * 1000 // Miliseconds
  val getAccountHolderTTL                   = APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccountHolderTTL", "0").toInt * 1000 // Miliseconds
  val getAccountsTTL                        = APIUtil.getPropsValue("connector.cache.ttl.seconds.getAccounts", "0").toInt * 1000 // Miliseconds
  val getTransactionTTL                     = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransaction", "0").toInt * 1000 // Miliseconds
  val getTransactionsTTL                    = APIUtil.getPropsValue("connector.cache.ttl.seconds.getTransactions", "0").toInt * 1000 // Miliseconds
  val getCounterpartyFromTransactionTTL     = APIUtil.getPropsValue("connector.cache.ttl.seconds.getCounterpartyFromTransaction", "0").toInt * 1000 // Miliseconds
  val getCounterpartiesFromTransactionTTL   = APIUtil.getPropsValue("connector.cache.ttl.seconds.getCounterpartiesFromTransaction", "0").toInt * 1000 // Miliseconds
  
  val primaryUserIdentifier = AuthUser.getCurrentUserUsername


  // "Versioning" of the messages sent by this or similar connector might work like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Probably should be in a separate file e.g. Nov2016_messages.scala
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  
  val formatVersion: String = "Nov2016"

  //This is a temporary way to mapping the adapter(Java) side, we maybe used Adapter(Scala) later.
  // Because of the Java Adapter has the fixed format, we need map our input vaule to it.
  def anyToMap[A: scala.reflect.runtime.universe.TypeTag](a: A): Map[String, String] = {
    import scala.reflect.runtime.universe._
    
    val mirror = runtimeMirror(a.getClass.getClassLoader)
    
    def a2m(x: Any, t: Type): Any = {
      val xm = mirror reflect x
      
      val members =
        t.declarations.collect {
          case acc: MethodSymbol if acc.isCaseAccessor =>
            acc.name.decoded -> a2m(
              (xm reflectMethod acc) (),
              acc.typeSignature
            )
        }.toMap.asInstanceOf[Map[String, String]]
      
      if (members.isEmpty) x else members
    }
    
    a2m(a, typeOf[A]).asInstanceOf[Map[String, String]]
  }
  
  // For this method, can only check the JValue --> correct format. We need handle the kafka or Future exception.
  // So I try the error handling for each method.
  def tryExtract[T](in: JValue)(implicit ev: Manifest[T]): Box[T] = {
    try {
      Full(in.extract[T])
    } catch {
      case m: MappingException => Empty
    }
  }

  def getAccountHolderCached(bankId: BankId, accountId: AccountId) : String = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getAccountHolderTTL millisecond) {
        val accountHolderList = AccountHolders.accountHolders.vend.getAccountHolders(bankId, accountId).toList

        val accountHolder = accountHolderList.length match {
          case 0 => throw new RuntimeException(NoExistingAccountHolders + "BankId= " + bankId + " and AcoountId = "+ accountId )
          case _ => accountHolderList.toList(0).name
        }
        accountHolder
      }
    }
  }("getAccountHolder")
  
  // TODO Create and use a case class for each Map so we can document each structure.

  //gets banks handled by this connector
  override def getBanks(callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getBanksTTL millisecond) {
        val req = Map(
          "version" -> formatVersion,
          "name" -> "get",
          "target" -> "banks"
        )

        logger.debug(s"Kafka getBanks says: req is: $req")
        try {
          val rList = process(req).extract[List[KafkaInboundBank]]

          logger.debug(s"Kafka getBanks says rList is $rList")

          // Loop through list of responses and create entry for each
          Full(for (r <- rList)
            yield {
              KafkaBank(r)
            }
            ,callContext
          )
        } catch {
          case m: MappingException =>
            logger.error("getBanks-MappingException",m)
            Failure(AdapterOrCoreBankingSystemException)
          case m: TimeoutException =>
            logger.error("getBanks-TimeoutException",m)
            Failure(FutureTimeoutException)
          case m: ClassCastException =>
            logger.error("getBanks-ClassCastException",m)
            Failure(KafkaMessageClassCastException)
          case m: Throwable =>
            logger.error("getBanks-Unexpected",m)
            Failure(UnknownError)
        }
      }
    }
  }("getBanks")

  // Gets bank identified by bankId
  override def getBank(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getBankTTL millisecond){
        // Create argument list
        val req = Map(
          "version" -> formatVersion,
          "name" -> "get",
          "target" -> "bank",
          "bankId" -> bankId.value
        )
        try {
          val r = process(req).extract[KafkaInboundBank]
          // Return result
          Full(new KafkaBank(r), callContext)
        } catch {
          case m: MappingException =>
            logger.error("getBank-MappingException",m)
            Failure(AdapterOrCoreBankingSystemException)
          case m: TimeoutException =>
            logger.error("getBank-TimeoutException",m)
            Failure(FutureTimeoutException)
          case m: ClassCastException =>
            logger.error("getBank-ClassCastException",m)
            Failure(KafkaMessageClassCastException)
          case m: Throwable =>
            logger.error("getBank-Unexpected",m)
            Failure(UnknownError)
        }
      }
    }
  }("getBank")

  //TODO this is not implement in adapter
  override def getUser( username: String, password: String ): Box[InboundUser] = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(getUserTTL millisecond) {
        try {
          for {
            req <- Full {
              Map[String, String](
                "version" -> formatVersion,
                "name" -> "get",
                "target" -> "user",
                "username" -> username,
                "password" -> password
              )
            }
            u <- tryExtract[KafkaInboundValidatedUser](process(req))
            recUsername <- tryo { u.displayName }
          } yield {
            if (username == u.displayName) new InboundUser(recUsername, password,
              recUsername
            )
            else null
          }
        } catch {
          case m: MappingException =>
            logger.error("getUser-MappingException",m)
            Failure(AdapterOrCoreBankingSystemException)
          case m: TimeoutException =>
            logger.error("getUser-TimeoutException",m)
            Failure(FutureTimeoutException)
          case m: ClassCastException =>
            logger.error("getUser-ClassCastException",m)
            Failure(KafkaMessageClassCastException)
          case m: Throwable =>
            logger.error("getBank-Unexpected",m)
            Failure(UnknownError)
        }
      }
    }
  }("getUser")

  override def updateUserAccountViewsOld( user: ResourceUser ) = saveConnectorMetric {
    /**
      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(updateUserAccountViewsTTL millisecond){
        //1 getAccounts from Kafka
        val accounts: List[KafkaInboundAccount] = getBanks(None).map(_._1).getOrElse(List.empty).flatMap { bank => {
          val bankId = bank.bankId.value
          val username = user.name
          logger.debug(s"JVMCompatible updateUserAccountViews for user.email ${user.email} user.name ${user.name} at bank ${bankId}")
          for {
            req <- tryo { Map[String, String](
              "version" -> formatVersion,
              "name" -> "get",
              "target" -> "accounts",
              "userId" -> username,
              "bankId" -> bankId)}
          } yield {
            val res = tryExtract[List[KafkaInboundAccount]](process(req)) match {
              case Full(a) => a
              case _ => List.empty
            }
            logger.debug(s"JVMCompatible updateUserAccountViews got response ${res}")
            res
          }
        }
        }.flatten

        logger.debug(s"JVMCompatible getAccounts says res is $accounts")

        //2 CreatViews for each account
        for {
          acc <- accounts
          username <- tryo {user.name}
          createdNewViewsForTheUser <- tryo {createViews( BankId(acc.bankId),
            AccountId(acc.accountId),
            true,
            true,
            true,
            true
          )}
          //3 get all the existing views.
          existingViewsNotBelongtoTheUser <- tryo {
            Views.views.vend.viewsForAccount(BankIdAccountId(BankId(acc.bankId), AccountId(acc.accountId)))
              .filterNot(_.users.contains(user.userPrimaryKey))
          }
        } yield {
          //4 set Account link to User
          setAccountHolder(username, BankId(acc.bankId), AccountId(acc.accountId), username::Nil)
          createdNewViewsForTheUser.foreach(v => {
            Views.views.vend.addPermission(v.uid, user)
            logger.debug(s"------------> updated view ${v.uid} for resourceuser ${user} and account ${acc}")
          })
          existingViewsNotBelongtoTheUser.foreach (v => {
            Views.views.vend.addPermission(v.uid, user)
            logger.debug(s"------------> added resourceuser ${user} to view ${v.uid} for account ${acc}")
          })
        }
      }
    }
  }("updateUserAccountViews")

  // Gets transaction identified by bankid, accountid and transactionId
  override def getTransaction(
                               bankId: BankId,
                               accountId: AccountId,
                               transactionId: TransactionId,
                               callContext: Option[CallContext] = None) =
    saveConnectorMetric {
      /**
        * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(getTransactionTTL millisecond)  {
          try {
            val req = Map(
              "version" -> formatVersion,
              "name" -> "get",
              "target" -> "transaction",
              "accountId" -> accountId.toString,
              "bankId" -> bankId.toString,
              "transactionId" -> transactionId.toString,
              "userId" -> AuthUser.getCurrentUserUsername
            )
            // Since result is single account, we need only first list entry
            val r = process(req).extractOpt[KafkaInboundTransaction]
            r match {
              // Check does the response data match the requested data
              case Some(x) if transactionId.value != x.transactionId => Failure(ErrorMessages.InvalidConnectorResponseForGetTransaction, Empty, Empty)
              case Some(x) if transactionId.value == x.transactionId => createNewTransaction(x).map(transaction =>(transaction, callContext))
              case _ => Failure(ErrorMessages.ConnectorEmptyResponse, Empty, Empty)
            }
          } catch {
            case m: MappingException =>
              logger.error("getTransaction-MappingException",m)
              Failure(AdapterOrCoreBankingSystemException)
            case m: TimeoutException =>
              logger.error("getTransaction-TimeoutException",m)
              Failure(FutureTimeoutException)
            case m: ClassCastException =>
              logger.error("getTransaction-ClassCastException",m)
              Failure(KafkaMessageClassCastException)
            case m: Throwable =>
              logger.error("getTransaction-Unexpected",m)
              Failure(UnknownError)
          }
        }
      }
    }("getTransaction")
  
  case class OutboundTransactionsQuery(
    version: String,
    name: String,
    target: String,
    accountId: String,
    bankId: String,
    userId: String,
    filter: SountFilter = SountFilter(),
    sort: SountSort = SountSort()
  )
  
  case class SountFilter(
    high: String = "2020-01-01T00:00:00.000Z",
    low: String = "1999-01-01T00:00:00.000Z",
    name: String = "postedDate",
    `type`: String = "timestamp"
  )
  
  case class SountSort(
    completedDate: String = "ascending"
  )
  
  override def getTransactions(
                                bankId: BankId,
                                accountId: AccountId,
                                callContext: Option[CallContext],
                                queryParams: OBPQueryParam*
  ) = 
    saveConnectorMetric 
    {
      try {
        val accountHolder = getAccountHolderCached(bankId,accountId)
        
        //TODO this is a quick solution for cache, because of (queryParams: OBPQueryParam*)
        def getTransactionsCached(bankId: BankId, accountId: AccountId, userId : String , loginUser: String): Box[List[Transaction]] =  {
          /**
            * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
            * is just a temporary value filed with UUID values in order to prevent any ambiguity.
            * The real value will be assigned by Macro during compile time at this line of a code:
            * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
            */
          var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
          CacheKeyFromArguments.buildCacheKey {
            Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(getTransactionsTTL millisecond) {
            val limit: OBPLimit = queryParams.collect { case OBPLimit(value) => OBPLimit(value) }.headOption.get
            val offset = queryParams.collect { case OBPOffset(value) => OBPOffset(value) }.headOption.get
            val fromDate = queryParams.collect { case OBPFromDate(date) => OBPFromDate(date) }.headOption.get
            val toDate = queryParams.collect { case OBPToDate(date) => OBPToDate(date)}.headOption.get
            val ordering = queryParams.collect {
              //we don't care about the intended sort field and only sort on finish date for now
              case OBPOrdering(field, direction) => OBPOrdering(field, direction)}.headOption.get
            val optionalParams = Seq(limit, offset, fromDate, toDate, ordering)

            //the following are OBPJVM page classes, need to map to OBP pages
            val invalid = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, UTC)
            val earliest = ZonedDateTime.of(1999, 1, 1, 0, 0, 0, 0, UTC) // todo how from scala?
            val latest = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, UTC)   // todo how from scala?
            val filter = new TimestampFilter("postedDate", earliest, latest)
            val sorter = new DefaultSorter("completedDate", Pager.SortOrder.ascending)
            val pageSize = Pager.DEFAULT_SIZE; // all in one page
            val pager = new DefaultPager(pageSize, 0, filter, sorter)

            val req1 = OutboundTransactionsQuery(
              version = formatVersion,
              name = "get",
              target = "transactions",
              accountId = accountId.toString,
              bankId = bankId.toString,
              userId = userId
            )
            val requestToMap= anyToMap(req1)

            val responseFromKafka = process(requestToMap)
            logger.debug("the getTransactions from JVMcompatible is : "+responseFromKafka)
            val rList =responseFromKafka.extract[List[KafkaInboundTransaction]]
            // Check does the response data match the requested data
            val isCorrect = rList.forall(x=>x.accountId == accountId.value && x.bankId == bankId.value)
            if (!isCorrect) throw new Exception(ErrorMessages.InvalidConnectorResponseForGetTransactions)
            // Populate fields and generate result
            val res = for {
              r <- rList
              transaction <- createNewTransaction(r)
            } yield {
              transaction
            }
            Full(res)
            //TODO is this needed updateAccountTransactions(bankId, accountId)
          }
        }
      }
        getTransactionsCached(bankId,accountId,accountHolder , AuthUser.getCurrentUserUsername).map(transactions => (transactions, callContext))
      } catch {
        case m: MappingException =>
          logger.error("getTransactions-MappingException",m)
          Failure(AdapterOrCoreBankingSystemException)
        case m: TimeoutException =>
          logger.error("getTransactions-TimeoutException",m)
          Failure(FutureTimeoutException)
        case m: ClassCastException =>
          logger.error("getTransactions-ClassCastException",m)
          Failure(KafkaMessageClassCastException)
        case m: RuntimeException =>
          logger.error("getTransactions-AccountID-UserId-Mapping",m)
          Failure(m.getMessage)
        case m: Throwable =>
          logger.error("getTransactions-Unexpected",m)
          Failure(UnknownError)
      }
    }("getTransactions")

  override def getBankAccount(
                               bankId: BankId,
                               accountId: AccountId,
                               callContext: Option[CallContext]
                             )= saveConnectorMetric {
    try {
      val accountHolder = getAccountHolderCached(bankId,accountId)

      def getBankAccountCached(
                                bankId: BankId,
                                accountId: AccountId,
                                userId : String,
                                loginUser: String // added the login user here ,is just for cache
                              ): Box[(BankAccount, Option[CallContext])] = {
        /**
          * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
          * is just a temporary value filed with UUID values in order to prevent any ambiguity.
          * The real value will be assigned by Macro during compile time at this line of a code:
          * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
          */
        var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
        CacheKeyFromArguments.buildCacheKey {
          Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(getAccountTTL millisecond) {

            // Generate random uuid to be used as request-response match id
            val req = Map(
              "version" -> formatVersion,
              "name" -> "get",
              "target" -> "account",
              "accountId" -> accountId.value,
              "bankId" -> bankId.toString,
              "userId" -> userId
            )
            val r = process(req).extract[KafkaInboundAccount]
            logger.debug(s"getBankAccount says ! account.isPresent and userId is ${userId}")
            Full(new KafkaBankAccount(r), callContext)
          }
        }}
      getBankAccountCached(bankId: BankId, accountId: AccountId, accountHolder, AuthUser.getCurrentUserUsername)
    } catch {
      case m: MappingException =>
        logger.error("getBankAccount-MappingException",m)
        Failure(AdapterOrCoreBankingSystemException)
      case m: TimeoutException =>
        logger.error("getBankAccount-TimeoutException",m)
        Failure(FutureTimeoutException)
      case m: ClassCastException =>
        logger.error("getBankAccount-ClassCastException",m)
        Failure(KafkaMessageClassCastException)
      case m: RuntimeException =>
        logger.error("getBankAccount-AccountID-UserId-Mapping",m)
        Failure(m.getMessage)
      case m: Throwable =>
        logger.error("getBankAccount-Unexpected",m)
        Failure(UnknownError)
    }
  }("getBankAccount")

  private def getAccountByNumber(bankId : BankId, number : String) : Box[BankAccount] = Empty
  // memoizeSync(getAccountTTL millisecond) {
//    val accountHolder = getAccountHolderCached(bankId,accountId)
//    val req = Map(
//      "version" -> formatVersion,
//      "name" -> "get",
//      "target" -> "account",
//      "accountId" -> number,
//      "bankId" -> bankId.toString,
//      "userId" -> primaryUserIdentifier
//    )
//    // Since result is single account, we need only first list entry
//    val r = process(req).extract[KafkaInboundAccount]
//    createMappedAccountDataIfNotExisting(r.bankId, r.accountId, r.label)
//    Full(new KafkaBankAccount(r))
//  }

  /**
   *
   * refreshes transactions via hbci if the transaction info is sourced from hbci
   *
   *  Checks if the last update of the account was made more than one hour ago.
   *  if it is the case we put a message in the message queue to ask for
   *  transactions updates
   *
   *  It will be used each time we fetch transactions from the DB. But the test
   *  is performed in a different thread.
   */
  /*
  private def updateAccountTransactions(bankId : BankId, accountId : AccountId) = {

    for {
      (bank, _)<- getBank(bankId, None)
      account <- getBankAccountType(bankId, accountId)
    } {
      spawn{
        val useMessageQueue = APIUtil.getPropsAsBoolValue("messageQueue.updateBankAccountsTransaction", false)
        val outDatedTransactions = Box!!account.lastUpdate match {
          case Full(l) => now after time(l.getTime + hours(APIUtil.getPropsAsIntValue("messageQueue.updateTransactionsInterval", 1)))
          case _ => true
        }
        //if(outDatedTransactions && useMessageQueue) {
        //  UpdatesRequestSender.sendMsg(UpdateBankAccount(account.number, bank.national_identifier.get))
        //}
      }
    }
  }
  */


  override def getCounterparty(thisBankId: BankId, thisAccountId: AccountId, couterpartyId: String): Box[Counterparty] = {
    //note: kafka mode just used the mapper data
    LocalMappedConnector.getCounterparty(thisBankId, thisAccountId, couterpartyId)
  }

  override def getCounterpartyByIban(iban: String, callContext: Option[CallContext]) =
    LocalMappedConnector.getCounterpartyByIban(iban: String, callContext)
  
  override def createOrUpdatePhysicalCard(bankCardNumber: String,
                      nameOnCard: String,
                      issueNumber: String,
                      serialNumber: String,
                      validFrom: Date,
                      expires: Date,
                      enabled: Boolean,
                      cancelled: Boolean,
                      onHotList: Boolean,
                      technology: String,
                      networks: List[String],
                      allows: List[String],
                      accountId: String,
                      bankId: String,
                      replacement: Option[CardReplacementInfo],
                      pinResets: List[PinResetInfo],
                      collected: Option[CardCollectionInfo],
                      posted: Option[CardPostedInfo]
                     ) : Box[PhysicalCard] = {
    Empty
  }


  protected override def makePaymentImpl(fromAccount: BankAccount,
                                         toAccount: BankAccount,
                                         transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                         amt: BigDecimal,
                                         description: String,
                                         transactionRequestType: TransactionRequestType,
                                         chargePolicy: String): Box[TransactionId] = {

    val sentTransactionId = saveTransaction(fromAccount,
                                            toAccount,
                                            transactionRequestCommonBody,
                                            amt,
                                            description,
                                            transactionRequestType,
                                            chargePolicy)

    sentTransactionId
  }

  
  case class TransactionQuery(
    name: String = "put",
    target: String = "transaction",
    version: String = formatVersion,
    `type`: String = "obp.mar.2017",
    fields: PaymentFields 
  )
  
  case class PaymentFields(
    fromAccountName: String,
    fromAccountId: String, 
    fromAccountBankId: String, 
    fromAccountCurrency: String,
    transactionId: String, 
    transactionRequestType: String, 
    transactionCurrency: String, 
    transactionAmount: String, 
    transactionChargePolicy: String, 
    transactionChargeAmount: String, 
    transactionChargeCurrency: String,
    transactionDescription: String, 
    transactionPostedDate: String, 
    toCounterpartyId: String, 
    toCounterpartyName: String, 
    toCounterpartyCurrency: String, 
    toCounterpartyAccountRoutingAddress: String, 
    toCounterpartyAccountRoutingScheme: String,  
    toCounterpartyBankRoutingAddress: String, 
    toCounterpartyBankRoutingScheme: String 
  )
  /**
   * Saves a transaction with amount @amount and counterparty @counterparty for account @account. Returns the id
   * of the saved transaction.
   */
  private def saveTransaction(fromAccount: BankAccount,toAccount: BankAccount,
                              transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                              amount: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String) = {
  
    val toCounterpartyAccountRoutingAddress =
      if (transactionRequestType.value == SANDBOX_TAN.toString)
        toAccount.accountId.value
      else
        toAccount.accountRoutingAddress
  
    val toCounterpartyBankRoutingAddress =
      if (transactionRequestType.value == SANDBOX_TAN.toString)
        toAccount.bankId.value
      else
        toAccount.bankRoutingAddress
    
    val toCounterpartyName =
      if (transactionRequestType.value == SANDBOX_TAN.toString)
        getAccountHolderCached(BankId(toCounterpartyBankRoutingAddress), AccountId(toCounterpartyAccountRoutingAddress))
      else
        toAccount.name
  
    val req = TransactionQuery(
      fields = PaymentFields(
        //from Account
        fromAccountName = AuthUser.getCurrentUserUsername, //"1000203891", //need fill 1
        fromAccountId = fromAccount.accountId.value,//"1f5587fa-8ad8-3c6b-8fac-ac3db5bdc3db", //need fill 2 --> 1000203891(account 05010616953) 
        fromAccountBankId = fromAccount.bankId.value , //"00100", //need fill 3
        fromAccountCurrency = fromAccount.currency,//"XAF", //need fill 4
        //transaction detail
        transactionId = APIUtil.generateUUID().take(35), //need fill 5
        transactionRequestType = transactionRequestType.value,
        transactionCurrency = "XAF", //need fill 6
        transactionAmount = amount.toString(), //"3001", //need fill 7
        transactionChargePolicy = "No3",
        transactionChargeAmount = "10.0000", //need fill 8
        transactionChargeCurrency = "XAF",
        transactionDescription = description,
        transactionPostedDate = "2016-01-21T18:46:19.056Z", //TODO, this is fixed for now. because of Bank server need it.
        // to Counterparty
        toCounterpartyId = "not used 2",
        toCounterpartyName = toCounterpartyName, //toCounterparty.name, //"1000203892", // need fill 9, DEBTOR_NAME
        toCounterpartyCurrency = "XAF", //need fill 10
        toCounterpartyAccountRoutingAddress = toCounterpartyAccountRoutingAddress, //TODO fix the name  //"410ad4eb-9f63-300f-8cb9-12f0ab677521", //need fill 11 1000203893(account 06010616954)
        toCounterpartyAccountRoutingScheme = "not used 3",
        toCounterpartyBankRoutingAddress = toCounterpartyBankRoutingAddress,//"00100", //need fill 12
        toCounterpartyBankRoutingScheme = "not used 4"
      )
    )
  
    val requestToMap= anyToMap(req)
    
    try{
      // Since result is single account, we need only first list entry
      val r = process(requestToMap)
      
      r.extract[KafkaInboundTransactionId] match {
//        case r: KafkaInboundTransactionId => Full(TransactionId(r.transactionId))
        // for now, we need just send the empty transaction-id, because the payments stuff is handling by SOPRA server.
        // need some time to create the transaction, and get the id .  
        case r: KafkaInboundTransactionId => Full(TransactionId(""))
        case _ => Full(TransactionId(""))
      }
      
     } catch {
        case m: MappingException =>
          logger.error("getBankAccount-MappingException",m)
          Failure(AdapterOrCoreBankingSystemException)
        case m: TimeoutException =>
          logger.error("getBankAccount-TimeoutException",m)
          Failure(FutureTimeoutException)
        case m: ClassCastException =>
          logger.error("getBankAccount-ClassCastException",m)
          Failure(KafkaMessageClassCastException)
        case m: RuntimeException =>
          logger.error("getBankAccount-AccountID-UserId-Mapping",m)
          Failure(m.getMessage)
        case m: Throwable =>
          logger.error("getBankAccount-Unexpected",m)
          Failure(UnknownError)
      }
    
  }

  /*
    Transaction Requests
  */
  //TODO not finished, look at override def getTransactionRequestStatusesImpl() in obpjvmMappedConnector
  override def getTransactionRequestStatusesImpl() : Box[TransactionRequestStatus] ={
    logger.debug("Kafka getTransactionRequestStatusesImpl response -- This is KafkaMappedConnector, just call KafkaMappedConnector_vMar2017 methods:")
    KafkaMappedConnector_vMar2017.getTransactionRequestStatusesImpl()
  }

  override def createTransactionRequestImpl(transactionRequestId: TransactionRequestId, transactionRequestType: TransactionRequestType,
                                            account : BankAccount, counterparty : BankAccount, body: TransactionRequestBody,
                                            status: String, charge: TransactionRequestCharge) : Box[TransactionRequest] = {
    TransactionRequests.transactionRequestProvider.vend.createTransactionRequestImpl(transactionRequestId,
      transactionRequestType,
      account,
      counterparty,
      body,
      status,
      charge)
  }

  //Note: now call the local mapper to store data
  override def saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId): Box[Boolean] = {
    LocalMappedConnector.saveTransactionRequestTransactionImpl(transactionRequestId: TransactionRequestId, transactionId: TransactionId)
  }

  override def saveTransactionRequestChallengeImpl(transactionRequestId: TransactionRequestId, challenge: TransactionRequestChallenge): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestChallengeImpl(transactionRequestId, challenge)
  }

  override def saveTransactionRequestStatusImpl(transactionRequestId: TransactionRequestId, status: String): Box[Boolean] = {
    TransactionRequests.transactionRequestProvider.vend.saveTransactionRequestStatusImpl(transactionRequestId, status)
  }


  override def getTransactionRequestsImpl(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  override def getTransactionRequestsImpl210(fromAccount : BankAccount) : Box[List[TransactionRequest]] = {
    TransactionRequests.transactionRequestProvider.vend.getTransactionRequests(fromAccount.bankId, fromAccount.accountId)
  }

  /*
    Bank account creation
   */

  //creates a bank account (if it doesn't exist) and creates a bank (if it doesn't exist)
  //again assume national identifier is unique
  override def createBankAndAccount(
    bankName: String,
    bankNationalIdentifier: String,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ) = {
    //don't require and exact match on the name, just the identifier
    val bank: Bank = MappedBank.find(By(MappedBank.national_identifier, bankNationalIdentifier)) match {
      case Full(b) =>
        logger.debug(s"bank with id ${b.bankId} and national identifier ${b.nationalIdentifier} found")
        b
      case _ =>
        logger.debug(s"creating bank with national identifier $bankNationalIdentifier")
        //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank
        MappedBank.create
          .permalink(Helper.generatePermalink(bankName))
          .fullBankName(bankName)
          .shortBankName(bankName)
          .national_identifier(bankNationalIdentifier)
          .saveMe()
    }

    //TODO: pass in currency as a parameter?
    val account = createAccountIfNotExisting(
      bank.bankId,
      AccountId(APIUtil.generateUUID()),
      accountNumber,
      accountType,
      accountLabel,
      currency,
      0L,
      accountHolderName
    )

    Full((bank, account))
  }

//  //for sandbox use -> allows us to check if we can generate a new test account with the given number
  override def accountExists(bankId: BankId, accountNumber: String) = Full(true)
  // {
//    getAccountByNumber(bankId, accountNumber) != null
//  }

  //remove an account and associated transactions
  override def removeAccount(bankId: BankId, accountId: AccountId) = {
    //delete comments on transactions of this account
    val commentsDeleted = Comments.comments.vend.bulkDeleteComments(bankId, accountId)

    //delete narratives on transactions of this account
    val narrativesDeleted = MappedNarrative.bulkDelete_!!(
      By(MappedNarrative.bank, bankId.value),
      By(MappedNarrative.account, accountId.value)
    )

    //delete narratives on transactions of this account
    val tagsDeleted = Tags.tags.vend.bulkDeleteTags(bankId, accountId)

    //delete WhereTags on transactions of this account
    val whereTagsDeleted = WhereTags.whereTags.vend.bulkDeleteWhereTags(bankId, accountId)

    //delete transaction images on transactions of this account
    val transactionImagesDeleted = TransactionImages.transactionImages.vend.bulkDeleteTransactionImage(bankId, accountId)

    //delete transactions of account
    val transactionsDeleted = MappedTransaction.bulkDelete_!!(
      By(MappedTransaction.bank, bankId.value),
      By(MappedTransaction.account, accountId.value)
    )

    //remove view privileges
    val privilegesDeleted = Views.views.vend.removeAllPermissions(bankId, accountId)

    //delete views of account
    val viewsDeleted = Views.views.vend.removeAllViews(bankId, accountId)

    //delete account
    val account = getBankAccount(bankId, accountId)

    val accountDeleted = account match {
      case acc => true //acc.delete_! //TODO
      // case _ => false
    }

    Full(commentsDeleted && narrativesDeleted && tagsDeleted && whereTagsDeleted && transactionImagesDeleted &&
      transactionsDeleted && privilegesDeleted && viewsDeleted && accountDeleted)
}

  //creates a bank account for an existing bank, with the appropriate values set. Can fail if the bank doesn't exist
  override def createSandboxBankAccount(
    bankId: BankId,
    accountId: AccountId,
    accountNumber: String,
    accountType: String,
    accountLabel: String,
    currency: String,
    initialBalance: BigDecimal,
    accountHolderName: String,
    branchId: String,
    accountRoutingScheme: String,
    accountRoutingAddress: String
  ): Box[BankAccount] = {

    for {
      (bank, _)<- getBank(bankId, None) //bank is not really used, but doing this will ensure account creations fails if the bank doesn't
    } yield {

      val balanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(initialBalance, currency)
      createAccountIfNotExisting(bankId, accountId, accountNumber, accountType, accountLabel, currency, balanceInSmallestCurrencyUnits, accountHolderName)
    }

  }

  private def createAccountIfNotExisting(bankId: BankId, accountId: AccountId, accountNumber: String,
                                         accountType: String, accountLabel: String, currency: String,
                                         balanceInSmallestCurrencyUnits: Long, accountHolderName: String) : BankAccount = {
    getBankAccount(bankId, accountId) match {
      case Full(a) =>
        logger.debug(s"account with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        a
      case _ => null //TODO
        /*
       new  KafkaBankAccount
          .bank(bankId.value)
          .theAccountId(accountId.value)
          .accountNumber(accountNumber)
          .accountType(accountType)
          .accountLabel(accountLabel)
          .accountCurrency(currency)
          .accountBalance(balanceInSmallestCurrencyUnits)
          .holder(accountHolderName)
          .saveMe()
          */
    }
  }

  private def createMappedAccountDataIfNotExisting(bankId: String, accountId: String, label: String) : Boolean = {
    MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId),
                                    By(MappedBankAccountData.bankId, bankId)) match {
      case Empty =>
        val data = new MappedBankAccountData
        data.setAccountId(accountId)
        data.setBankId(bankId)
        data.setLabel(label)
        data.save()
        true
      case _ =>
        logger.debug(s"account data with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        false
    }
  }

  /*
    End of bank account creation
   */


  /*
    Transaction importer api
   */

  //used by the transaction import api
  override def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: BigDecimal) = {

    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
    } yield {
      //acc.balance = newBalance
      setBankAccountLastUpdated(bank.nationalIdentifier, acc.number, now).openOrThrowException(attemptedToOpenAnEmptyBox)
    }
  
    Full(result.getOrElse(false))
  }

  //transaction import api uses bank national identifiers to uniquely indentify banks,
  //which is unfortunate as theoretically the national identifier is unique to a bank within
  //one country
  private def getBankByNationalIdentifier(nationalIdentifier : String) : Box[Bank] = {
    MappedBank.find(By(MappedBank.national_identifier, nationalIdentifier))
  }


  private val bigDecimalFailureHandler : PartialFunction[Throwable, Unit] = {
    case ex : NumberFormatException => {
      logger.warn(s"could not convert amount to a BigDecimal: $ex")
    }
  }
//
//  //used by transaction import api call to check for duplicates
  override def getMatchingTransactionCount(bankNationalIdentifier : String, accountNumber : String, amount: String, completed: Date, otherAccountHolder: String) = Full(5)
//   {
//    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
//    val count = for {
//      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
//      account <- getAccountByNumber(bankId, accountNumber)
//      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(amount))
//    } yield {
//
//      val amountInSmallestCurrencyUnits =
//        Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)
//
//      MappedTransaction.count(
//        By(MappedTransaction.bank, bankId.value),
//        By(MappedTransaction.account, account.accountId.value),
//        By(MappedTransaction.amount, amountInSmallestCurrencyUnits),
//        By(MappedTransaction.tFinishDate, completed),
//        By(MappedTransaction.counterpartyAccountHolder, otherAccountHolder))
//    }
//
//    //icky
//    count.map(_.toInt) getOrElse 0
//  }

//  //used by transaction import api
  override def createImportedTransaction(transaction: ImporterTransaction): Box[Transaction] = Empty 
  // {
//    //we need to convert from the legacy bankNationalIdentifier to BankId, and from the legacy accountNumber to AccountId
//    val obpTransaction = transaction.obp_transaction
//    val thisAccount = obpTransaction.this_account
//    val nationalIdentifier = thisAccount.bank.national_identifier
//    val accountNumber = thisAccount.number
//    for {
//      bank <- getBankByNationalIdentifier(transaction.obp_transaction.this_account.bank.national_identifier) ?~!
//        s"No bank found with national identifier $nationalIdentifier"
//      bankId = bank.bankId
//      account <- getAccountByNumber(bankId, accountNumber)
//      details = obpTransaction.details
//      amountAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.value.amount))
//      newBalanceAsBigDecimal <- tryo(bigDecimalFailureHandler)(BigDecimal(details.new_balance.amount))
//      amountInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(amountAsBigDecimal, account.currency)
//      newBalanceInSmallestCurrencyUnits = Helper.convertToSmallestCurrencyUnits(newBalanceAsBigDecimal, account.currency)
//      otherAccount = obpTransaction.other_account
//      mappedTransaction = MappedTransaction.create
//        .bank(bankId.value)
//        .account(account.accountId.value)
//        .transactionType(details.kind)
//        .amount(amountInSmallestCurrencyUnits)
//        .newAccountBalance(newBalanceInSmallestCurrencyUnits)
//        .currency(account.currency)
//        .tStartDate(details.posted.`$dt`)
//        .tFinishDate(details.completed.`$dt`)
//        .description(details.label)
//        .counterpartyAccountNumber(otherAccount.number)
//        .counterpartyAccountHolder(otherAccount.holder)
//        .counterpartyAccountKind(otherAccount.kind)
//        .counterpartyNationalId(otherAccount.bank.national_identifier)
//        .counterpartyBankName(otherAccount.bank.name)
//        .counterpartyIban(otherAccount.bank.IBAN)
//        .saveMe()
//      transaction <- mappedTransaction.toTransaction(account)
//    } yield transaction
//  }

  override def setBankAccountLastUpdated(bankNationalIdentifier: String, accountNumber : String, updateDate: Date)  = Full(true)
  // {
//    val result = for {
//      bankId <- getBankByNationalIdentifier(bankNationalIdentifier).map(_.bankId)
//      account <- getAccountByNumber(bankId, accountNumber)
//    } yield {
//        val acc = getBankAccount(bankId, account.accountId)
//        acc match {
//          case a => true //a.lastUpdate = updateDate //TODO
//          case _ => logger.warn("can't set bank account.lastUpdated because the account was not found"); false
//        }
//    }
//    result.getOrElse(false)
//  }

  /*
    End of transaction importer api
   */


  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String) = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId.value), By(MappedBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    Full(result.getOrElse(false))
  }


  override def getProducts(bankId: BankId): Box[List[Product]] = {
    LocalMappedConnector.getProducts(bankId)
  }

  override def createOrUpdateProduct(bankId : String,
                                     code : String,
                                     parentProductCode : Option[String],
                                     name : String,
                                     category : String,
                                     family : String,
                                     superFamily : String,
                                     moreInfoUrl : String,
                                     details : String,
                                     description : String,
                                     metaLicenceId : String,
                                     metaLicenceName : String): Box[Product] = {
    LocalMappedConnector.createOrUpdateProduct(bankId, code, parentProductCode, name, category, family, superFamily, moreInfoUrl, details, description, metaLicenceId, metaLicenceName)
  }

  override def getProduct(bankId: BankId, productCode: ProductCode): Box[Product] = {
    LocalMappedConnector.getProduct(bankId, productCode)
  }

  override  def createOrUpdateBranch(branch: Branch): Box[BranchT] = {
    LocalMappedConnector.createOrUpdateBranch(branch)
  }

  override def createOrUpdateAtm(atm: Atms.Atm): Box[AtmT] = {
    LocalMappedConnector.createOrUpdateAtm(atm)
  }

  override def getAtm(bankId: BankId, atmId: AtmId): Box[MappedAtm] = {
    LocalMappedConnector.getAtm(bankId, atmId)
  }
  

  override def getCurrentFxRate(bankId : BankId, fromCurrencyCode: String, toCurrencyCode: String): Box[FXRate] = Empty
  
  //TODO need to fix in obpjvm, just mocked result as Mapper
  override def getTransactionRequestTypeCharge(bankId: BankId, accountId: AccountId, viewId: ViewId, transactionRequestType: TransactionRequestType): Box[TransactionRequestTypeCharge] = {
    val transactionRequestTypeChargeMapper = MappedTransactionRequestTypeCharge.find(
      By(MappedTransactionRequestTypeCharge.mBankId, bankId.value),
      By(MappedTransactionRequestTypeCharge.mTransactionRequestTypeId, transactionRequestType.value))
    
    val transactionRequestTypeCharge = transactionRequestTypeChargeMapper match {
      case Full(transactionRequestType) => Full(TransactionRequestTypeChargeMock(
        transactionRequestType.transactionRequestTypeId,
        transactionRequestType.bankId,
        transactionRequestType.chargeCurrency,
        transactionRequestType.chargeAmount,
        transactionRequestType.chargeSummary
      )
      )
      //If it is empty, return the default value : "0.0000000" and set the BankAccount currency
      case _ =>
        for {
          fromAccount <- getBankAccount(bankId, accountId)
          fromAccountCurrency <- tryo{ fromAccount.currency }
        } yield {
          TransactionRequestTypeChargeMock(transactionRequestType.value, bankId.value, fromAccountCurrency, "0.00", "Warning! Default value!")
        }
    }
    
    transactionRequestTypeCharge
  }
  
  
  override def getEmptyBankAccount(): Box[BankAccount] = {
    Full(
      new KafkaBankAccount(
        KafkaInboundAccount(
          accountId = "",
          bankId = "",
          number = "",
          `type` = "",
          balanceAmount = "",
          balanceCurrency = "",
          iban = ""
        )
      )
    )
  }

  /////////////////////////////////////////////////////////////////////////////



  // Helper for creating a transaction
  def createNewTransaction(r: KafkaInboundTransaction):Box[Transaction] = {
    var datePosted: Date = null
    val formatter = DateTimeFormatter.ofPattern(DATE_FORMAT)
    if (r.postedDate != null) // && r.details.posted.matches("^[0-9]{8}$"))
      datePosted = new SimpleDateFormat(APIUtil.DateWithMinutes).parse(r.postedDate)

    var dateCompleted: Date = null
    if (r.completedDate != null) // && r.details.completed.matches("^[0-9]{8}$"))
      dateCompleted = new SimpleDateFormat(APIUtil.DateWithMinutes).parse(r.completedDate)

    for {
        counterpartyId <- tryo{r.counterpartyId}
        counterpartyName <- tryo{r.counterpartyName}
        thisAccount <- getBankAccount(BankId(r.bankId), AccountId(r.accountId))
        //creates a dummy OtherBankAccount without an OtherBankAccountMetadata, which results in one being generated (in OtherBankAccount init)
        dummyOtherBankAccount <- tryo{createCounterparty(counterpartyId, counterpartyName, thisAccount, None)}
        //and create the proper OtherBankAccount with the correct "id" attribute set to the metadataId of the OtherBankAccountMetadata object
        //note: as we are passing in the OtherBankAccountMetadata we don't incur another db call to get it in OtherBankAccount init
        counterparty <- tryo{createCounterparty(counterpartyId, counterpartyName, thisAccount, Some(dummyOtherBankAccount.metadata))}
      } yield {
        // Create new transaction
        new Transaction(
          r.transactionId, // uuid:String
          TransactionId(r.transactionId), // id:TransactionId
          thisAccount, // thisAccount:BankAccount
          counterparty, // otherAccount:OtherBankAccount
          r.`type`, // transactionType:String
          BigDecimal(r.amount), // val amount:BigDecimal
          thisAccount.currency, // currency:String
          Some(r.description), // description:Option[String]
          datePosted, // startDate:Date
          dateCompleted, // finishDate:Date
          BigDecimal(r.newBalanceAmount) // balance:BigDecimal)
        )
    }
  }


  case class KafkaBank(r: KafkaInboundBank) extends Bank {
    def fullName           = r.name
    def shortName          = r.name
    def logoUrl            = r.logo
    def bankId             = BankId(r.bankId)
    def nationalIdentifier = "None"  
    def swiftBic           = "None"  
    def websiteUrl         = r.url
    def bankRoutingScheme = "None"
    def bankRoutingAddress = "None"
  }

  // Helper for creating other bank account
  def createCounterparty(counterpartyId: String, counterpartyName: String, o: BankAccount, alreadyFoundMetadata : Option[CounterpartyMetadata]) = {
    new Counterparty(
      counterpartyId = alreadyFoundMetadata.map(_.getCounterpartyId).getOrElse(""),
      counterpartyName = counterpartyName,
      nationalIdentifier = "",
      otherBankRoutingAddress = None,
      otherAccountRoutingAddress = None,
      thisAccountId = AccountId(counterpartyId),
      thisBankId = BankId(""),
      kind = "",
      otherBankRoutingScheme = "",
      otherAccountRoutingScheme="",
      otherAccountProvider = "",
      isBeneficiary = true
    )
  }
  case class KafkaBankAccount(r: KafkaInboundAccount) extends BankAccount {
    def accountId : AccountId       = AccountId(r.accountId)
    def accountType : String        = r.`type`
    def balance : BigDecimal        = BigDecimal(r.balanceAmount)
    def currency : String           = r.balanceCurrency
    def name : String               = "NONE" //TODO
    def iban : Option[String]       = Some(r.iban)
    def number : String             = r.number
    def bankId : BankId             = BankId(r.bankId)
    def lastUpdate : Date           = APIUtil.DateWithMsFormat.parse(today.getTime.toString)
    def accountHolder : String      = "NONE" //TODO
    def accountRoutingScheme: String = "NONE" //TODO
    def accountRoutingAddress: String = "NONE" //TODO
    def accountRoutings: List[AccountRouting] = List()
    def branchId: String = "NONE" //TODO
    def accountRules: List[AccountRule] = List() //TODO

    // Fields modifiable from OBP are stored in mapper
    def label : String              = (for {
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, r.accountId))
    } yield {
      d.getLabel
    }).getOrElse(r.number)

  }

  case class KafkaFXRate(kafkaInboundFxRate: KafkaInboundFXRate) extends FXRate {
    def bankId: BankId = BankId(kafkaInboundFxRate.bank_id)
    def fromCurrencyCode : String= kafkaInboundFxRate.from_currency_code
    def toCurrencyCode : String= kafkaInboundFxRate.to_currency_code
    def conversionValue : Double= kafkaInboundFxRate.conversion_value
    def inverseConversionValue : Double= kafkaInboundFxRate.inverse_conversion_value
    //TODO need to add error handling here for String --> Date transfer
    def effectiveDate : Date= APIUtil.DateWithMsFormat.parse(kafkaInboundFxRate.effective_date)
  }

  case class KafkaCounterparty(counterparty: KafkaInboundCounterparty) extends CounterpartyTrait {
    def createdByUserId: String = counterparty.created_by_user_id
    def name: String = counterparty.name
    def thisBankId: String = counterparty.this_bank_id
    def thisAccountId: String = counterparty.this_account_id
    def thisViewId: String = counterparty.this_view_id
    def counterpartyId: String = counterparty.counterparty_id
    def otherAccountRoutingScheme: String = counterparty.other_account_routing_scheme
    def otherAccountRoutingAddress: String = counterparty.other_account_routing_address
    def otherBankRoutingScheme: String = counterparty.other_bank_routing_scheme
    def otherBankRoutingAddress: String = counterparty.other_bank_routing_address
    def otherBranchRoutingScheme: String = counterparty.other_branch_routing_scheme
    def otherBranchRoutingAddress: String = counterparty.other_branch_routing_address
    def isBeneficiary : Boolean = counterparty.is_beneficiary
    def description: String = ""
    def otherAccountSecondaryRoutingScheme: String = ""
    def otherAccountSecondaryRoutingAddress: String = ""
    def bespoke: List[CounterpartyBespoke] = Nil
  }

  case class KafkaTransactionRequestTypeCharge(kafkaInboundTransactionRequestTypeCharge: KafkaInboundTransactionRequestTypeCharge) extends TransactionRequestTypeCharge{
    def transactionRequestTypeId: String = kafkaInboundTransactionRequestTypeCharge.transaction_request_type_id
    def bankId: String = kafkaInboundTransactionRequestTypeCharge.bank_id
    def chargeCurrency: String = kafkaInboundTransactionRequestTypeCharge.charge_currency
    def chargeAmount: String = kafkaInboundTransactionRequestTypeCharge.charge_amount
    def chargeSummary: String = kafkaInboundTransactionRequestTypeCharge.charge_summary
  }
  //link to transport.nov2016.Bank 
  //keep both the same fields.
  case class KafkaInboundBank(
    bankId: String,
    name: String,
    logo: String,
    url: String
  )


  /** Bank Branches
    *
    * @param id Uniquely identifies the Branch within the Bank. SHOULD be url friendly (no spaces etc.) Used in URLs
    * @param bank_id MUST match bank_id in Banks
    * @param name Informal name for the Branch
    * @param address Address
    * @param location Geolocation
    * @param meta Meta information including the license this information is published under
    * @param lobby Info about when the lobby doors are open
    * @param driveUp Info about when automated facilities are open e.g. cash point machine
    */
  case class KafkaInboundBranch(
                                 id : String,
                                 bank_id: String,
                                 name : String,
                                 address : KafkaInboundAddress,
                                 location : KafkaInboundLocation,
                                 meta : KafkaInboundMeta,
                                 lobby : Option[KafkaInboundLobby],
                                 driveUp : Option[KafkaInboundDriveUp])

  case class KafkaInboundLicense(
                                 id : String,
                                 name : String)

  case class KafkaInboundMeta(
                              license : KafkaInboundLicense)

  case class KafkaInboundLobby(
                               hours : String)

  case class KafkaInboundDriveUp(
                                 hours : String)

  /**
    *
    * @param line_1 Line 1 of Address
    * @param line_2 Line 2 of Address
    * @param line_3 Line 3 of Address
    * @param city City
    * @param county County i.e. Division of State
    * @param state State i.e. Division of Country
    * @param post_code Post Code or Zip Code
    * @param country_code 2 letter country code: ISO 3166-1 alpha-2
    */
  case class KafkaInboundAddress(
                                 line_1 : String,
                                 line_2 : String,
                                 line_3 : String,
                                 city : String,
                                 county : String, // Division of State
                                 state : String, // Division of Country
                                 post_code : String,
                                 country_code: String)

  case class KafkaInboundLocation(
                                  latitude : Double,
                                  longitude : Double)

  case class KafkaInboundValidatedUser(email: String,
                                       displayName: String)

  // link to adapter <-->transport.nov2016.Account
  // keep both side the same, when update some fields.
  case class KafkaInboundAccount(
                                  accountId : String,
                                  bankId : String,
//                                  label : String = "None",
                                  number : String,
                                  `type` : String,
                                  balanceAmount: String,
                                  balanceCurrency: String,
                                  iban : String
//                                  owners : List[String] = Nil,
//                                  generate_public_view : Boolean = false,
//                                  generate_accountants_view : Boolean = false,
//                                  generate_auditors_view : Boolean = false,
//                                  accountRoutingScheme: String  = "None",
//                                  accountRoutingAddress: String  = "None",
//                                  branchId: String  = "None"
                                 )

  case class KafkaInboundTransaction(
                                      transactionId : String,
                                      accountId : String,
                                      amount: String,
                                      bankId : String,
                                      completedDate: String,
                                      counterpartyId: String,
                                      counterpartyName: String,
                                      currency: String,
                                      description: String,
                                      newBalanceAmount: String,
                                      newBalanceCurrency: String,
                                      postedDate: String,
                                      `type`: String,
                                      userId: String
                                      )

  case class KafkaInboundAtm(
                              id : String,
                              bank_id: String,
                              name : String,
                              address : KafkaInboundAddress,
                              location : KafkaInboundLocation,
                              meta : KafkaInboundMeta
                           )

  case class KafkaInboundProduct(
                                 bank_id : String,
                                 code: String,
                                 name : String,
                                 category : String,
                                 family : String,
                                 super_family : String,
                                 more_info_url : String,
                                 meta : KafkaInboundMeta
                               )

  case class KafkaInboundAccountData(
                                      banks : List[KafkaInboundBank],
                                      users : List[InboundUser],
                                      accounts : List[KafkaInboundAccount]
                                   )

  // We won't need this. TODO clean up.
  case class KafkaInboundData(
                               banks : List[KafkaInboundBank],
                               users : List[InboundUser],
                               accounts : List[KafkaInboundAccount],
                               transactions : List[KafkaInboundTransaction],
                               branches: List[KafkaInboundBranch],
                               atms: List[KafkaInboundAtm],
                               products: List[KafkaInboundProduct],
                               crm_events: List[KafkaInboundCrmEvent]
                            )

  case class KafkaInboundCrmEvent(
                                   id : String, // crmEventId
                                   bank_id : String,
                                   customer: KafkaInboundCustomer,
                                   category : String,
                                   detail : String,
                                   channel : String,
                                   actual_date: String
                                 )

  case class KafkaInboundCustomer(
                                   name: String,
                                   number : String // customer number, also known as ownerId (owner of accounts) aka API User?
                                 )

  case class KafkaInboundTransactionId(
                                        transactionId : String
                                      )

  case class KafkaOutboundTransaction(
                                      north: String,
                                      version: String,
                                      name: String,
                                      accountId: String,
                                      currency: String,
                                      amount: String,
                                      otherAccountId: String,
                                      otherAccountCurrency: String,
                                      transactionType: String)

  case class KafkaInboundChallengeLevel(
                                       limit: String,
                                       currency: String
                                        )
  case class KafkaInboundTransactionRequestStatus(
                                             transactionRequestId : String,
                                             bulkTransactionsStatus: List[KafkaInboundTransactionStatus]
                                           )
  case class KafkaInboundTransactionStatus(
                                transactionId : String,
                                transactionStatus: String,
                                transactionTimestamp: String
                              )
  case class KafkaInboundCreateChallange(challengeId: String)
  case class KafkaInboundValidateChallangeAnswer(answer: String)

  case class KafkaInboundChargeLevel(
                                      currency: String,
                                      amount: String
                                    )

  case class KafkaInboundFXRate( bank_id: String,
                                 from_currency_code: String,
                                 to_currency_code: String,
                                 conversion_value: Double,
                                 inverse_conversion_value: Double,
                                 effective_date: String
                               )

  case class KafkaInboundCounterparty(
                                       name: String,
                                       created_by_user_id: String,
                                       this_bank_id: String,
                                       this_account_id: String,
                                       this_view_id: String,
                                       counterparty_id: String,
                                       other_bank_routing_scheme: String,
                                       other_account_routing_scheme: String,
                                       other_bank_routing_address: String,
                                       other_account_routing_address: String,
                                       other_branch_routing_scheme: String,
                                       other_branch_routing_address: String,
                                       is_beneficiary: Boolean
                                     )


  case class KafkaInboundTransactionRequestTypeCharge(
                                transaction_request_type_id: String,
                                bank_id: String,
                                charge_currency: String,
                                charge_amount: String,
                                charge_summary: String
                               )

}