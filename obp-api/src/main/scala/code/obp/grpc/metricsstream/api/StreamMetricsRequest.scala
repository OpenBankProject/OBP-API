// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api/StreamMessagesRequest.scala). No protoc plugin is
// wired into the Maven build.
//
// Protofile syntax: PROTO3

package code.obp.grpc.metricsstream.api

@SerialVersionUID(0L)
final case class StreamMetricsRequest(
    consumerId: _root_.scala.Predef.String = "",
    userId: _root_.scala.Predef.String = "",
    verb: _root_.scala.Predef.String = "",
    urlSubstring: _root_.scala.Predef.String = "",
    implementedByPartialFunction: _root_.scala.Predef.String = "",
    appName: _root_.scala.Predef.String = "",
    consentReferenceId: _root_.scala.Predef.String = ""
    ) extends scalapb.GeneratedMessage with scalapb.Message[StreamMetricsRequest] with scalapb.lenses.Updatable[StreamMetricsRequest] {
    @transient
    private[this] var __serializedSizeCachedValue: _root_.scala.Int = 0
    private[this] def __computeSerializedValue(): _root_.scala.Int = {
      var __size = 0
      if (consumerId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, consumerId) }
      if (userId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, userId) }
      if (verb != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(3, verb) }
      if (urlSubstring != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(4, urlSubstring) }
      if (implementedByPartialFunction != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(5, implementedByPartialFunction) }
      if (appName != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(6, appName) }
      if (consentReferenceId != "") { __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(7, consentReferenceId) }
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
      { val __v = consumerId; if (__v != "") _output__.writeString(1, __v) };
      { val __v = userId; if (__v != "") _output__.writeString(2, __v) };
      { val __v = verb; if (__v != "") _output__.writeString(3, __v) };
      { val __v = urlSubstring; if (__v != "") _output__.writeString(4, __v) };
      { val __v = implementedByPartialFunction; if (__v != "") _output__.writeString(5, __v) };
      { val __v = appName; if (__v != "") _output__.writeString(6, __v) };
      { val __v = consentReferenceId; if (__v != "") _output__.writeString(7, __v) };
    }
    def mergeFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): code.obp.grpc.metricsstream.api.StreamMetricsRequest = {
      var __consumerId = this.consumerId
      var __userId = this.userId
      var __verb = this.verb
      var __urlSubstring = this.urlSubstring
      var __implementedByPartialFunction = this.implementedByPartialFunction
      var __appName = this.appName
      var __consentReferenceId = this.consentReferenceId
      var _done__ = false
      while (!_done__) {
        val _tag__ = _input__.readTag()
        _tag__ match {
          case 0 => _done__ = true
          case 10 => __consumerId = _input__.readString()
          case 18 => __userId = _input__.readString()
          case 26 => __verb = _input__.readString()
          case 34 => __urlSubstring = _input__.readString()
          case 42 => __implementedByPartialFunction = _input__.readString()
          case 50 => __appName = _input__.readString()
          case 58 => __consentReferenceId = _input__.readString()
          case tag => _input__.skipField(tag)
        }
      }
      code.obp.grpc.metricsstream.api.StreamMetricsRequest(
          consumerId = __consumerId,
          userId = __userId,
          verb = __verb,
          urlSubstring = __urlSubstring,
          implementedByPartialFunction = __implementedByPartialFunction,
          appName = __appName,
          consentReferenceId = __consentReferenceId
      )
    }
    def withConsumerId(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(consumerId = __v)
    def withUserId(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(userId = __v)
    def withVerb(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(verb = __v)
    def withUrlSubstring(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(urlSubstring = __v)
    def withImplementedByPartialFunction(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(implementedByPartialFunction = __v)
    def withAppName(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(appName = __v)
    def withConsentReferenceId(__v: _root_.scala.Predef.String): StreamMetricsRequest = copy(consentReferenceId = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => { val __t = consumerId; if (__t != "") __t else null }
        case 2 => { val __t = userId; if (__t != "") __t else null }
        case 3 => { val __t = verb; if (__t != "") __t else null }
        case 4 => { val __t = urlSubstring; if (__t != "") __t else null }
        case 5 => { val __t = implementedByPartialFunction; if (__t != "") __t else null }
        case 6 => { val __t = appName; if (__t != "") __t else null }
        case 7 => { val __t = consentReferenceId; if (__t != "") __t else null }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(consumerId)
        case 2 => _root_.scalapb.descriptors.PString(userId)
        case 3 => _root_.scalapb.descriptors.PString(verb)
        case 4 => _root_.scalapb.descriptors.PString(urlSubstring)
        case 5 => _root_.scalapb.descriptors.PString(implementedByPartialFunction)
        case 6 => _root_.scalapb.descriptors.PString(appName)
        case 7 => _root_.scalapb.descriptors.PString(consentReferenceId)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion = code.obp.grpc.metricsstream.api.StreamMetricsRequest
}

object StreamMetricsRequest extends scalapb.GeneratedMessageCompanion[code.obp.grpc.metricsstream.api.StreamMetricsRequest] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[code.obp.grpc.metricsstream.api.StreamMetricsRequest] = this
  def fromFieldsMap(__fieldsMap: scala.collection.immutable.Map[_root_.com.google.protobuf.Descriptors.FieldDescriptor, scala.Any]): code.obp.grpc.metricsstream.api.StreamMetricsRequest = {
    require(__fieldsMap.keys.forall(_.getContainingType() == javaDescriptor), "FieldDescriptor does not match message type.")
    val __fields = javaDescriptor.getFields
    code.obp.grpc.metricsstream.api.StreamMetricsRequest(
      __fieldsMap.getOrElse(__fields.get(0), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(1), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(2), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(3), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(4), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(5), "").asInstanceOf[_root_.scala.Predef.String],
      __fieldsMap.getOrElse(__fields.get(6), "").asInstanceOf[_root_.scala.Predef.String]
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[code.obp.grpc.metricsstream.api.StreamMetricsRequest] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      require(__fieldsMap.keys.forall(_.containingMessage == scalaDescriptor), "FieldDescriptor does not match message type.")
      code.obp.grpc.metricsstream.api.StreamMetricsRequest(
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(4).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        __fieldsMap.get(scalaDescriptor.findFieldByNumber(7).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = MetricsStreamProto.javaDescriptor.getMessageTypes.get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = throw new UnsupportedOperationException("scalaDescriptor not available")
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = code.obp.grpc.metricsstream.api.StreamMetricsRequest()
  implicit class StreamMetricsRequestLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, code.obp.grpc.metricsstream.api.StreamMetricsRequest]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, code.obp.grpc.metricsstream.api.StreamMetricsRequest](_l) {
    def consumerId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.consumerId)((c_, f_) => c_.copy(consumerId = f_))
    def userId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.userId)((c_, f_) => c_.copy(userId = f_))
    def verb: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.verb)((c_, f_) => c_.copy(verb = f_))
    def urlSubstring: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.urlSubstring)((c_, f_) => c_.copy(urlSubstring = f_))
    def implementedByPartialFunction: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.implementedByPartialFunction)((c_, f_) => c_.copy(implementedByPartialFunction = f_))
    def appName: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.appName)((c_, f_) => c_.copy(appName = f_))
    def consentReferenceId: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.consentReferenceId)((c_, f_) => c_.copy(consentReferenceId = f_))
  }
  final val CONSUMER_ID_FIELD_NUMBER = 1
  final val USER_ID_FIELD_NUMBER = 2
  final val VERB_FIELD_NUMBER = 3
  final val URL_SUBSTRING_FIELD_NUMBER = 4
  final val IMPLEMENTED_BY_PARTIAL_FUNCTION_FIELD_NUMBER = 5
  final val APP_NAME_FIELD_NUMBER = 6
  final val CONSENT_REFERENCE_ID_FIELD_NUMBER = 7
}
