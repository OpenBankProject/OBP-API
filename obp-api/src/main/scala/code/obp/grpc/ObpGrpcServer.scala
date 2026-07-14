package code.obp.grpc

import org.json4s._
import scala.language.existentials
import scala.language.reflectiveCalls
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, CallContext, NewStyle}
import code.api.v3_0_0.{CoreTransactionsJsonV300, ModeratedTransactionCoreWithAttributes}
import code.api.v4_0_0.{BankJson400, BanksJson400, JSONFactory400, OBPAPI4_0_0}
import code.obp.grpc.api.BanksJson400Grpc.{BankJson400Grpc, BankRoutingJsonV121Grpc}
import code.obp.grpc.api._
import code.util.Helper
import code.util.Helper.MdcLoggable
import code.views.Views
import com.google.protobuf.empty.Empty
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model._
import io.grpc.{Server, ServerBuilder}
import net.liftweb.common.Full
import org.json4s.JsonAST.{JField, JObject}
import org.json4s.JsonDSL._
import org.json4s.{Extraction, JArray}

import scala.concurrent.{ExecutionContext, Future}

/**
 * OBP gRPC server — serves banking RPCs (ObpService) and chat streaming RPCs (ChatStreamService).
 * Enable via grpc.server.enabled=true in props.
 */
object ObpGrpcServer {

  def main(args: Array[String] = Array.empty): Unit = {
    val server = new ObpGrpcServer(code.api.util.BlockingIoExecutionContext.ec)
    server.start()
    server.blockUntilShutdown()
  }

  val port = APIUtil.getPropsAsIntValue("grpc.server.port", 50051)
}

class ObpGrpcServer(executionContext: ExecutionContext) extends MdcLoggable { self =>
  private[this] var server: Server = null
  def start(): Unit = {

    // Start chat event bus for Redis pub/sub streaming
    code.chat.ChatEventBus.start()

    // Start log cache event bus (no-op if grpc.log_cache_stream.enabled=false)
    code.logcache.LogCacheEventBus.start()

    // Start metrics event bus (no-op if grpc.metrics_stream.enabled=false)
    code.metricsstream.MetricsEventBus.start()

    val baseBuilder = ServerBuilder.forPort(ObpGrpcServer.port)
      .addService(ObpServiceGrpc.bindService(ObpServiceImpl, executionContext))
      .addService(code.obp.grpc.chat.api.ChatStreamServiceGrpc.bindService(
        code.obp.grpc.chat.ChatStreamServiceImpl, executionContext))
      .addService(io.grpc.protobuf.services.ProtoReflectionService.newInstance())
      .intercept(new code.obp.grpc.chat.AuthInterceptor())

    val withLogCache =
      if (code.logcache.LogCacheEventBus.isEnabled)
        baseBuilder.addService(code.obp.grpc.logcache.api.LogCacheStreamServiceGrpc.bindService(
          code.obp.grpc.logcache.LogCacheStreamServiceImpl, executionContext))
      else baseBuilder

    val serverBuilder =
      (if (code.metricsstream.MetricsEventBus.isEnabled)
        withLogCache.addService(code.obp.grpc.metricsstream.api.MetricsStreamServiceGrpc.bindService(
          code.obp.grpc.metricsstream.MetricsStreamServiceImpl, executionContext))
       else withLogCache)
      .asInstanceOf[ServerBuilder[_]]
    server = serverBuilder.build.start;
    logger.info("Server started, listening on " + ObpGrpcServer.port)
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  def stop(): Unit = {
    code.chat.ChatEventBus.stop()
    code.logcache.LogCacheEventBus.stop()
    code.metricsstream.MetricsEventBus.stop()
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
            val BankJson400(id, short_name, full_name, logo, website, bank_routings, None) = bank
            val bankRoutingGrpcs = bank_routings.map(routings => BankRoutingJsonV121Grpc(routings.scheme, routings.address))
            BankJson400Grpc(id, short_name, full_name, logo, website, bankRoutingGrpcs)
          })
          BanksJson400Grpc(grpcBanks)
        })
    }

    // Temporarily disabled — see api.proto, ApiProto.scala javaDescriptor filter,
    // and ObpServiceGrpc.scala for the matching changes.
    //
    //override def getPrivateAccountsAtOneBank(request: BankIdUserIdGrpc): Future[AccountsGrpc] = {
    //  implicit val toBankExtended = code.model.toBankExtended(_)
    //  val callContext: Option[CallContext] = Some(CallContext())
    //  val bankId = BankId(request.bankId)
    //  val userId =  request.userId
    //
    //  for {
    //    (bank, _) <- NewStyle.function.getBank(bankId, callContext)
    //    (user, _) <- NewStyle.function.findByUserId(userId, callContext)
    //  } yield {
    //    val (privateViewsUserCanAccessAtOneBank, privateAccountAccess) = Views.views.vend.privateViewsUserCanAccessAtBank(user, bankId)
    //    val availablePrivateAccounts = bank.privateAccounts(privateAccountAccess)
    //    val jValue = OBPAPI4_0_0.Implementations2_0_0.processAccounts(privateViewsUserCanAccessAtOneBank, availablePrivateAccounts)
    //    val jArray = JArray(
    //      jValue.asInstanceOf[JArray].arr.map(it => {
    //        val bankIdJObject: JObject = "bankId" -> (it \ "bank_id")
    //        it merge bankIdJObject
    //      })
    //    )
    //    val jObject = JObject(List(JField("accounts", jArray)))
    //    val accountsGrpc = jObject.extract[AccountsGrpc]
    //    accountsGrpc
    //  }
    //}
    //
    //override def getBankAccountsBalances(request: BankIdGrpc): Future[AccountsBalancesV310JsonGrpc] = Future {
    //  ???
    //}
    //
    //override def getCoreTransactionsForBankAccount(request: BankIdAccountIdAndUserIdGrpc): Future[CoreTransactionsJsonV300Grpc] = {
    //  implicit val toViewExtended = code.model.toViewExtended(_)
    //  implicit val toBankAccountExtended = code.model.toBankAccountExtended(_)
    //  val callContext: Option[CallContext] = Some(CallContext())
    //  val bankId = BankId(request.bankId)
    //  val accountId = AccountId(request.accountId)
    //  for {
    //    (user, _) <- NewStyle.function.findByUserId(request.userId, callContext)
    //    (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, accountId, callContext)
    //    (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
    //    view <- ViewNewStyle.checkOwnerViewAccessAndReturnOwnerView(user, BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
    //    (Full(transactionsCore), callContext) <- bankAccount.getModeratedTransactionsCore(bank, Full(user), view, BankIdAccountId(bankId, accountId), Nil, callContext)
    //    obpCoreTransactions: CoreTransactionsJsonV300 = code.api.v3_0_0.JSONFactory300.createCoreTransactionsJSON(transactionsCore.map(ModeratedTransactionCoreWithAttributes(_)))
    //  } yield {
    //    val jValue = Extraction.decompose(obpCoreTransactions)
    //    val coreTransactionsJsonV300Grpc = jValue.extract[CoreTransactionsJsonV300Grpc]
    //    coreTransactionsJsonV300Grpc
    //  }
    //}
  }
}
