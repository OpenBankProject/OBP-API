// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class PublishResponse(
    messageId: _root_.scala.Predef.String = "",
    channelName: _root_.scala.Predef.String = "",
    timestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp] = _root_.scala.None,
    channelMessageCount: _root_.scala.Long = 0L,
    sequence: _root_.scala.Long = 0L
    ) extends scalapb.GeneratedMessage with scalapb.Message[PublishResponse] with scalapb.lenses.Updatable[PublishResponse] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (messageId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, messageId) }
      if (channelName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, channelName) }
      if (timestamp.isDefined) {
        val __v = timestamp.get
        val __s = __v.serializedSize
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__s) + __s
      }
      if (channelMessageCount != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(4, channelMessageCount) }
      if (sequence != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(5, sequence) }
      __size
    }
    final override def serializedSize: _root_.scala.Int = {
      var read = __serializedSizeCachedValue
      if (read == 0) {
        read = __computeSerializedValue()
        __serializedSizeCachedValue = read
      }
      read
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      { val __v = messageId; if (__v != "") _output__.writeString(1, __v) };
      { val __v = channelName; if (__v != "") _output__.writeString(2, __v) };
      timestamp.foreach { __v =>
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__v.serializedSize)
        __v.writeTo(_output__)
      };
      { val __v = channelMessageCount; if (__v != 0L) _output__.writeInt64(4, __v) };
      { val __v = sequence; if (__v != 0L) _output__.writeInt64(5, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.PublishResponse = {
      var __messageId = this.messageId
      var __channelName = this.channelName
      var __timestamp = this.timestamp
      var __channelMessageCount = this.channelMessageCount
      var __sequence = this.sequence
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __messageId = _input__.readString()
          case 18 =>
            __channelName = _input__.readString()
          case 26 =>
            __timestamp = Some(_root_.scalapb.LiteParser.readMessage(_input__, __timestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)))
          case 32 =>
            __channelMessageCount = _input__.readInt64()
          case 40 =>
            __sequence = _input__.readInt64()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.PublishResponse(
          messageId = __messageId,
          channelName = __channelName,
          timestamp = __timestamp,
          channelMessageCount = __channelMessageCount,
          sequence = __sequence
      )
    }
    def withMessageId(__v: _root_.scala.Predef.String): PublishResponse = copy(messageId = __v)
    def withChannelName(__v: _root_.scala.Predef.String): PublishResponse = copy(channelName = __v)
    def getTimestamp: com.google.protobuf.timestamp.Timestamp = timestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)
    def clearTimestamp: PublishResponse = copy(timestamp = _root_.scala.None)
    def withTimestamp(__v: com.google.protobuf.timestamp.Timestamp): PublishResponse = copy(timestamp = Some(__v))
    def withChannelMessageCount(__v: _root_.scala.Long): PublishResponse = copy(channelMessageCount = __v)
    def withSequence(__v: _root_.scala.Long): PublishResponse = copy(sequence = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = messageId
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = channelName
          if (__t != "") __t else null
        }
        case 3 => timestamp.orNull
        case 4 => {
          val __t = channelMessageCount
          if (__t != 0L) __t else null
        }
        case 5 => {
          val __t = sequence
          if (__t != 0L) __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(messageId)
        case 2 => _root_.scalapb.descriptors.PString(channelName)
        case 3 => timestamp.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        case 4 => _root_.scalapb.descriptors.PLong(channelMessageCount)
        case 5 => _root_.scalapb.descriptors.PLong(sequence)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.PublishResponse
}

object PublishResponse extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.PublishResponse] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.PublishResponse] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.PublishResponse = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.PublishResponse(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.get(__fields.get(2)).asInstanceOf[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]],
      __fieldsMap.getOrElse(__fields.get(3), 0L).asInstanceOf[_root_.scala.Long],
      __fieldsMap.getOrElse(__fields.get(4), 0L).asInstanceOf[_root_.scala.Long]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.PublishResponse] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.PublishResponse(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]]),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Long]).getOrElse(0L),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Long]).getOrElse(0L)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(3)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 3 => __out = com.google.protobuf.timestamp.Timestamp
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.PublishResponse(
  )
  implicit class PublishResponseLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.PublishResponse]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.PublishResponse](_l) {
    def messageId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.messageId)((c_, f_) => c_.copy(messageId = f_))
    def channelName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.channelName)((c_, f_) => c_.copy(channelName = f_))
    def timestamp: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.timestamp.Timestamp] = field(_.getTimestamp)((c_, f_) => c_.copy(timestamp = Some(f_)))
    def optionalTimestamp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]] = field(_.timestamp)((c_, f_) => c_.copy(timestamp = f_))
    def channelMessageCount: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.channelMessageCount)((c_, f_) => c_.copy(channelMessageCount = f_))
    def sequence: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.sequence)((c_, f_) => c_.copy(sequence = f_))
  }
  final val MESSAGE_ID_FIELD_NUMBER = 1
  final val CHANNEL_NAME_FIELD_NUMBER = 2
  final val TIMESTAMP_FIELD_NUMBER = 3
  final val CHANNEL_MESSAGE_COUNT_FIELD_NUMBER = 4
  final val SEQUENCE_FIELD_NUMBER = 5
}
