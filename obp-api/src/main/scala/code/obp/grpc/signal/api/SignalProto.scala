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

package code.obp.grpc.signal.api

import com.google.protobuf.DescriptorProtos._
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.{Label, Type}

/**
 * Proto file descriptor for the signal channels service, built programmatically
 * so gRPC reflection (service discovery) works without a protoc plugin in the
 * Maven build. Must stay in step with obp-api/src/main/protobuf/signal.proto:
 * message order here is the index the companions use in `javaDescriptor`.
 */
object SignalProto {

  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val fileProto = FileDescriptorProto.newBuilder()
      .setName("signal.proto")
      .setPackage("code.obp.grpc.signal.g1")
      .setSyntax("proto3")
      .addDependency("google/protobuf/timestamp.proto")
      // 0: SignalMessage
      .addMessageType(DescriptorProto.newBuilder()
        .setName("SignalMessage")
        .addField(stringField("message_id", 1))
        .addField(stringField("channel_name", 2))
        .addField(stringField("sender_consumer_id", 3))
        .addField(stringField("sender_user_id", 4))
        .addField(stringField("to_user_id", 5))
        .addField(messageField("timestamp", 6, ".google.protobuf.Timestamp"))
        .addField(stringField("message_type", 7))
        .addField(stringField("payload_json", 8))
        .addField(int64Field("sequence", 9))
      )
      // 1: SignalChannelInfo
      .addMessageType(DescriptorProto.newBuilder()
        .setName("SignalChannelInfo")
        .addField(stringField("channel_name", 1))
        .addField(int64Field("message_count", 2))
        .addField(int64Field("ttl_seconds", 3))
      )
      // 2: PublishRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("PublishRequest")
        .addField(stringField("channel_name", 1))
        .addField(stringField("to_user_id", 2))
        .addField(stringField("message_type", 3))
        .addField(stringField("payload_json", 4))
      )
      // 3: PublishResponse
      .addMessageType(DescriptorProto.newBuilder()
        .setName("PublishResponse")
        .addField(stringField("message_id", 1))
        .addField(stringField("channel_name", 2))
        .addField(messageField("timestamp", 3, ".google.protobuf.Timestamp"))
        .addField(int64Field("channel_message_count", 4))
        .addField(int64Field("sequence", 5))
      )
      // 4: FetchRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("FetchRequest")
        .addField(stringField("channel_name", 1))
        .addField(int32Field("offset", 2))
        .addField(int32Field("limit", 3))
        .addField(int64Field("after_sequence", 4))
      )
      // 5: FetchResponse
      .addMessageType(DescriptorProto.newBuilder()
        .setName("FetchResponse")
        .addField(stringField("channel_name", 1))
        .addField(repeatedMessageField("messages", 2, ".code.obp.grpc.signal.g1.SignalMessage"))
        .addField(int64Field("total_count", 3))
        .addField(boolField("has_more", 4))
        .addField(int64Field("latest_sequence", 5))
        .addField(int64Field("next_after_sequence", 6))
        .addField(int64Field("visible_count", 7))
      )
      // 6: ListChannelsRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("ListChannelsRequest")
      )
      // 7: ListChannelsResponse
      .addMessageType(DescriptorProto.newBuilder()
        .setName("ListChannelsResponse")
        .addField(repeatedMessageField("channels", 1, ".code.obp.grpc.signal.g1.SignalChannelInfo"))
      )
      // 8: SubscribeRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("SubscribeRequest")
        .addField(stringField("channel_name", 1))
      )
      // SignalChannelsService
      .addService(ServiceDescriptorProto.newBuilder()
        .setName("SignalChannelsService")
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("Publish")
          .setInputType(".code.obp.grpc.signal.g1.PublishRequest")
          .setOutputType(".code.obp.grpc.signal.g1.PublishResponse")
        )
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("Fetch")
          .setInputType(".code.obp.grpc.signal.g1.FetchRequest")
          .setOutputType(".code.obp.grpc.signal.g1.FetchResponse")
        )
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("ListChannels")
          .setInputType(".code.obp.grpc.signal.g1.ListChannelsRequest")
          .setOutputType(".code.obp.grpc.signal.g1.ListChannelsResponse")
        )
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("Subscribe")
          .setInputType(".code.obp.grpc.signal.g1.SubscribeRequest")
          .setOutputType(".code.obp.grpc.signal.g1.SignalMessage")
          .setServerStreaming(true)
        )
      )
      .build()

    com.google.protobuf.Descriptors.FileDescriptor.buildFrom(
      fileProto,
      Array(com.google.protobuf.TimestampProto.getDescriptor)
    )
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

  private def boolField(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_BOOL)
      .setLabel(Label.LABEL_OPTIONAL)

  private def messageField(name: String, number: Int, typeName: String): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(typeName)
      .setLabel(Label.LABEL_OPTIONAL)

  private def repeatedMessageField(name: String, number: Int, typeName: String): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(typeName)
      .setLabel(Label.LABEL_REPEATED)
}
