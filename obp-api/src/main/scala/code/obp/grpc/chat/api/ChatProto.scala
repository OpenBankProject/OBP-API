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

package code.obp.grpc.chat.api

import com.google.protobuf.DescriptorProtos._
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.{Label, Type}

/**
 * Proto file descriptor for the chat streaming service.
 * Built programmatically to support gRPC reflection (service discovery).
 */
object ChatProto {

  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val fileProto = FileDescriptorProto.newBuilder()
      .setName("chat.proto")
      .setPackage("code.obp.grpc.chat.g1")
      .setSyntax("proto3")
      .addDependency("google/protobuf/timestamp.proto")
      // StreamMessagesRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("StreamMessagesRequest")
        .addField(stringField("chat_room_id", 1))
      )
      // ChatMessageEvent
      .addMessageType(DescriptorProto.newBuilder()
        .setName("ChatMessageEvent")
        .addField(stringField("event_type", 1))
        .addField(stringField("chat_message_id", 2))
        .addField(stringField("chat_room_id", 3))
        .addField(stringField("sender_user_id", 4))
        .addField(stringField("sender_consumer_id", 5))
        .addField(stringField("sender_username", 6))
        .addField(stringField("sender_provider", 7))
        .addField(stringField("sender_consumer_name", 8))
        .addField(stringField("content", 9))
        .addField(stringField("message_type", 10))
        .addField(repeatedStringField("mentioned_user_ids", 11))
        .addField(stringField("reply_to_message_id", 12))
        .addField(stringField("thread_id", 13))
        .addField(boolField("is_deleted", 14))
        .addField(messageField("created_at", 15, ".google.protobuf.Timestamp"))
        .addField(messageField("updated_at", 16, ".google.protobuf.Timestamp"))
      )
      // TypingEvent
      .addMessageType(DescriptorProto.newBuilder()
        .setName("TypingEvent")
        .addField(stringField("chat_room_id", 1))
        .addField(boolField("is_typing", 2))
      )
      // TypingIndicator
      .addMessageType(DescriptorProto.newBuilder()
        .setName("TypingIndicator")
        .addField(stringField("chat_room_id", 1))
        .addField(stringField("user_id", 2))
        .addField(stringField("username", 3))
        .addField(stringField("provider", 4))
        .addField(boolField("is_typing", 5))
      )
      // StreamPresenceRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("StreamPresenceRequest")
        .addField(stringField("chat_room_id", 1))
      )
      // PresenceEvent
      .addMessageType(DescriptorProto.newBuilder()
        .setName("PresenceEvent")
        .addField(stringField("user_id", 1))
        .addField(stringField("username", 2))
        .addField(stringField("provider", 3))
        .addField(boolField("is_online", 4))
      )
      // StreamUnreadCountsRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("StreamUnreadCountsRequest")
      )
      // UnreadCountEvent
      .addMessageType(DescriptorProto.newBuilder()
        .setName("UnreadCountEvent")
        .addField(stringField("chat_room_id", 1))
        .addField(int64Field("unread_count", 2))
      )
      // ChatStreamService
      .addService(ServiceDescriptorProto.newBuilder()
        .setName("ChatStreamService")
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("StreamMessages")
          .setInputType(".code.obp.grpc.chat.g1.StreamMessagesRequest")
          .setOutputType(".code.obp.grpc.chat.g1.ChatMessageEvent")
          .setServerStreaming(true)
        )
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("StreamTyping")
          .setInputType(".code.obp.grpc.chat.g1.TypingEvent")
          .setOutputType(".code.obp.grpc.chat.g1.TypingIndicator")
          .setClientStreaming(true)
          .setServerStreaming(true)
        )
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("StreamPresence")
          .setInputType(".code.obp.grpc.chat.g1.StreamPresenceRequest")
          .setOutputType(".code.obp.grpc.chat.g1.PresenceEvent")
          .setServerStreaming(true)
        )
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("StreamUnreadCounts")
          .setInputType(".code.obp.grpc.chat.g1.StreamUnreadCountsRequest")
          .setOutputType(".code.obp.grpc.chat.g1.UnreadCountEvent")
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

  private def repeatedStringField(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_STRING)
      .setLabel(Label.LABEL_REPEATED)

  private def boolField(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_BOOL)
      .setLabel(Label.LABEL_OPTIONAL)

  private def int64Field(name: String, number: Int): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_INT64)
      .setLabel(Label.LABEL_OPTIONAL)

  private def messageField(name: String, number: Int, typeName: String): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(typeName)
      .setLabel(Label.LABEL_OPTIONAL)
}
