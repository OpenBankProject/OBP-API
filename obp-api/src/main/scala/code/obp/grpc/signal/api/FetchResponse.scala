// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api and logcache/api). No protoc plugin is wired into
// the Maven build. Source of truth: obp-api/src/main/protobuf/signal.proto.
// Regenerate with scripts/gen_signal_grpc_messages.py if the proto changes.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

@SerialVersionUID(0L)
final case class FetchResponse(
    channelName: _root_.scala.Predef.String = "",
    messages: _root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalMessage] = _root_.scala.collection.Seq.empty,
    totalCount: _root_.scala.Long = 0L,
    hasMore: _root_.scala.Boolean = false,
    latestSequence: _root_.scala.Long = 0L,
    nextAfterSequence: _root_.scala.Long = 0L,
    visibleCount: _root_.scala.Long = 0L
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[FetchResponse] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (channelName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, channelName) }
      messages.foreach(messages => __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(messages.serializedSize) + messages.serializedSize)
      if (totalCount != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(3, totalCount) }
      if (hasMore != false) { __size += _root_.com.google.protobuf.CodedOutputStream.computeBoolSize(4, hasMore) }
      if (latestSequence != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(5, latestSequence) }
      if (nextAfterSequence != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(6, nextAfterSequence) }
      if (visibleCount != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(7, visibleCount) }
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
      messages.foreach { __v =>
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__v.serializedSize)
        __v.writeTo(_output__)
      };
      { val __v = totalCount; if (__v != 0L) _output__.writeInt64(3, __v) };
      { val __v = hasMore; if (__v != false) _output__.writeBool(4, __v) };
      { val __v = latestSequence; if (__v != 0L) _output__.writeInt64(5, __v) };
      { val __v = nextAfterSequence; if (__v != 0L) _output__.writeInt64(6, __v) };
      { val __v = visibleCount; if (__v != 0L) _output__.writeInt64(7, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.FetchResponse = {
      var __channelName = this.channelName
      val __messages = (_root_.scala.collection.immutable.Vector.newBuilder[code.obp.grpc.signal.api.SignalMessage] ++= this.messages)
      var __totalCount = this.totalCount
      var __hasMore = this.hasMore
      var __latestSequence = this.latestSequence
      var __nextAfterSequence = this.nextAfterSequence
      var __visibleCount = this.visibleCount
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 =>
            __channelName = _input__.readString()
          case 18 =>
            __messages += _root_.scalapb.LiteParser.readMessage(_input__, code.obp.grpc.signal.api.SignalMessage.defaultInstance)
          case 24 =>
            __totalCount = _input__.readInt64()
          case 32 =>
            __hasMore = _input__.readBool()
          case 40 =>
            __latestSequence = _input__.readInt64()
          case 48 =>
            __nextAfterSequence = _input__.readInt64()
          case 56 =>
            __visibleCount = _input__.readInt64()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.signal.api.FetchResponse(
          channelName = __channelName,
          messages = __messages.result(),
          totalCount = __totalCount,
          hasMore = __hasMore,
          latestSequence = __latestSequence,
          nextAfterSequence = __nextAfterSequence,
          visibleCount = __visibleCount
      )
    }
    def withChannelName(__v: _root_.scala.Predef.String): FetchResponse = copy(channelName = __v)
    def clearMessages = copy(messages = _root_.scala.collection.Seq.empty)
    def addMessages(__vs: code.obp.grpc.signal.api.SignalMessage*): FetchResponse = addAllMessages(__vs)
    def addAllMessages(__vs: TraversableOnce[code.obp.grpc.signal.api.SignalMessage]): FetchResponse = copy(messages = messages ++ __vs)
    def withMessages(__v: _root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalMessage]): FetchResponse = copy(messages = __v)
    def withTotalCount(__v: _root_.scala.Long): FetchResponse = copy(totalCount = __v)
    def withHasMore(__v: _root_.scala.Boolean): FetchResponse = copy(hasMore = __v)
    def withLatestSequence(__v: _root_.scala.Long): FetchResponse = copy(latestSequence = __v)
    def withNextAfterSequence(__v: _root_.scala.Long): FetchResponse = copy(nextAfterSequence = __v)
    def withVisibleCount(__v: _root_.scala.Long): FetchResponse = copy(visibleCount = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = channelName
          if (__t != "") __t else null
        }
        case 2 => messages
        case 3 => {
          val __t = totalCount
          if (__t != 0L) __t else null
        }
        case 4 => {
          val __t = hasMore
          if (__t != false) __t else null
        }
        case 5 => {
          val __t = latestSequence
          if (__t != 0L) __t else null
        }
        case 6 => {
          val __t = nextAfterSequence
          if (__t != 0L) __t else null
        }
        case 7 => {
          val __t = visibleCount
          if (__t != 0L) __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(channelName)
        case 2 => _root_.scalapb.descriptors.PRepeated(messages.iterator.map(_.toPMessage).toVector)
        case 3 => _root_.scalapb.descriptors.PLong(totalCount)
        case 4 => _root_.scalapb.descriptors.PBoolean(hasMore)
        case 5 => _root_.scalapb.descriptors.PLong(latestSequence)
        case 6 => _root_.scalapb.descriptors.PLong(nextAfterSequence)
        case 7 => _root_.scalapb.descriptors.PLong(visibleCount)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.signal.api.FetchResponse
}

object FetchResponse extends scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.FetchResponse] {
  // scalapb 0.11's GeneratedMessageCompanion declares parseFrom; the checked-in sources were
  // generated by an older scalapb that put mergeFrom on the message instead (via the
  // scalapb.Message trait, which 0.11 removed). Same in-place patching the 0.9.0 upgrade used -
  // see scripts/regenerate_grpc.sh's header for why these files are patched, not regenerated.
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.signal.api.FetchResponse =
    defaultInstance.mergeFrom(`_input__`)
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.signal.api.FetchResponse] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.signal.api.FetchResponse = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.signal.api.FetchResponse(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), Nil).asInstanceOf[_root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalMessage]],
      __fieldsMap.getOrElse(__fields.get(2), 0L).asInstanceOf[_root_.scala.Long],
      __fieldsMap.getOrElse(__fields.get(3), false).asInstanceOf[_root_.scala.Boolean],
      __fieldsMap.getOrElse(__fields.get(4), 0L).asInstanceOf[_root_.scala.Long],
      __fieldsMap.getOrElse(__fields.get(5), 0L).asInstanceOf[_root_.scala.Long],
      __fieldsMap.getOrElse(__fields.get(6), 0L).asInstanceOf[_root_.scala.Long]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.signal.api.FetchResponse] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.signal.api.FetchResponse(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalMessage]]).getOrElse(_root_.scala.collection.Seq.empty),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Long]).getOrElse(0L),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Boolean]).getOrElse(false),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Long]).getOrElse(0L),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(_.as[_root_.scala.Long]).getOrElse(0L),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).map(_.as[_root_.scala.Long]).getOrElse(0L)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = SignalProto.javaDescriptor.getMessageTypes.get(5)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 2 => __out = code.obp.grpc.signal.api.SignalMessage
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.signal.api.FetchResponse(
  )
  implicit class FetchResponseLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.signal.api.FetchResponse]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.signal.api.FetchResponse](_l) {
    def channelName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.channelName)((c_, f_) => c_.copy(channelName = f_))
    def messages: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.collection.Seq[code.obp.grpc.signal.api.SignalMessage]] = field(_.messages)((c_, f_) => c_.copy(messages = f_))
    def totalCount: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.totalCount)((c_, f_) => c_.copy(totalCount = f_))
    def hasMore: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Boolean] = field(_.hasMore)((c_, f_) => c_.copy(hasMore = f_))
    def latestSequence: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.latestSequence)((c_, f_) => c_.copy(latestSequence = f_))
    def nextAfterSequence: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.nextAfterSequence)((c_, f_) => c_.copy(nextAfterSequence = f_))
    def visibleCount: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.visibleCount)((c_, f_) => c_.copy(visibleCount = f_))
  }
  final val CHANNEL_NAME_FIELD_NUMBER = 1
  final val MESSAGES_FIELD_NUMBER = 2
  final val TOTAL_COUNT_FIELD_NUMBER = 3
  final val HAS_MORE_FIELD_NUMBER = 4
  final val LATEST_SEQUENCE_FIELD_NUMBER = 5
  final val NEXT_AFTER_SEQUENCE_FIELD_NUMBER = 6
  final val VISIBLE_COUNT_FIELD_NUMBER = 7
}
