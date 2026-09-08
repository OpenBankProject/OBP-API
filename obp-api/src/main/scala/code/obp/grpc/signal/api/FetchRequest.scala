// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class FetchRequest(
    channelName: _root_.scala.Predef.String = "",
    offset: _root_.scala.Int = 0,
    limit: _root_.scala.Int = 0,
    afterSequence: _root_.scala.Long = 0L
    ) extends scalapb.GeneratedMessage with scalapb.Message[FetchRequest] with scalapb.lenses.Updatable[FetchRequest] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (channelName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, channelName) }
      if (offset != 0) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt32Size(2, offset) }
      if (limit != 0) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt32Size(3, limit) }
      if (afterSequence != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(4, afterSequence) }
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
      { val __v = offset; if (__v != 0) _output__.writeInt32(2, __v) };
      { val __v = limit; if (__v != 0) _output__.writeInt32(3, __v) };
      { val __v = afterSequence; if (__v != 0L) _output__.writeInt64(4, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.FetchRequest = {
      var __channelName = this.channelName
      var __offset = this.offset
      var __limit = this.limit
      var __afterSequence = this.afterSequence
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __channelName = _input__.readString()
          case 16 =>
            __offset = _input__.readInt32()
          case 24 =>
            __limit = _input__.readInt32()
          case 32 =>
            __afterSequence = _input__.readInt64()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.FetchRequest(
          channelName = __channelName,
          offset = __offset,
          limit = __limit,
          afterSequence = __afterSequence
      )
    }
    def withChannelName(__v: _root_.scala.Predef.String): FetchRequest = copy(channelName = __v)
    def withOffset(__v: _root_.scala.Int): FetchRequest = copy(offset = __v)
    def withLimit(__v: _root_.scala.Int): FetchRequest = copy(limit = __v)
    def withAfterSequence(__v: _root_.scala.Long): FetchRequest = copy(afterSequence = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = channelName
          if (__t != "") __t else null
        }
        case 2 => {
          val __t = offset
          if (__t != 0) __t else null
        }
        case 3 => {
          val __t = limit
          if (__t != 0) __t else null
        }
        case 4 => {
          val __t = afterSequence
          if (__t != 0L) __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(channelName)
        case 2 => _root_.scalapb.descriptors.PInt(offset)
        case 3 => _root_.scalapb.descriptors.PInt(limit)
        case 4 => _root_.scalapb.descriptors.PLong(afterSequence)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.FetchRequest
}

object FetchRequest extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.FetchRequest] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.FetchRequest] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.FetchRequest = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.FetchRequest(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), 0).asInstanceOf[_root_.scala.Int],
      __fieldsMap.getOrElse(__fields.get(2), 0).asInstanceOf[_root_.scala.Int],
      __fieldsMap.getOrElse(__fields.get(3), 0L).asInstanceOf[_root_.scala.Long]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.FetchRequest] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.FetchRequest(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Int]).getOrElse(0),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Int]).getOrElse(0),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Long]).getOrElse(0L)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(4)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.FetchRequest(
  )
  implicit class FetchRequestLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.FetchRequest]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.FetchRequest](_l) {
    def channelName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.channelName)((c_, f_) => c_.copy(channelName = f_))
    def offset: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Int] = field(_.offset)((c_, f_) => c_.copy(offset = f_))
    def limit: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Int] = field(_.limit)((c_, f_) => c_.copy(limit = f_))
    def afterSequence: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.afterSequence)((c_, f_) => c_.copy(afterSequence = f_))
  }
  final val CHANNEL_NAME_FIELD_NUMBER = 1
  final val OFFSET_FIELD_NUMBER = 2
  final val LIMIT_FIELD_NUMBER = 3
  final val AFTER_SEQUENCE_FIELD_NUMBER = 4
}
