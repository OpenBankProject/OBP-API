// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api/ChatStreamServiceGrpc.scala and
// api/ObpServiceGrpc.scala). No protoc plugin is wired into the Maven build.
// Source of truth: obp-api/src/main/protobuf/signal.proto.
//
// Protofile syntax: PROTO3

package code.obp.grpc.signal.api

object SignalChannelsServiceGrpc {

  private val SERVICE_NAME = "code.obp.grpc.signal.g1.SignalChannelsService"

  val METHOD_PUBLISH: _root_.io.grpc.MethodDescriptor[PublishRequest, PublishResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Publish"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(PublishRequest))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(PublishResponse))
      .build()

  val METHOD_FETCH: _root_.io.grpc.MethodDescriptor[FetchRequest, FetchResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Fetch"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(FetchRequest))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(FetchResponse))
      .build()

  val METHOD_LIST_CHANNELS: _root_.io.grpc.MethodDescriptor[ListChannelsRequest, ListChannelsResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName(SERVICE_NAME, "ListChannels"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(ListChannelsRequest))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(ListChannelsResponse))
      .build()

  val METHOD_SUBSCRIBE: _root_.io.grpc.MethodDescriptor[SubscribeRequest, SignalMessage] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Subscribe"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(SubscribeRequest))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(SignalMessage))
      .build()

  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(SignalProto.javaDescriptor))
      .addMethod(METHOD_PUBLISH)
      .addMethod(METHOD_FETCH)
      .addMethod(METHOD_LIST_CHANNELS)
      .addMethod(METHOD_SUBSCRIBE)
      .build()

  trait SignalChannelsService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion = SignalChannelsService

    /** 1:1 with POST /signal-channels/CHANNEL_NAME/messages */
    def publish(request: PublishRequest): scala.concurrent.Future[PublishResponse]

    /** 1:1 with GET /signal-channels/CHANNEL_NAME/messages (offset/limit, privacy-filtered) */
    def fetch(request: FetchRequest): scala.concurrent.Future[FetchResponse]

    /** 1:1 with GET /signal-channels (broadcast-visible channels only) */
    def listChannels(request: ListChannelsRequest): scala.concurrent.Future[ListChannelsResponse]

    /** Server-side stream of new messages on one channel. Live only: no catch-up, no replay. */
    def subscribe(request: SubscribeRequest,
                  responseObserver: _root_.io.grpc.stub.StreamObserver[SignalMessage]): Unit
  }

  object SignalChannelsService extends _root_.scalapb.grpc.ServiceCompanion[SignalChannelsService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[SignalChannelsService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor =
      SignalProto.javaDescriptor.getServices().get(0)
  }

  trait SignalChannelsServiceBlockingClient {
    def serviceCompanion = SignalChannelsService
    def publish(request: PublishRequest): PublishResponse
    def fetch(request: FetchRequest): FetchResponse
    def listChannels(request: ListChannelsRequest): ListChannelsResponse
    def subscribe(request: SubscribeRequest): scala.collection.Iterator[SignalMessage]
  }

  class SignalChannelsServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT)
    extends _root_.io.grpc.stub.AbstractStub[SignalChannelsServiceBlockingStub](channel, options) with SignalChannelsServiceBlockingClient {

    override def publish(request: PublishRequest): PublishResponse =
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_PUBLISH, options), request)

    override def fetch(request: FetchRequest): FetchResponse =
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_FETCH, options), request)

    override def listChannels(request: ListChannelsRequest): ListChannelsResponse =
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_LIST_CHANNELS, options), request)

    override def subscribe(request: SubscribeRequest): scala.collection.Iterator[SignalMessage] =
      scala.jdk.CollectionConverters.IteratorHasAsScala(
        _root_.io.grpc.stub.ClientCalls.blockingServerStreamingCall(channel.newCall(METHOD_SUBSCRIBE, options), request)).asScala

    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): SignalChannelsServiceBlockingStub =
      new SignalChannelsServiceBlockingStub(channel, options)
  }

  class SignalChannelsServiceStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT)
    extends _root_.io.grpc.stub.AbstractStub[SignalChannelsServiceStub](channel, options) with SignalChannelsService {

    override def publish(request: PublishRequest): scala.concurrent.Future[PublishResponse] =
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_PUBLISH, options), request))

    override def fetch(request: FetchRequest): scala.concurrent.Future[FetchResponse] =
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_FETCH, options), request))

    override def listChannels(request: ListChannelsRequest): scala.concurrent.Future[ListChannelsResponse] =
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_LIST_CHANNELS, options), request))

    override def subscribe(request: SubscribeRequest, responseObserver: _root_.io.grpc.stub.StreamObserver[SignalMessage]): Unit =
      _root_.io.grpc.stub.ClientCalls.asyncServerStreamingCall(channel.newCall(METHOD_SUBSCRIBE, options), request, responseObserver)

    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): SignalChannelsServiceStub =
      new SignalChannelsServiceStub(channel, options)
  }

  def bindService(serviceImpl: SignalChannelsService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
    _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
      .addMethod(
        METHOD_PUBLISH,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[PublishRequest, PublishResponse] {
          override def invoke(request: PublishRequest, observer: _root_.io.grpc.stub.StreamObserver[PublishResponse]): Unit =
            serviceImpl.publish(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(executionContext)
        }))
      .addMethod(
        METHOD_FETCH,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[FetchRequest, FetchResponse] {
          override def invoke(request: FetchRequest, observer: _root_.io.grpc.stub.StreamObserver[FetchResponse]): Unit =
            serviceImpl.fetch(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(executionContext)
        }))
      .addMethod(
        METHOD_LIST_CHANNELS,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[ListChannelsRequest, ListChannelsResponse] {
          override def invoke(request: ListChannelsRequest, observer: _root_.io.grpc.stub.StreamObserver[ListChannelsResponse]): Unit =
            serviceImpl.listChannels(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(executionContext)
        }))
      .addMethod(
        METHOD_SUBSCRIBE,
        _root_.io.grpc.stub.ServerCalls.asyncServerStreamingCall(
          new _root_.io.grpc.stub.ServerCalls.ServerStreamingMethod[SubscribeRequest, SignalMessage] {
            override def invoke(request: SubscribeRequest, responseObserver: _root_.io.grpc.stub.StreamObserver[SignalMessage]): Unit =
              serviceImpl.subscribe(request, responseObserver)
          }))
      .build()

  def blockingStub(channel: _root_.io.grpc.Channel): SignalChannelsServiceBlockingStub = new SignalChannelsServiceBlockingStub(channel)

  def stub(channel: _root_.io.grpc.Channel): SignalChannelsServiceStub = new SignalChannelsServiceStub(channel)

  def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = SignalProto.javaDescriptor.getServices().get(0)
}
