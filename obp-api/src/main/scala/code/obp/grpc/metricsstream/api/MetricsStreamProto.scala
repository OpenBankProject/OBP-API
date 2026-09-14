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

package code.obp.grpc.metricsstream.api

import com.google.protobuf.DescriptorProtos._
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.{Label, Type}

/**
 * Proto file descriptor for the metrics streaming service.
 * Built programmatically to support gRPC reflection (service discovery).
 */
object MetricsStreamProto {

  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val fileProto = FileDescriptorProto.newBuilder()
      .setName("metrics_stream.proto")
      .setPackage("code.obp.grpc.metricsstream.g1")
      .setSyntax("proto3")
      // StreamMetricsRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("StreamMetricsRequest")
        .addField(stringField("consumer_id", 1))
        .addField(stringField("user_id", 2))
        .addField(stringField("verb", 3))
        .addField(stringField("url_substring", 4))
        .addField(stringField("implemented_by_partial_function", 5))
        .addField(stringField("app_name", 6))
        .addField(stringField("consent_reference_id", 7))
      )
      // MetricEvent
      .addMessageType(DescriptorProto.newBuilder()
        .setName("MetricEvent")
        .addField(stringField("url", 1))
        .addField(stringField("date", 2))
        .addField(int64Field("duration", 3))
        .addField(stringField("user_id", 4))
        .addField(stringField("username", 5))
        .addField(stringField("app_name", 6))
        .addField(stringField("developer_email", 7))
        .addField(stringField("consumer_id", 8))
        .addField(stringField("implemented_by_partial_function", 9))
        .addField(stringField("implemented_in_version", 10))
        .addField(stringField("verb", 11))
        .addField(int32Field("status_code", 12))
        .addField(stringField("correlation_id", 13))
        .addField(stringField("source_ip", 14))
        .addField(stringField("target_ip", 15))
        .addField(stringField("api_instance_id", 16))
        .addField(stringField("operation_id", 17))
        .addField(stringField("consent_reference_id", 18))
      )
      // MetricsStreamService
      .addService(ServiceDescriptorProto.newBuilder()
        .setName("MetricsStreamService")
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("StreamMetrics")
          .setInputType(".code.obp.grpc.metricsstream.g1.StreamMetricsRequest")
          .setOutputType(".code.obp.grpc.metricsstream.g1.MetricEvent")
          .setServerStreaming(true)
        )
      )
      .build()

    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(fileProto, Array.empty)
  }

  private def stringField(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_STRING)
      .setLabel(Label.LABEL_OPTIONAL)

  private def int32Field(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_INT32)
      .setLabel(Label.LABEL_OPTIONAL)

  private def int64Field(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_INT64)
      .setLabel(Label.LABEL_OPTIONAL)
}
