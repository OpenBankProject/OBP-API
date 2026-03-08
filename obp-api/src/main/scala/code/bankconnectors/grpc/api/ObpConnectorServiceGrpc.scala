// Hand-written gRPC stubs for connector.proto
// Uses raw io.grpc API with protobuf wire format for full compatibility
// with any gRPC server implementing the same proto definition.

package code.bankconnectors.grpc.api

case class ObpConnectorRequest(methodName: String = "", jsonPayload: String = "")
case class ObpConnectorResponse(jsonPayload: String = "")

object ObpConnectorServiceGrpc {

  private val requestMarshaller: _root_.io.grpc.MethodDescriptor.Marshaller[ObpConnectorRequest] =
    new _root_.io.grpc.MethodDescriptor.Marshaller[ObpConnectorRequest] {
      override def stream(value: ObpConnectorRequest): java.io.InputStream = {
        val baos = new java.io.ByteArrayOutputStream()
        val cos = _root_.com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.methodName.nonEmpty) cos.writeString(1, value.methodName)
        if (value.jsonPayload.nonEmpty) cos.writeString(2, value.jsonPayload)
        cos.flush()
        new java.io.ByteArrayInputStream(baos.toByteArray)
      }
      override def parse(stream: java.io.InputStream): ObpConnectorRequest = {
        val cis = _root_.com.google.protobuf.CodedInputStream.newInstance(stream)
        var methodName = ""
        var jsonPayload = ""
        var done = false
        while (!done) {
          val tag = cis.readTag()
          tag match {
            case 0 => done = true
            case 10 => methodName = cis.readString()
            case 18 => jsonPayload = cis.readString()
            case other => cis.skipField(other)
          }
        }
        ObpConnectorRequest(methodName, jsonPayload)
      }
    }

  private val responseMarshaller: _root_.io.grpc.MethodDescriptor.Marshaller[ObpConnectorResponse] =
    new _root_.io.grpc.MethodDescriptor.Marshaller[ObpConnectorResponse] {
      override def stream(value: ObpConnectorResponse): java.io.InputStream = {
        val baos = new java.io.ByteArrayOutputStream()
        val cos = _root_.com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.jsonPayload.nonEmpty) cos.writeString(1, value.jsonPayload)
        cos.flush()
        new java.io.ByteArrayInputStream(baos.toByteArray)
      }
      override def parse(stream: java.io.InputStream): ObpConnectorResponse = {
        val cis = _root_.com.google.protobuf.CodedInputStream.newInstance(stream)
        var jsonPayload = ""
        var done = false
        while (!done) {
          val tag = cis.readTag()
          tag match {
            case 0 => done = true
            case 10 => jsonPayload = cis.readString()
            case other => cis.skipField(other)
          }
        }
        ObpConnectorResponse(jsonPayload)
      }
    }

  val METHOD_PROCESS_OBP_REQUEST: _root_.io.grpc.MethodDescriptor[ObpConnectorRequest, ObpConnectorResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.bankconnectors.grpc.ObpConnectorService", "ProcessObpRequest"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(requestMarshaller)
      .setResponseMarshaller(responseMarshaller)
      .build()

  class ObpConnectorServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) {
    def processObpRequest(request: ObpConnectorRequest): ObpConnectorResponse = {
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_PROCESS_OBP_REQUEST, options), request)
    }
  }

  def blockingStub(channel: _root_.io.grpc.Channel): ObpConnectorServiceBlockingStub = new ObpConnectorServiceBlockingStub(channel)
}
