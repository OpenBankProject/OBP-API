package code.obp.grpc

import org.json4s._
import scala.language.existentials
import scala.language.reflectiveCalls
import code.api.util.newstyle.ViewNewStyle
import code.api.util.{APIUtil, CallContext, NewStyle}
import code.api.v3_0_0.{CoreTransactionsJsonV300, ModeratedTransactionCoreWithAttributes}
import code.api.v4_0_0.{BankJson400, BanksJson400, JSONFactory400}
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
import scala.util.control.NonFatal

/**
 * OBP gRPC server — serves banking RPCs (ObpService), chat streaming RPCs (ChatStreamService)
 * and signal channel RPCs (SignalChannelsService: publish/fetch/list plus a live Subscribe stream).
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

// The port is a constructor parameter defaulting to the configured one, so a test can pass 0 and
// let the OS choose. Shards run as parallel JVMs, and two of them starting this server on the same
// port aborts one run with a BindException. Read boundPort afterwards - asking for a free port,
// closing it, then binding leaves a window for another process to take it.
class ObpGrpcServer(executionContext: ExecutionContext, port: Int = ObpGrpcServer.port) extends MdcLoggable { self =>
  private[this] var server: Server = null
  // Recorded at start rather than read back off the server: stop() nulls the field, and for a
  // server given port 0 the constructor argument it would fall back to is 0. @volatile because
  // start() runs on one thread and callers read this from another.
  @volatile private[this] var actualPort: Int = port
  // Which of the process-wide buses this instance actually started. Each is an object holding one
  // subscriber connection, start() is a no-op once it is running, and stop() was not - so a second
  // server's stop() closed the connection the first one was still serving from.
  // @volatile for the same reason as actualPort: stop() also runs on the JVM's shutdown-hook
  // thread, which never synchronised with the thread that ran start().
  @volatile private[this] var startedChatBus = false
  @volatile private[this] var startedLogCacheBus = false
  @volatile private[this] var startedMetricsBus = false
  @volatile private[this] var startedSignalBus = false
  @volatile private[this] var shutdownHook: scala.sys.ShutdownHookThread = null

  def start(): Unit = {
    // A second start() on the same instance would recompute the ownership flags against buses it
    // had already started - reading them as someone else's - overwrite `server`, leaking the first
    // one with its port still bound, and overwrite shutdownHook, orphaning the first with no
    // reference left to remove it. Guarded rather than made to restart: the buses guard the same
    // way, and nothing here has a use for a second server on one instance.
    if (server != null) {
      logger.warn(s"gRPC server is already started on port $actualPort; ignoring this start()")
      return
    }

    // The guard above keys off `server`, which is set last, so a start that fails partway - a
    // BindException is the ordinary case - leaves it null and lets a retry back in. Without the
    // rollback below, that retry would find the buses this instance started already running,
    // record them as somebody else's, and leave them up for the life of the process.
    try startInternal() catch {
      case NonFatal(e) =>
        // Suppressed rather than swallowed or thrown in its place: a failure while tearing down
        // must not replace the start failure that is the reason anyone is reading this.
        try stop() catch { case NonFatal(cleanupFailure) => e.addSuppressed(cleanupFailure) }
        throw e
    }
  }

  private def startInternal(): Unit = {
    // Ownership is read after each start() rather than before: a disabled bus makes start() a
    // no-op, and "was not running beforehand" alone would claim one this instance never started.

    // Start chat event bus for Redis pub/sub streaming
    val chatWasRunning = code.chat.ChatEventBus.isRunning
    code.chat.ChatEventBus.start()
    startedChatBus = !chatWasRunning && code.chat.ChatEventBus.isRunning

    // Start log cache event bus (no-op if grpc.log_cache_stream.enabled=false)
    val logCacheWasRunning = code.logcache.LogCacheEventBus.isRunning
    code.logcache.LogCacheEventBus.start()
    startedLogCacheBus = !logCacheWasRunning && code.logcache.LogCacheEventBus.isRunning

    // Start signal event bus for the SignalChannelsService Subscribe stream
    val signalWasRunning = code.signal.SignalEventBus.isRunning
    code.signal.SignalEventBus.start()
    startedSignalBus = !signalWasRunning && code.signal.SignalEventBus.isRunning

    // Start metrics event bus (no-op if grpc.metrics_stream.enabled=false)
    val metricsWasRunning = code.metricsstream.MetricsEventBus.isRunning
    code.metricsstream.MetricsEventBus.start()
    startedMetricsBus = !metricsWasRunning && code.metricsstream.MetricsEventBus.isRunning

    val baseBuilder = ServerBuilder.forPort(port)
      .addService(ObpServiceGrpc.bindService(ObpServiceImpl, executionContext))
      .addService(code.obp.grpc.chat.api.ChatStreamServiceGrpc.bindService(
        code.obp.grpc.chat.ChatStreamServiceImpl, executionContext))
      .addService(code.obp.grpc.signal.api.SignalChannelsServiceGrpc.bindService(
        code.obp.grpc.signal.SignalChannelsServiceImpl, executionContext))
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
    actualPort = server.getPort
    logger.info("Server started, listening on " + actualPort)
    // Kept so stop() can take it down again: without that, every server ever started leaves a hook
    // behind, and each one calls stop() on an instance that has usually stopped already.
    shutdownHook = sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  /** The port actually bound, which differs from the requested one when 0 was asked for. */
  def boundPort: Int = actualPort

  def stop(): Unit = {
    if (startedChatBus) { code.chat.ChatEventBus.stop(); startedChatBus = false }
    if (startedLogCacheBus) { code.logcache.LogCacheEventBus.stop(); startedLogCacheBus = false }
    if (startedMetricsBus) { code.metricsstream.MetricsEventBus.stop(); startedMetricsBus = false }
    if (startedSignalBus) { code.signal.SignalEventBus.stop(); startedSignalBus = false }
    if (server != null) {
      server.shutdown()
      server = null
    }
    if (shutdownHook != null) {
      // remove() throws once the JVM is already shutting down, which is exactly when the hook runs.
      try shutdownHook.remove() catch { case _: IllegalStateException => () }
      shutdownHook = null
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
            // This used to destructure with `val BankJson400(..., None) = bank`, a refutable
            // pattern in a val definition: any bank whose attributes are Some - Some(List())
            // included - threw a MatchError, which the client saw as INTERNAL. The attributes are
            // not carried over the wire anyway, so read the fields instead of matching on them.
            val bankRoutingGrpcs = bank.bank_routings.map(routings => BankRoutingJsonV121Grpc(routings.scheme, routings.address))
            // protobuf string fields reject null, and logo and website are both nullable here.
            def orEmpty(value: String): String = Option(value).getOrElse("")
            BankJson400Grpc(
              bank.id, bank.short_name, bank.full_name,
              orEmpty(bank.logo), orEmpty(bank.website), bankRoutingGrpcs)
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
    //    val jValue = Http4s200.Implementations2_0_0.processAccounts(privateViewsUserCanAccessAtOneBank, availablePrivateAccounts)
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
