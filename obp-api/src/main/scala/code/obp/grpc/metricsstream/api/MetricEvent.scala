// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer. No protoc plugin is wired into the Maven build.
//
// Protofile syntax: PROTO3

package code.obp.grpc.metricsstream.api

@SerialVersionUID(0L)
final case class MetricEvent(
    url: _root_.scala.Predef.String = "",
    date: _root_.scala.Option[com.google.protobuf.timestamp.Timestamp] = _root_.scala.None,
    durationMs: _root_.scala.Long = 0L,
    userId: _root_.scala.Predef.String = "",
    userName: _root_.scala.Predef.String = "",
    appName: _root_.scala.Predef.String = "",
    developerEmail: _root_.scala.Predef.String = "",
    consumerId: _root_.scala.Predef.String = "",
    implementedByPartialFunction: _root_.scala.Predef.String = "",
    implementedInVersion: _root_.scala.Predef.String = "",
    verb: _root_.scala.Predef.String = "",
    httpCode: _root_.scala.Int = 0,
    correlationId: _root_.scala.Predef.String = "",
    sourceIp: _root_.scala.Predef.String = "",
    targetIp: _root_.scala.Predef.String = "",
    apiInstanceId: _root_.scala.Predef.String = ""
    ) extends scalapb.GeneratedMessage with scalapb.Message[MetricEvent] with scalapb.lenses.Updatable[MetricEvent] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (url != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, url) }
      if (date.isDefined) {
        val __v = date.get
        val __s = __v.serializedSize
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__s) + __s
      }
      if (durationMs != 0L) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt64Size(3, durationMs) }
      if (userId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, userId) }
      if (userName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(5, userName) }
      if (appName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(6, appName) }
      if (developerEmail != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(7, developerEmail) }
      if (consumerId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(8, consumerId) }
      if (implementedByPartialFunction != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(9, implementedByPartialFunction) }
      if (implementedInVersion != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(10, implementedInVersion) }
      if (verb != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(11, verb) }
      if (httpCode != 0) { __size += _root_.com.google.protobuf.CodedOutputStream.computeInt32Size(12, httpCode) }
      if (correlationId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(13, correlationId) }
      if (sourceIp != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(14, sourceIp) }
      if (targetIp != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(15, targetIp) }
      if (apiInstanceId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(16, apiInstanceId) }
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
      { val __v = url; if (__v != "") _output__.writeString(1, __v) };
      date.foreach { __v =>
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__v.serializedSize)
        __v.writeTo(_output__)
      };
      { val __v = durationMs; if (__v != 0L) _output__.writeInt64(3, __v) };
      { val __v = userId; if (__v != "") _output__.writeString(4, __v) };
      { val __v = userName; if (__v != "") _output__.writeString(5, __v) };
      { val __v = appName; if (__v != "") _output__.writeString(6, __v) };
      { val __v = developerEmail; if (__v != "") _output__.writeString(7, __v) };
      { val __v = consumerId; if (__v != "") _output__.writeString(8, __v) };
      { val __v = implementedByPartialFunction; if (__v != "") _output__.writeString(9, __v) };
      { val __v = implementedInVersion; if (__v != "") _output__.writeString(10, __v) };
      { val __v = verb; if (__v != "") _output__.writeString(11, __v) };
      { val __v = httpCode; if (__v != 0) _output__.writeInt32(12, __v) };
      { val __v = correlationId; if (__v != "") _output__.writeString(13, __v) };
      { val __v = sourceIp; if (__v != "") _output__.writeString(14, __v) };
      { val __v = targetIp; if (__v != "") _output__.writeString(15, __v) };
      { val __v = apiInstanceId; if (__v != "") _output__.writeString(16, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.metricsstream.api.MetricEvent = {
      var __url = this.url
      var __date = this.date
      var __durationMs = this.durationMs
      var __userId = this.userId
      var __userName = this.userName
      var __appName = this.appName
      var __developerEmail = this.developerEmail
      var __consumerId = this.consumerId
      var __implementedByPartialFunction = this.implementedByPartialFunction
      var __implementedInVersion = this.implementedInVersion
      var __verb = this.verb
      var __httpCode = this.httpCode
      var __correlationId = this.correlationId
      var __sourceIp = this.sourceIp
      var __targetIp = this.targetIp
      var __apiInstanceId = this.apiInstanceId
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 => __url = _input__.readString()
          case 18 =>
            __date = Some(_root_.scalapb.LiteParser.readMessage(_input__, __date.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)))
          case 24 => __durationMs = _input__.readInt64()
          case 34 => __userId = _input__.readString()
          case 42 => __userName = _input__.readString()
          case 50 => __appName = _input__.readString()
          case 58 => __developerEmail = _input__.readString()
          case 66 => __consumerId = _input__.readString()
          case 74 => __implementedByPartialFunction = _input__.readString()
          case 82 => __implementedInVersion = _input__.readString()
          case 90 => __verb = _input__.readString()
          case 96 => __httpCode = _input__.readInt32()
          case 106 => __correlationId = _input__.readString()
          case 114 => __sourceIp = _input__.readString()
          case 122 => __targetIp = _input__.readString()
          case 130 => __apiInstanceId = _input__.readString()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.metricsstream.api.MetricEvent(
          url = __url,
          date = __date,
          durationMs = __durationMs,
          userId = __userId,
          userName = __userName,
          appName = __appName,
          developerEmail = __developerEmail,
          consumerId = __consumerId,
          implementedByPartialFunction = __implementedByPartialFunction,
          implementedInVersion = __implementedInVersion,
          verb = __verb,
          httpCode = __httpCode,
          correlationId = __correlationId,
          sourceIp = __sourceIp,
          targetIp = __targetIp,
          apiInstanceId = __apiInstanceId
      )
    }
    def withUrl(__v: _root_.scala.Predef.String): MetricEvent = copy(url = __v)
    def getDate: com.google.protobuf.timestamp.Timestamp = date.getOrElse(com.google.protobuf.timestamp.Timestamp.defaultInstance)
    def clearDate: MetricEvent = copy(date = _root_.scala.None)
    def withDate(__v: com.google.protobuf.timestamp.Timestamp): MetricEvent = copy(date = Some(__v))
    def withDurationMs(__v: _root_.scala.Long): MetricEvent = copy(durationMs = __v)
    def withUserId(__v: _root_.scala.Predef.String): MetricEvent = copy(userId = __v)
    def withUserName(__v: _root_.scala.Predef.String): MetricEvent = copy(userName = __v)
    def withAppName(__v: _root_.scala.Predef.String): MetricEvent = copy(appName = __v)
    def withDeveloperEmail(__v: _root_.scala.Predef.String): MetricEvent = copy(developerEmail = __v)
    def withConsumerId(__v: _root_.scala.Predef.String): MetricEvent = copy(consumerId = __v)
    def withImplementedByPartialFunction(__v: _root_.scala.Predef.String): MetricEvent = copy(implementedByPartialFunction = __v)
    def withImplementedInVersion(__v: _root_.scala.Predef.String): MetricEvent = copy(implementedInVersion = __v)
    def withVerb(__v: _root_.scala.Predef.String): MetricEvent = copy(verb = __v)
    def withHttpCode(__v: _root_.scala.Int): MetricEvent = copy(httpCode = __v)
    def withCorrelationId(__v: _root_.scala.Predef.String): MetricEvent = copy(correlationId = __v)
    def withSourceIp(__v: _root_.scala.Predef.String): MetricEvent = copy(sourceIp = __v)
    def withTargetIp(__v: _root_.scala.Predef.String): MetricEvent = copy(targetIp = __v)
    def withApiInstanceId(__v: _root_.scala.Predef.String): MetricEvent = copy(apiInstanceId = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => { val __t = url; if (__t != "") __t else null }
        case 2 => date.orNull
        case 3 => { val __t = durationMs; if (__t != 0L) __t else null }
        case 4 => { val __t = userId; if (__t != "") __t else null }
        case 5 => { val __t = userName; if (__t != "") __t else null }
        case 6 => { val __t = appName; if (__t != "") __t else null }
        case 7 => { val __t = developerEmail; if (__t != "") __t else null }
        case 8 => { val __t = consumerId; if (__t != "") __t else null }
        case 9 => { val __t = implementedByPartialFunction; if (__t != "") __t else null }
        case 10 => { val __t = implementedInVersion; if (__t != "") __t else null }
        case 11 => { val __t = verb; if (__t != "") __t else null }
        case 12 => { val __t = httpCode; if (__t != 0) __t else null }
        case 13 => { val __t = correlationId; if (__t != "") __t else null }
        case 14 => { val __t = sourceIp; if (__t != "") __t else null }
        case 15 => { val __t = targetIp; if (__t != "") __t else null }
        case 16 => { val __t = apiInstanceId; if (__t != "") __t else null }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(url)
        case 2 => date.map(_.toPMessage).getOrElse(_root_.scalapb.descriptors.PEmpty)
        case 3 => _root_.scalapb.descriptors.PLong(durationMs)
        case 4 => _root_.scalapb.descriptors.PString(userId)
        case 5 => _root_.scalapb.descriptors.PString(userName)
        case 6 => _root_.scalapb.descriptors.PString(appName)
        case 7 => _root_.scalapb.descriptors.PString(developerEmail)
        case 8 => _root_.scalapb.descriptors.PString(consumerId)
        case 9 => _root_.scalapb.descriptors.PString(implementedByPartialFunction)
        case 10 => _root_.scalapb.descriptors.PString(implementedInVersion)
        case 11 => _root_.scalapb.descriptors.PString(verb)
        case 12 => _root_.scalapb.descriptors.PInt(httpCode)
        case 13 => _root_.scalapb.descriptors.PString(correlationId)
        case 14 => _root_.scalapb.descriptors.PString(sourceIp)
        case 15 => _root_.scalapb.descriptors.PString(targetIp)
        case 16 => _root_.scalapb.descriptors.PString(apiInstanceId)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.metricsstream.api.MetricEvent
}

object MetricEvent extends scalapb.GeneratedMessageCompanion[code.obp.grpc.metricsstream.api.MetricEvent] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.metricsstream.api.MetricEvent] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.metricsstream.api.MetricEvent = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.metricsstream.api.MetricEvent(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.get(__fields.get(1)).asInstanceOf[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]],
      __fieldsMap.getOrElse(__fields.get(2), 0L).asInstanceOf[_root_.scala.Long],
      __fieldsMap.getOrElse(__fields.get(3), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(4), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(5), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(6), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(7), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(8), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(9), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(10), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(11), 0).asInstanceOf[_root_.scala.Int],
      __fieldsMap.getOrElse(__fields.get(12), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(13), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(14), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(15), "").asInstanceOf[_root_.scala.Predef.String]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.metricsstream.api.MetricEvent] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.metricsstream.api.MetricEvent(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).flatMap(_.as[_root_.scala.Option[com.google.protobuf.timestamp.Timestamp]]),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Long]).getOrElse(0L),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(8).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(9).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(10).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(11).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(12).get).map(_.as[_root_.scala.Int]).getOrElse(0),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(13).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(14).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(15).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(16).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = MetricsStreamProto.javaDescriptor.getMessageTypes.get(1)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    __number match {
      case 2 => __out = com.google.protobuf.timestamp.Timestamp
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.metricsstream.api.MetricEvent()
  implicit class MetricEventLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.metricsstream.api.MetricEvent]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.metricsstream.api.MetricEvent](_l) {
    def url: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.url)((c_, f_) => c_.copy(url = f_))
    def date: _root_.scalapb.lenses.Lens[UpperPB, com.google.protobuf.timestamp.Timestamp] = field(_.getDate)((c_, f_) => c_.copy(date = Some(f_)))
    def optionalDate: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Option[com.google.protobuf.timestamp.Timestamp]] = field(_.date)((c_, f_) => c_.copy(date = f_))
    def durationMs: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Long] = field(_.durationMs)((c_, f_) => c_.copy(durationMs = f_))
    def userId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.userId)((c_, f_) => c_.copy(userId = f_))
    def userName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.userName)((c_, f_) => c_.copy(userName = f_))
    def appName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.appName)((c_, f_) => c_.copy(appName = f_))
    def developerEmail: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.developerEmail)((c_, f_) => c_.copy(developerEmail = f_))
    def consumerId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.consumerId)((c_, f_) => c_.copy(consumerId = f_))
    def implementedByPartialFunction: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.implementedByPartialFunction)((c_, f_) => c_.copy(implementedByPartialFunction = f_))
    def implementedInVersion: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.implementedInVersion)((c_, f_) => c_.copy(implementedInVersion = f_))
    def verb: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.verb)((c_, f_) => c_.copy(verb = f_))
    def httpCode: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Int] = field(_.httpCode)((c_, f_) => c_.copy(httpCode = f_))
    def correlationId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.correlationId)((c_, f_) => c_.copy(correlationId = f_))
    def sourceIp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.sourceIp)((c_, f_) => c_.copy(sourceIp = f_))
    def targetIp: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.targetIp)((c_, f_) => c_.copy(targetIp = f_))
    def apiInstanceId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.apiInstanceId)((c_, f_) => c_.copy(apiInstanceId = f_))
  }
  final val URL_FIELD_NUMBER = 1
  final val DATE_FIELD_NUMBER = 2
  final val DURATION_MS_FIELD_NUMBER = 3
  final val USER_ID_FIELD_NUMBER = 4
  final val USER_NAME_FIELD_NUMBER = 5
  final val APP_NAME_FIELD_NUMBER = 6
  final val DEVELOPER_EMAIL_FIELD_NUMBER = 7
  final val CONSUMER_ID_FIELD_NUMBER = 8
  final val IMPLEMENTED_BY_PARTIAL_FUNCTION_FIELD_NUMBER = 9
  final val IMPLEMENTED_IN_VERSION_FIELD_NUMBER = 10
  final val VERB_FIELD_NUMBER = 11
  final val HTTP_CODE_FIELD_NUMBER = 12
  final val CORRELATION_ID_FIELD_NUMBER = 13
  final val SOURCE_IP_FIELD_NUMBER = 14
  final val TARGET_IP_FIELD_NUMBER = 15
  final val API_INSTANCE_ID_FIELD_NUMBER = 16
}
