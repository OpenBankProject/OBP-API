/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.bankconnectors.grpc

import org.json4s._
import code.api.util.APIUtil
import code.api.util.ErrorMessages.AdapterUnknownError
import code.bankconnectors.Connector
import code.bankconnectors.grpc.api.{ObpConnectorRequest, ObpConnectorServiceGrpc}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import net.liftweb.common.{Box, Empty}
import org.json4s.native.Serialization.write

import java.io.File
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * gRPC utils for the connector.
 * Manages the ManagedChannel lifecycle and executes gRPC calls.
 * The reason for extracting this util: if not using the gRPC connector,
 * the gRPC channel will not be initialized.
 */
object GrpcUtils extends MdcLoggable {

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  val host: String = APIUtil.getPropsValue("grpc_connector.host", "localhost")
  val port: Int = APIUtil.getPropsAsIntValue("grpc_connector.port", 50051)
  val deadlineMs: Long = APIUtil.getPropsAsLongValue("grpc_connector.deadline_ms", 30000L)
  val useTls: Boolean = APIUtil.getPropsAsBoolValue("grpc_connector.use_tls", false)

  lazy val channel: io.grpc.ManagedChannel = {
    val builder = NettyChannelBuilder.forAddress(host, port)
    if (useTls) {
      val sslBuilder = SslContextBuilder.forClient()
      val trustCertFile = APIUtil.getPropsValue("grpc_connector.tls.trust_cert_collection_file", "")
      if (trustCertFile.nonEmpty) sslBuilder.trustManager(new File(trustCertFile))
      val clientCertFile = APIUtil.getPropsValue("grpc_connector.tls.client_cert_chain_file", "")
      val clientKeyFile = APIUtil.getPropsValue("grpc_connector.tls.client_private_key_file", "")
      if (clientCertFile.nonEmpty && clientKeyFile.nonEmpty) {
        sslBuilder.keyManager(new File(clientCertFile), new File(clientKeyFile))
      }
      builder.sslContext(GrpcSslContexts.configure(sslBuilder).build())
    } else {
      builder.usePlaintext()
    }
    val ch = builder.build()
    logger.info(s"gRPC channel created: $host:$port (tls=$useTls)")
    // Register shutdown hook for clean teardown
    sys.addShutdownHook {
      logger.info("Shutting down gRPC channel...")
      ch.shutdown()
      if (!ch.awaitTermination(5, TimeUnit.SECONDS)) {
        ch.shutdownNow()
      }
    }
    ch
  }

  lazy val blockingStub: ObpConnectorServiceGrpc.ObpConnectorServiceBlockingStub =
    ObpConnectorServiceGrpc.blockingStub(channel)

  def sendRequest[T: Manifest](processName: String, outBound: TopicTrait): Future[Box[T]] = {
    val outBoundJson: String = write(outBound)
    logger.debug(s"${GrpcConnector_vFeb2026.toString} outBoundJson: $processName = $outBoundJson")

    Future {
      val request = ObpConnectorRequest(methodName = processName, jsonPayload = outBoundJson)
      val response = blockingStub.processObpRequest(request)
      response.jsonPayload
    }.map { responseJson =>
      logger.debug(s"${GrpcConnector_vFeb2026.toString} inBoundJson: $processName = $responseJson")
      Connector.extractAdapterResponse[T](responseJson, Empty)
    }
  }
}
