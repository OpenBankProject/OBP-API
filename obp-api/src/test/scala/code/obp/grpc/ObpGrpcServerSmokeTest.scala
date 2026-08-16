package code.obp.grpc

import code.bankconnectors.Connector
import code.chat.ChatEventBus
import code.obp.grpc.api.ObpServiceGrpc
import code.setup.ServerSetupWithTestData
import com.google.protobuf.empty.Empty
import io.grpc.stub.MetadataUtils
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata, StatusRuntimeException}
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * A connectivity smoke test for the gRPC server.
 *
 * Nothing under src/test referenced grpc at all before this, and no shard's test_filter names the
 * package, so a running production path had no coverage whatsoever. That matters on its own, and it
 * matters specifically for the scalapb upgrade: everything under code/obp/grpc/api is generated code
 * checked into the repository, and regenerating it against a new scalapb needs to be verifiable by
 * something other than "it still compiles".
 *
 * One authenticated request over a real socket covers that. It exercises the server build, the
 * service binding, the auth interceptor, the generated stub, protobuf serialisation on the way out
 * and deserialisation on the way back.
 *
 * The package is not in any shard's test_filter, so this is picked up by the catch-all (shard 8 in
 * CI, shard 4 locally). Confirm with the "Catch-all extras" line in that shard's log if you need to
 * know it actually ran.
 */
class ObpGrpcServerSmokeTest extends ServerSetupWithTestData {

  object GrpcSmoke extends Tag("GrpcSmoke")

  private var grpcServer: ObpGrpcServer = _
  private var channel: ManagedChannel = _
  private var grpcPort: Int = _

  /**
   * Port 0, and the bound port read back afterwards. Shards run as parallel JVMs, and two of them
   * starting this server on the same port aborts one of the runs with a BindException - which is
   * what happens with the configured default, and why every other server-starting suite here takes
   * its port from the shard rather than from a global.
   *
   * Not "open a socket on 0, read the port, close it, then bind": between the close and the bind
   * another process can take it, which is the flaw of that idiom rather than a fix for it.
   */
  override def beforeAll(): Unit = {
    super.beforeAll()
    grpcServer = new ObpGrpcServer(scala.concurrent.ExecutionContext.global, port = 0)
    grpcServer.start()
    grpcPort = grpcServer.boundPort
    channel = ManagedChannelBuilder
      .forAddress("localhost", grpcPort)
      .usePlaintext()
      .asInstanceOf[ManagedChannelBuilder[_]]
      .build()
  }

  override def afterAll(): Unit = {
    if (channel != null) channel.shutdownNow()
    if (grpcServer != null) grpcServer.stop()
    super.afterAll()
  }

  /** The interceptor reads the same Authorization header the REST endpoints take. */
  private def authenticatedStub: ObpServiceGrpc.ObpServiceBlockingStub = {
    val token = user1.map(_._2.value).getOrElse(fail("no DirectLogin token for user1"))
    val metadata = new Metadata()
    metadata.put(
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
      s"""DirectLogin token="$token""""
    )
    ObpServiceGrpc.blockingStub(channel)
      .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
  }

  Feature("The gRPC server answers over a real connection") {

    Scenario("getBanks returns the banks the connector returns", GrpcSmoke) {
      val viaGrpc = authenticatedStub.getBanks(Empty.defaultInstance)

      val viaConnector = Await.result(Connector.connector.vend.getBanks(None), 30.seconds)
        .map(_._1.map(_.bankId.value))
        .getOrElse(Nil)

      viaGrpc.banks.map(_.id).sorted should equal(viaConnector.sorted)
      viaGrpc.banks should not be empty
      // A field other than the key, so the check covers more than the message arriving at all.
      viaGrpc.banks.map(_.fullName).exists(_.nonEmpty) should equal(true)
    }

    Scenario("the bound port is still reportable after the server stops", GrpcSmoke) {
      // boundPort read server.getPort and fell back to the constructor argument once stop() nulled
      // the field - which is 0 for a server given an ephemeral port, so teardown logging and any
      // reconnect would see 0 rather than where it had been listening.
      //
      // try/finally, because a failure of the first assertion would otherwise leave this server
      // bound for the life of the JVM - beforeAll's server is the only one afterAll knows about.
      val server = new ObpGrpcServer(scala.concurrent.ExecutionContext.global, port = 0)
      server.start()
      try {
        val whileRunning = server.boundPort
        whileRunning should not equal 0

        server.stop()
        server.boundPort should equal(whileRunning)
      } finally {
        server.stop()
      }
    }

    Scenario("stopping one server leaves another server's event buses alone", GrpcSmoke) {
      // start() starts ChatEventBus and, when enabled, the log-cache and metrics buses. All three
      // are objects holding one subscriber connection for the process, and start() is a no-op once
      // one is running - but stop() was not: it punsubscribed and closed that shared connection
      // whoever called it. So a second server's stop() silently cut the pub/sub out from under the
      // server this suite started in beforeAll, which is still serving.
      ChatEventBus.isRunning should equal(true)

      val second = new ObpGrpcServer(scala.concurrent.ExecutionContext.global, port = 0)
      second.start()
      second.stop()

      withClue("the second server stopped a bus it had joined rather than started: ") {
        ChatEventBus.isRunning should equal(true)
      }
    }

    Scenario("a call with no credentials is rejected", GrpcSmoke) {
      // AuthInterceptor had no coverage either, and this is the branch that decides whether the
      // server is open to the world.
      val thrown = intercept[StatusRuntimeException] {
        ObpServiceGrpc.blockingStub(channel).getBanks(Empty.defaultInstance)
      }
      thrown.getStatus.getCode should equal(io.grpc.Status.Code.UNAUTHENTICATED)
    }
  }
}
