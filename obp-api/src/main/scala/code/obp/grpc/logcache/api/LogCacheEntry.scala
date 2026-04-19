// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api/ChatMessageEvent.scala). No protoc plugin is
// wired into the Maven build.
//
// Protofile syntax: PROTO3

package code.obp.grpc.logcache.api

@SerialVersionUID(0L)
final case class LogCacheEntry(
    level: _root_.scala.Int = 0,
    message: _root_.scala.Predef.String = "",
    timestamp: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp] = _root_.scala.None,
    apiInstanceId: _root_.scala.Predef.String = ""
    ) extends scalapb.GeneratedMessage with scalapb.Message[LogCacheEntry] with scalapb.lenses.Updatable[LogCacheEntry] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (level != 0) { __size += _root_.com.google.protobuf.CodedOutputStream.computeEnumSize(1, level) }
      if (message != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, message) }
      if (timestamp.isDefined) {
        val __v = timestamp.get
        val __s = __v.serializedSize
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__s) + __s
      }
      if (apiInstanceId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, apiInstanceId) }
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
      {
        val __v = level
        if (__v != 0) {
          _output__.writeEnum(1, __v)
        }
      };
      {
        val __v = message
        if (__v != "") {
          _output__.writeString(2, __v)
        }
      };
      timestamp.foreach { __v =>
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__v.serializedSize)
        __v.writeTo(_output__)
      };
      { val __v = apiInstanceId; if (__v != "") _output__.writeString(4, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.logcache.api.LogCacheEntry = {
      var __level = this.level
      var __message = this.message
      var __timestamp = this.timestamp
      var __apiInstanceId = this.apiInstanceId
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 8 =>
            __level = _input__.readEnum()
          case 18 =>
            __message = _input__.readString()
          case 26 =>
            __timestamp = Some(_root_.scalapb.LiteParser.readMessage(_input__, __timestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)))
          case 34 =>
            __apiInstanceId = _input__.readString()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.logcache.api.LogCacheEntry(
          level = __level,
          message = __message,
          timestamp = __timestamp,
          apiInstanceId = __apiInstanceId
      )
    }
    def withLevel(__v: _root_.scala.Int): LogCacheEntry = copy(level = __v)
    def withMessage(__v: _root_.scala.Predef.String): LogCacheEntry = copy(message = __v)
    def getTimestamp: com.google.protobuf.timestamp.Timestamp = timestamp.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)
    def clearTimestamp: LogCacheEntry = copy(timestamp = _root_.scala.None)
    def withTimestamp(__v: com.google.protobuf.timestamp.Timestamp): LogCacheEntry = copy(timestamp = Some(__v))
    def withApiInstanceId(__v: _root_.scala.Predef.String): LogCacheEntry = copy(apiInstanceId = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = level
          if (__t != 0) __t else null
        }
        case 2 => {
          val __t = message
          if (__t != "") __t else null
        }
        case 3 => timestamp.orNull
        case 4 => {
          val __t = apiInstanceId
          if (__t != "") __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PInt(level)
        case 2 => _root_.scalapb.descriptors.PString(message)
        case 3 => timestamp.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        case 4 => _root_.scalapb.descriptors.PString(apiInstanceId)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.logcache.api.LogCacheEntry
}

object LogCacheEntry extends scalapb.GeneratedMessageCompanion[code.obp.grpc.logcache.api.LogCacheEntry] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.logcache.api.LogCacheEntry] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.logcache.api.LogCacheEntry = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.logcache.api.LogCacheEntry(
      __fieldsMap.get(__fields.get(0)).map(_.asInstanceOf[_root_.com.google.protobuf.Descriptors.EnumValueDescriptor].getNumber).getOrElse(0),
      __fieldsMap.getOrElse(__fields.get(1), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.get(__fields.get(2)).asInstanceOf[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]],
      __fieldsMap.getOrElse(__fields.get(3), "").asInstanceOf[_root_.scala.Predef.String]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.logcache.api.LogCacheEntry] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.logcache.api.LogCacheEntry(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Int]).getOrElse(0),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]]),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = LogCacheProto.javaDescriptor.getMessageTypes.get(1)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    __number match {
      case 3 => __out = com.google.protobuf.timestamp.Timestamp
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.logcache.api.LogCacheEntry(
  )
  implicit class LogCacheEntryLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.logcache.api.LogCacheEntry]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.logcache.api.LogCacheEntry](_l) {
    def level: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Int] = field(_.level)((c_, f_) => c_.copy(level = f_))
    def message: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.message)((c_, f_) => c_.copy(message = f_))
    def timestamp: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.timestamp.Timestamp] = field(_.getTimestamp)((c_, f_) => c_.copy(timestamp = Some(f_)))
    def optionalTimestamp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]] = field(_.timestamp)((c_, f_) => c_.copy(timestamp = f_))
    def apiInstanceId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.apiInstanceId)((c_, f_) => c_.copy(apiInstanceId = f_))
  }
  final val LEVEL_FIELD_NUMBER = 1
  final val MESSAGE_FIELD_NUMBER = 2
  final val TIMESTAMP_FIELD_NUMBER = 3
  final val API_INSTANCE_ID_FIELD_NUMBER = 4
}
