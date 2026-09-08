// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class ListChannelsResponse(
    channels: _root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalChannelInfo] = _root_.scala.collection.Seq.empty
    ) extends scalapb.GeneratedMessage with scalapb.Message[ListChannelsResponse] with scalapb.lenses.Updatable[ListChannelsResponse] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      channels.foreach(channels => __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(channels.serializedSize) + channels.serializedSize)
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
      channels.foreach { __v =>
        _output__.writeTag(1, 2)
        _output__.writeUInt32NoTag(__v.serializedSize)
        __v.writeTo(_output__)
      };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.ListChannelsResponse = {
      val __channels = (_root_.scala.collection.immutable.Vector.newBuilder[code.obp.grpc.signal.api.SignalChannelInfo] ++= this.channels)
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __channels += _root_.scalapb.LiteParser.readMessage(_input__, code.obp.grpc.signal.api.SignalChannelInfo.defaultInstance)
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.ListChannelsResponse(
          channels = __channels.result()
      )
    }
    def clearChannels = copy(channels = _root_.scala.collection.Seq.empty)
    def addChannels(__vs: code.obp.grpc.signal.api.SignalChannelInfo*): ListChannelsResponse = addAllChannels(__vs)
    def addAllChannels(__vs: TraversableOnce[code.obp.grpc.signal.api.SignalChannelInfo]): ListChannelsResponse = copy(channels = channels ++ __vs)
    def withChannels(__v: _root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalChannelInfo]): ListChannelsResponse = copy(channels = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => channels
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PRepeated(channels.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.ListChannelsResponse
}

object ListChannelsResponse extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.ListChannelsResponse] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.ListChannelsResponse] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.ListChannelsResponse = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.ListChannelsResponse(
      __fieldsMap.getOrElse(__fields.get(0), Nil).asInstanceOf[_root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalChannelInfo]]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.ListChannelsResponse] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.ListChannelsResponse(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalChannelInfo]]).getOrElse(_root_.scala.collection.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(7)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = code.obp.grpc.signal.api.SignalChannelInfo
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.ListChannelsResponse(
  )
  implicit class ListChannelsResponseLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.ListChannelsResponse]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.ListChannelsResponse](_l) {
    def channels: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalChannelInfo]] = field(_.channels)((c_, f_) => c_.copy(channels = f_))
  }
  final val CHANNELS_FIELD_NUMBER = 1
}
