// Hand-written to match the scalapb-generated shape used elsewhere in the
// gRPC layer. No protoc plugin is wired into the Maven build.
//
// Protofile syntax: PROTO3

package code.obp.grpc.metricsstream.api

object MetricsStreamServiceGrpc {

  val METHOD_STREAM_METRICS: _root_.io.grpc.MethodDescriptor[code.obp.grpc.metricsstream.api.StreamMetricsRequest, code.obp.grpc.metricsstream.api.MetricEvent] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.obp.grpc.metricsstream.g1.MetricsStreamService", "StreamMetrics"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.metricsstream.api.StreamMetricsRequest))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.metricsstream.api.MetricEvent))
      .build()

  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder("code.obp.grpc.metricsstream.g1.MetricsStreamService")
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(code.obp.grpc.metricsstream.api.MetricsStreamProto.javaDescriptor))
      .addMethod(METHOD_STREAM_METRICS)
      .build()

  trait MetricsStreamService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion = MetricsStreamService

    /** Server-side stream: pushes new API metrics as they are written */
    def streamMetrics(request: code.obp.grpc.metricsstream.api.StreamMetricsRequest,
                      responseObserver: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.metricsstream.api.MetricEvent]): Unit
  }

  object MetricsStreamService extends _root_.scalapb.grpc.ServiceCompanion[MetricsStreamService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[MetricsStreamService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor =
      code.obp.grpc.metricsstream.api.MetricsStreamProto.javaDescriptor.getServices().get(0)
  }

  def bindService(serviceImpl: MetricsStreamService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
    _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
      .addMethod(
        METHOD_STREAM_METRICS,
        _root_.io.grpc.stub.ServerCalls.asyncServerStreamingCall(
          new _root_.io.grpc.stub.ServerCalls.ServerStreamingMethod[code.obp.grpc.metricsstream.api.StreamMetricsRequest, code.obp.grpc.metricsstream.api.MetricEvent] {
            override def invoke(request: code.obp.grpc.metricsstream.api.StreamMetricsRequest,
                                responseObserver: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.metricsstream.api.MetricEvent]): Unit =
              serviceImpl.streamMetrics(request, responseObserver)
          }))
      .build()
}
