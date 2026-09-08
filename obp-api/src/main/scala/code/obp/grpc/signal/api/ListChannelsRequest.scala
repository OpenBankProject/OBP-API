// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class ListChannelsRequest(

    ) extends scalapb.GeneratedMessage with scalapb.Message[ListChannelsRequest] with scalapb.lenses.Updatable[ListChannelsRequest] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0

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

    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.ListChannelsRequest = {

      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true

          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.ListChannelsRequest(

      )
    }

    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      throw new MatchError(__fieldNumber)
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      throw new MatchError(__field.number)
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.ListChannelsRequest
}

object ListChannelsRequest extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.ListChannelsRequest] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.ListChannelsRequest] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.ListChannelsRequest = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.ListChannelsRequest(

    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.ListChannelsRequest] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.ListChannelsRequest(

      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(6)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.ListChannelsRequest(
  )
  implicit class ListChannelsRequestLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.ListChannelsRequest]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.ListChannelsRequest](_l) {

  }

}
