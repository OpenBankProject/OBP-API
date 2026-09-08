// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class PublishRequest(
    channelName: _root_.scala.Predef.String = "",
    toUserId: _root_.scala.Predef.String = "",
    messageType: _root_.scala.Predef.String = "",
    payloadJson: _root_.scala.Predef.String = ""
    ) extends scalapb.GeneratedMessage with scalapb.Message[PublishRequest] with scalapb.lenses.Updatable[PublishRequest] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (channelName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, channelName) }
      if (toUserId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, toUserId) }
      if (messageType != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, messageType) }
      if (payloadJson != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, payloadJson) }
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
      { val __v = channelName; if (__v != "") _output__.writeString(1, __v) };
      { val __v = toUserId; if (__v != "") _output__.writeString(2, __v) };
      { val __v = messageType; if (__v != "") _output__.writeString(3, __v) };
      { val __v = payloadJson; if (__v != "") _output__.writeString(4, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.PublishRequest = {
      var __channelName = this.channelName
      var __toUserId = this.toUserId
      var __messageType = this.messageType
      var __payloadJson = this.payloadJson
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __channelName = _input__.readString()
          case 18 =>
            __toUserId = _input__.readString()
          case 26 =>
            __messageType = _input__.readString()
          case 34 =>
            __payloadJson = _input__.readString()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.PublishRequest(
          channelName = __channelName,
          toUserId = __toUserId,
          messageType = __messageType,
          payloadJson = __payloadJson
      )
    }
    def withChannelName(__v: _root_.scala.Predef.String): PublishRequest = copy(channelName = __v)
    def withToUserId(__v: _root_.scala.Predef.String): PublishRequest = copy(toUserId = __v)
    def withMessageType(__v: _root_.scala.Predef.String): PublishRequest = copy(messageType = __v)
    def withPayloadJson(__v: _root_.scala.Predef.String): PublishRequest = copy(payloadJson = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = channelName
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = toUserId
          if (__t != "") __t else null
        }
        case 3 => {
          val __t = messageType
          if (__t != "") __t else null
        }
        case 4 => {
          val __t = payloadJson
          if (__t != "") __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(channelName)
        case 2 => _root_.scalapb.descriptors.PString(toUserId)
        case 3 => _root_.scalapb.descriptors.PString(messageType)
        case 4 => _root_.scalapb.descriptors.PString(payloadJson)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.PublishRequest
}

object PublishRequest extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.PublishRequest] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.PublishRequest] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.PublishRequest = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.PublishRequest(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(2), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(3), "").asInstanceOf[_root_.scala.Predef.String]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.PublishRequest] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.PublishRequest(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(2)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.PublishRequest(
  )
  implicit class PublishRequestLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.PublishRequest]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.PublishRequest](_l) {
    def channelName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.channelName)((c_, f_) => c_.copy(channelName = f_))
    def toUserId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.toUserId)((c_, f_) => c_.copy(toUserId = f_))
    def messageType: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.messageType)((c_, f_) => c_.copy(messageType = f_))
    def payloadJson: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.payloadJson)((c_, f_) => c_.copy(payloadJson = f_))
  }
  final val CHANNEL_NAME_FIELD_NUMBER = 1
  final val TO_USER_ID_FIELD_NUMBER = 2
  final val MESSAGE_TYPE_FIELD_NUMBER = 3
  final val PAYLOAD_JSON_FIELD_NUMBER = 4
}
