// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer (see chat/api/ChatStreamServiceGrpc.scala). No protoc plugin
// is wired into the Maven build.
//
// Protofile syntax: PROTO3

package code.obp.grpc.logcache.api

object LogCacheStreamServiceGrpc {

  val METHOD_STREAM_LOG_CACHE_ENTRIES: _root_.io.grpc.MethodDescriptor[code.obp.grpc.logcache.api.StreamLogCacheRequest, code.obp.grpc.logcache.api.LogCacheEntry] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.obp.grpc.logcache.g1.LogCacheStreamService", "StreamLogCacheEntries"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.logcache.api.StreamLogCacheRequest))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.logcache.api.LogCacheEntry))
      .build()

  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder("code.obp.grpc.logcache.g1.LogCacheStreamService")
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(code.obp.grpc.logcache.api.LogCacheProto.javaDescriptor))
      .addMethod(METHOD_STREAM_LOG_CACHE_ENTRIES)
      .build()

  trait LogCacheStreamService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion = LogCacheStreamService

    /** Server-side stream: pushes new log cache entries for the requested level */
    def streamLogCacheEntries(request: code.obp.grpc.logcache.api.StreamLogCacheRequest,
                              responseObserver: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.logcache.api.LogCacheEntry]): Unit
  }

  object LogCacheStreamService extends _root_.scalapb.grpc.ServiceCompanion[LogCacheStreamService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[LogCacheStreamService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor =
      code.obp.grpc.logcache.api.LogCacheProto.javaDescriptor.getServices().get(0)
  }

  def bindService(serviceImpl: LogCacheStreamService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
    _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
      .addMethod(
        METHOD_STREAM_LOG_CACHE_ENTRIES,
        _root_.io.grpc.stub.ServerCalls.asyncServerStreamingCall(
          new _root_.io.grpc.stub.ServerCalls.ServerStreamingMethod[code.obp.grpc.logcache.api.StreamLogCacheRequest, code.obp.grpc.logcache.api.LogCacheEntry] {
            override def invoke(request: code.obp.grpc.logcache.api.StreamLogCacheRequest,
                                responseObserver: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.logcache.api.LogCacheEntry]): Unit =
              serviceImpl.streamLogCacheEntries(request, responseObserver)
          }))
      .build()
}
