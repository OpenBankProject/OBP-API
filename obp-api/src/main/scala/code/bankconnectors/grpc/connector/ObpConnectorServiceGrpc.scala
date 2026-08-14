package code.bankconnectors.grpc.connector

object ObpConnectorServiceGrpc {
  val METHOD_PROCESS_OBP_REQUEST: _root_.io.grpc.MethodDescriptor[_root_.code.bankconnectors.grpc.connector.ObpConnectorRequest, _root_.code.bankconnectors.grpc.connector.ObpConnectorResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.bankconnectors.grpc.ObpConnectorService", "ProcessObpRequest"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[_root_.code.bankconnectors.grpc.connector.ObpConnectorRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[_root_.code.bankconnectors.grpc.connector.ObpConnectorResponse])
      .build()
  
  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder("code.bankconnectors.grpc.ObpConnectorService")
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(code.bankconnectors.grpc.connector.ConnectorProto.javaDescriptor))
      .addMethod(METHOD_PROCESS_OBP_REQUEST)
      .build()
  
  trait ObpConnectorService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion = ObpConnectorService
    def processObpRequest(request: _root_.code.bankconnectors.grpc.connector.ObpConnectorRequest): scala.concurrent.Future[_root_.code.bankconnectors.grpc.connector.ObpConnectorResponse]
  }
  
  object ObpConnectorService extends _root_.scalapb.grpc.ServiceCompanion[ObpConnectorService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[ObpConnectorService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = code.bankconnectors.grpc.connector.ConnectorProto.javaDescriptor.getServices().get(0)
    def scalaDescriptor: _root_.scalapb.descriptors.ServiceDescriptor = ConnectorProto.scalaDescriptor.services(0)
  }
  
  trait ObpConnectorServiceBlockingClient {
    def serviceCompanion = ObpConnectorService
    def processObpRequest(request: _root_.code.bankconnectors.grpc.connector.ObpConnectorRequest): _root_.code.bankconnectors.grpc.connector.ObpConnectorResponse
  }
  
  class ObpConnectorServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[ObpConnectorServiceBlockingStub](channel, options) with ObpConnectorServiceBlockingClient {
    override def processObpRequest(request: _root_.code.bankconnectors.grpc.connector.ObpConnectorRequest): _root_.code.bankconnectors.grpc.connector.ObpConnectorResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_PROCESS_OBP_REQUEST, options, request)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ObpConnectorServiceBlockingStub = new ObpConnectorServiceBlockingStub(channel, options)
  }
  
  class ObpConnectorServiceStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[ObpConnectorServiceStub](channel, options) with ObpConnectorService {
    override def processObpRequest(request: _root_.code.bankconnectors.grpc.connector.ObpConnectorRequest): scala.concurrent.Future[_root_.code.bankconnectors.grpc.connector.ObpConnectorResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_PROCESS_OBP_REQUEST, options, request)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ObpConnectorServiceStub = new ObpConnectorServiceStub(channel, options)
  }
  
  def bindService(serviceImpl: ObpConnectorService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
    _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
    .addMethod(
      METHOD_PROCESS_OBP_REQUEST,
      _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[_root_.code.bankconnectors.grpc.connector.ObpConnectorRequest, _root_.code.bankconnectors.grpc.connector.ObpConnectorResponse] {
        override def invoke(request: _root_.code.bankconnectors.grpc.connector.ObpConnectorRequest, observer: _root_.io.grpc.stub.StreamObserver[_root_.code.bankconnectors.grpc.connector.ObpConnectorResponse]): Unit =
          serviceImpl.processObpRequest(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
      }))
    .build()
  
  def blockingStub(channel: _root_.io.grpc.Channel): ObpConnectorServiceBlockingStub = new ObpConnectorServiceBlockingStub(channel)
  
  def stub(channel: _root_.io.grpc.Channel): ObpConnectorServiceStub = new ObpConnectorServiceStub(channel)
  
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = code.bankconnectors.grpc.connector.ConnectorProto.javaDescriptor.getServices().get(0)
  
}