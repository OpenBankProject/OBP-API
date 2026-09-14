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

package code.obp.grpc.logcache.api

import com.google.protobuf.DescriptorProtos._
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.{Label, Type}

/**
 * Proto file descriptor for the log cache streaming service.
 * Built programmatically to support gRPC reflection (service discovery).
 */
object LogCacheProto {

  lazy val javaDescriptor: com.google.protobuf.Descriptors.FileDescriptor = {
    val fileProto = FileDescriptorProto.newBuilder()
      .setName("log_cache.proto")
      .setPackage("code.obp.grpc.logcache.g1")
      .setSyntax("proto3")
      .addDependency("google/protobuf/timestamp.proto")
      // LogLevel enum
      .addEnumType(EnumDescriptorProto.newBuilder()
        .setName("LogLevel")
        .addValue(EnumValueDescriptorProto.newBuilder().setName("LOG_LEVEL_UNSPECIFIED").setNumber(0))
        .addValue(EnumValueDescriptorProto.newBuilder().setName("TRACE").setNumber(1))
        .addValue(EnumValueDescriptorProto.newBuilder().setName("DEBUG").setNumber(2))
        .addValue(EnumValueDescriptorProto.newBuilder().setName("INFO").setNumber(3))
        .addValue(EnumValueDescriptorProto.newBuilder().setName("WARNING").setNumber(4))
        .addValue(EnumValueDescriptorProto.newBuilder().setName("ERROR").setNumber(5))
        .addValue(EnumValueDescriptorProto.newBuilder().setName("ALL").setNumber(6))
      )
      // StreamLogCacheRequest
      .addMessageType(DescriptorProto.newBuilder()
        .setName("StreamLogCacheRequest")
        .addField(enumField("level", 1, ".code.obp.grpc.logcache.g1.LogLevel"))
      )
      // LogCacheEntry
      .addMessageType(DescriptorProto.newBuilder()
        .setName("LogCacheEntry")
        .addField(enumField("level", 1, ".code.obp.grpc.logcache.g1.LogLevel"))
        .addField(stringField("message", 2))
        .addField(messageField("timestamp", 3, ".google.protobuf.Timestamp"))
        .addField(stringField("api_instance_id", 4))
      )
      // LogCacheStreamService
      .addService(ServiceDescriptorProto.newBuilder()
        .setName("LogCacheStreamService")
        .addMethod(MethodDescriptorProto.newBuilder()
          .setName("StreamLogCacheEntries")
          .setInputType(".code.obp.grpc.logcache.g1.StreamLogCacheRequest")
          .setOutputType(".code.obp.grpc.logcache.g1.LogCacheEntry")
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

  private def enumField(name: String, number: Int, typeName: String): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_ENUM)
      .setTypeName(typeName)
      .setLabel(Label.LABEL_OPTIONAL)

  private def messageField(name: String, number: Int, typeName: String): FieldDescriptorProto.Builder =
    FieldDescriptorProto.newBuilder()
      .setName(name).setNumber(number)
      .setType(Type.TYPE_MESSAGE)
      .setTypeName(typeName)
      .setLabel(Label.LABEL_OPTIONAL)
}
