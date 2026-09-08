// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class SignalMessage(
    messageId: _root_.scala.Predef.String = "",
    channelName: _root_.scala.Predef.String = "",
    senderConsumerId: _root_.scala.Predef.String = "",
    senderUserId: _root_.scala.Predef.String = "",
    toUserId: _root_.scala.Predef.String = "",
    timestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp] = _root_.scala.None,
    messageType: _root_.scala.Predef.String = "",
    payloadJson: _root_.scala.Predef.String = "",
    sequence: _root_.scala.Long = 0L
    ) extends scalapb.GeneratedMessage with scalapb.Message[SignalMessage] with scalapb.lenses.Updatable[SignalMessage] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (messageId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, messageId) }
      if (channelName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, channelName) }
      if (senderConsumerId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, senderConsumerId) }
      if (senderUserId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, senderUserId) }
      if (toUserId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(5, toUserId) }
      if (timestamp.isDefined) {
        val __v = timestamp.get
        val __s = __v.serializedSize
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__s) + __s
      }
      if (messageType != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(7, messageType) }
      if (payloadJson != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(8, payloadJson) }
      if (sequence != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(9, sequence) }
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
      { val __v = senderConsumerId; if (__v != "") _output__.writeString(3, __v) };
      { val __v = senderUserId; if (__v != "") _output__.writeString(4, __v) };
      { val __v = toUserId; if (__v != "") _output__.writeString(5, __v) };
      timestamp.foreach { __v =>
        _output__.writeTag(6, 2)
        _output__.writeUInt32NoTag(__v.serializedSize)
        __v.writeTo(_output__)
      };
      { val __v = messageType; if (__v != "") _output__.writeString(7, __v) };
      { val __v = payloadJson; if (__v != "") _output__.writeString(8, __v) };
      { val __v = sequence; if (__v != 0L) _output__.writeInt64(9, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.SignalMessage = {
      var __messageId = this.messageId
      var __channelName = this.channelName
      var __senderConsumerId = this.senderConsumerId
      var __senderUserId = this.senderUserId
      var __toUserId = this.toUserId
      var __timestamp = this.timestamp
      var __messageType = this.messageType
      var __payloadJson = this.payloadJson
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
            __senderConsumerId = _input__.readString()
          case 34 =>
            __senderUserId = _input__.readString()
          case 42 =>
            __toUserId = _input__.readString()
          case 50 =>
            __timestamp = Some(_root_.scalapb.LiteParser.readMessage(_input__, __timestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)))
          case 58 =>
            __messageType = _input__.readString()
          case 66 =>
            __payloadJson = _input__.readString()
          case 72 =>
            __sequence = _input__.readInt64()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.SignalMessage(
          messageId = __messageId,
          channelName = __channelName,
          senderConsumerId = __senderConsumerId,
          senderUserId = __senderUserId,
          toUserId = __toUserId,
          timestamp = __timestamp,
          messageType = __messageType,
          payloadJson = __payloadJson,
          sequence = __sequence
      )
    }
    def withMessageId(__v: _root_.scala.Predef.String): SignalMessage = copy(messageId = __v)
    def withChannelName(__v: _root_.scala.Predef.String): SignalMessage = copy(channelName = __v)
    def withSenderConsumerId(__v: _root_.scala.Predef.String): SignalMessage = copy(senderConsumerId = __v)
    def withSenderUserId(__v: _root_.scala.Predef.String): SignalMessage = copy(senderUserId = __v)
    def withToUserId(__v: _root_.scala.Predef.String): SignalMessage = copy(toUserId = __v)
    def getTimestamp: com.google.protobuf.timestamp.Timestamp = timestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)
    def clearTimestamp: SignalMessage = copy(timestamp = _root_.scala.None)
    def withTimestamp(__v: com.google.protobuf.timestamp.Timestamp): SignalMessage = copy(timestamp = Some(__v))
    def withMessageType(__v: _root_.scala.Predef.String): SignalMessage = copy(messageType = __v)
    def withPayloadJson(__v: _root_.scala.Predef.String): SignalMessage = copy(payloadJson = __v)
    def withSequence(__v: _root_.scala.Long): SignalMessage = copy(sequence = __v)
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
        case 3 => {
          val __t = senderConsumerId
          if (__t != "") __t else null
        }
        case 4 => {
          val __t = senderUserId
          if (__t != "") __t else null
        }
        case 5 => {
          val __t = toUserId
          if (__t != "") __t else null
        }
        case 6 => timestamp.orNull
        case 7 => {
          val __t = messageType
          if (__t != "") __t else null
        }
        case 8 => {
          val __t = payloadJson
          if (__t != "") __t else null
        }
        case 9 => {
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
        case 3 => _root_.scalapb.descriptors.PString(senderConsumerId)
        case 4 => _root_.scalapb.descriptors.PString(senderUserId)
        case 5 => _root_.scalapb.descriptors.PString(toUserId)
        case 6 => timestamp.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        case 7 => _root_.scalapb.descriptors.PString(messageType)
        case 8 => _root_.scalapb.descriptors.PString(payloadJson)
        case 9 => _root_.scalapb.descriptors.PLong(sequence)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.SignalMessage
}

object SignalMessage extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.SignalMessage] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.SignalMessage] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.SignalMessage = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.SignalMessage(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(2), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(3), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(4), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.get(__fields.get(5)).asInstanceOf[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]],
      __fieldsMap.getOrElse(__fields.get(6), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(7), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(8), 0L).asInstanceOf[_root_.scala.Long]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.SignalMessage] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.SignalMessage(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]]),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(8).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(9).get).map(_.as[_root_.scala.Long]).getOrElse(0L)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 6 => __out = com.google.protobuf.timestamp.Timestamp
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.SignalMessage(
  )
  implicit class SignalMessageLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.SignalMessage]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.SignalMessage](_l) {
    def messageId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.messageId)((c_, f_) => c_.copy(messageId = f_))
    def channelName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.channelName)((c_, f_) => c_.copy(channelName = f_))
    def senderConsumerId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.senderConsumerId)((c_, f_) => c_.copy(senderConsumerId = f_))
    def senderUserId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.senderUserId)((c_, f_) => c_.copy(senderUserId = f_))
    def toUserId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.toUserId)((c_, f_) => c_.copy(toUserId = f_))
    def timestamp: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.timestamp.Timestamp] = field(_.getTimestamp)((c_, f_) => c_.copy(timestamp = Some(f_)))
    def optionalTimestamp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]] = field(_.timestamp)((c_, f_) => c_.copy(timestamp = f_))
    def messageType: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.messageType)((c_, f_) => c_.copy(messageType = f_))
    def payloadJson: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.payloadJson)((c_, f_) => c_.copy(payloadJson = f_))
    def sequence: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.sequence)((c_, f_) => c_.copy(sequence = f_))
  }
  final val MESSAGE_ID_FIELD_NUMBER = 1
  final val CHANNEL_NAME_FIELD_NUMBER = 2
  final val SENDER_CONSUMER_ID_FIELD_NUMBER = 3
  final val SENDER_USER_ID_FIELD_NUMBER = 4
  final val TO_USER_ID_FIELD_NUMBER = 5
  final val TIMESTAMP_FIELD_NUMBER = 6
  final val MESSAGE_TYPE_FIELD_NUMBER = 7
  final val PAYLOAD_JSON_FIELD_NUMBER = 8
  final val SEQUENCE_FIELD_NUMBER = 9
}
