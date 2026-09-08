// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class SignalChannelInfo(
    channelName: _root_.scala.Predef.String = "",
    messageCount: _root_.scala.Long = 0L,
    ttlSeconds: _root_.scala.Long = 0L
    ) extends scalapb.GeneratedMessage with scalapb.Message[SignalChannelInfo] with scalapb.lenses.Updatable[SignalChannelInfo] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (channelName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, channelName) }
      if (messageCount != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(2, messageCount) }
      if (ttlSeconds != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(3, ttlSeconds) }
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
      { val __v = messageCount; if (__v != 0L) _output__.writeInt64(2, __v) };
      { val __v = ttlSeconds; if (__v != 0L) _output__.writeInt64(3, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.SignalChannelInfo = {
      var __channelName = this.channelName
      var __messageCount = this.messageCount
      var __ttlSeconds = this.ttlSeconds
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __channelName = _input__.readString()
          case 16 =>
            __messageCount = _input__.readInt64()
          case 24 =>
            __ttlSeconds = _input__.readInt64()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.SignalChannelInfo(
          channelName = __channelName,
          messageCount = __messageCount,
          ttlSeconds = __ttlSeconds
      )
    }
    def withChannelName(__v: _root_.scala.Predef.String): SignalChannelInfo = copy(channelName = __v)
    def withMessageCount(__v: _root_.scala.Long): SignalChannelInfo = copy(messageCount = __v)
    def withTtlSeconds(__v: _root_.scala.Long): SignalChannelInfo = copy(ttlSeconds = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = channelName
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = messageCount
          if (__t != 0L) __t else null
        }
        case 3 => {
          val __t = ttlSeconds
          if (__t != 0L) __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(channelName)
        case 2 => _root_.scalapb.descriptors.PLong(messageCount)
        case 3 => _root_.scalapb.descriptors.PLong(ttlSeconds)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.SignalChannelInfo
}

object SignalChannelInfo extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.SignalChannelInfo] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.SignalChannelInfo] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.SignalChannelInfo = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.SignalChannelInfo(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), 0L).asInstanceOf[_root_.scala.Long],
      __fieldsMap.getOrElse(__fields.get(2), 0L).asInstanceOf[_root_.scala.Long]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.SignalChannelInfo] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.SignalChannelInfo(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Long]).getOrElse(0L),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Long]).getOrElse(0L)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(1)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.SignalChannelInfo(
  )
  implicit class SignalChannelInfoLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.SignalChannelInfo]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.SignalChannelInfo](_l) {
    def channelName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.channelName)((c_, f_) => c_.copy(channelName = f_))
    def messageCount: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.messageCount)((c_, f_) => c_.copy(messageCount = f_))
    def ttlSeconds: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.ttlSeconds)((c_, f_) => c_.copy(ttlSeconds = f_))
  }
  final val CHANNEL_NAME_FIELD_NUMBER = 1
  final val MESSAGE_COUNT_FIELD_NUMBER = 2
  final val TTL_SECONDS_FIELD_NUMBER = 3
}
