package code.obp.grpc

import java.util.logging.Logger

import code.api.util.{APIUtil, CallContext, NewStyle}
import code.api.v3_0_0.{CoreTransactionsJsonV300, ModeratedTransactionCoreWithAttributes}
import code.api.v4_0_0.{BankJson400, BanksJson400, JSONFactory400, OBPAPI4_0_0}
import code.obp.grpc.api.BanksJson400Grpc.{BankJson400Grpc, BankRoutingJsonV121Grpc}
import code.obp.grpc.api._
import code.util.Helper
import code.views.Views
import com.google.protobuf.empty.Empty
import com.openbankproject.commons.model._
import io.grpc.{Server, ServerBuilder}
import net.liftweb.common.Full
import net.liftweb.json.JsonAST.{JField, JObject}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.{Extraction, JArray}

import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

/**
 * [[https://github.com/grpc/grpc-java/blob/v0.15.0/examples/src/main/java/io/grpc/examples/helloworld/HelloWorldServer.java]]
 */
object HelloWorldServer {
  private val logger = Logger.getLogger(classOf[HelloWorldServer].getName)

  def main(args: Array[String] = Array.empty): Unit = {
    val server = new HelloWorldServer(ExecutionContext.global)
    server.start()
    server.blockUntilShutdown()
  }

  val port = APIUtil.getPropsAsIntValue("grpc.server.port", Helper.findAvailablePort()) 
}

class HelloWorldServer(executionContext: ExecutionContext) { self =>
  private[this] var server: Server = null
  def start(): Unit = {

    val serverBuilder = ServerBuilder.forPort(HelloWorldServer.port)
      .addService(ObpServiceGrpc.bindService(ObpServiceImpl, executionContext))
      .asInstanceOf[ServerBuilder[_]]
    server = serverBuilder.build.start;
    HelloWorldServer.logger.info("Server started, listening on " + HelloWorldServer.port)
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  def stop(): Unit = {
    if (server != null) {
      server.shutdown()
      server = null
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  object ObpServiceImpl extends ObpServiceGrpc.ObpService {

    implicit val formats =  code.api.util.CustomJsonFormats.formats

    override def getBanks(request: Empty): Future[BanksJson400Grpc] = {
      val callContext: Option[CallContext] = Some(CallContext())
      NewStyle.function.getBanks(callContext)
        .map(it => {
          val (bankList, _) = it
          val json40: BanksJson400 = JSONFactory400.createBanksJson(bankList)
          val grpcBanks: List[BankJson400Grpc] = json40.banks.map(bank => {
            val BankJson400(id, short_name, full_name, logo, website, bank_routings) = bank
            val bankRoutingGrpcs = bank_routings.map(routings => BankRoutingJsonV121Grpc(routings.scheme, routings.address))
            BankJson400Grpc(id, short_name, full_name, logo, website, bankRoutingGrpcs)
          })
          BanksJson400Grpc(grpcBanks)
        })
    }

    override def getPrivateAccountsAtOneBank(request: BankIdUserIdGrpc): Future[AccountsGrpc] = {
      implicit val toBankExtended = code.model.toBankExtended(_)
      val callContext: Option[CallContext] = Some(CallContext())
      val bankId = BankId(request.bankId)
      val userId =  request.userId

      for {
        (bank, _) <- NewStyle.function.getBank(bankId, callContext)
        (user, _) <- NewStyle.function.findByUserId(userId, callContext)
      } yield {
        val (privateViewsUserCanAccessAtOneBank, privateAccountAccesses) = Views.views.vend.privateViewsUserCanAccessAtBank(user, bankId)
        val availablePrivateAccounts = bank.privateAccounts(privateAccountAccesses)
        val jValue = OBPAPI4_0_0.Implementations2_0_0.processAccounts(privateViewsUserCanAccessAtOneBank, availablePrivateAccounts)
        val jArray = JArray(
          jValue.asInstanceOf[JArray].arr.map(it => {
            val bankIdJObject: JObject = "bankId" -> (it \ "bank_id")
            it merge bankIdJObject
          })
        )
        val jObject = JObject(List(JField("accounts", jArray)))
        val accountsGrpc = jObject.extract[AccountsGrpc]
        accountsGrpc
      }
    }

    override def getBankAccountsBalances(request: BankIdGrpc): Future[AccountsBalancesV310JsonGrpc] = Future {
//      val callContext: Option[CallContext] = Some(CallContext())
//      val bankId = BankId(request.value)
//      val bankIdAccountIds: List[BankIdAccountId] = Nil
//      for {
//        (accountsBalances, callContext)<- NewStyle.function.getBankAccountsBalances(bankIdAccountIds, callContext)
//      }
      ???
    }

    override def getCoreTransactionsForBankAccount(request: BankIdAccountIdAndUserIdGrpc): Future[CoreTransactionsJsonV300Grpc] = {
      implicit val toViewExtended = code.model.toViewExtended(_)
      implicit val toBankAccountExtended = code.model.toBankAccountExtended(_)
      val callContext: Option[CallContext] = Some(CallContext())
      val bankId = BankId(request.bankId)
      val accountId = AccountId(request.accountId)
      for {
        (user, _) <- NewStyle.function.findByUserId(request.userId, callContext)
        (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
        (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
        view <- NewStyle.function.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
        (Full(transactionsCore), callContext) <- bankAccount.getModeratedTransactionsCore(bank, Full(user), view, BankIdAccountId(bankId, accountId), Nil, callContext)
        obpCoreTransactions: CoreTransactionsJsonV300 = code.api.v3_0_0.JSONFactory300.createCoreTransactionsJSON(transactionsCore.map(ModeratedTransactionCoreWithAttributes(_)))
      } yield {
        val jValue = Extraction.decompose(obpCoreTransactions)
        val coreTransactionsJsonV300Grpc = jValue.extract[CoreTransactionsJsonV300Grpc]
        coreTransactionsJsonV300Grpc
      }
    }
  }
}

