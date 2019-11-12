package code.obp.grpc.api

object ObpServiceGrpc {
  val METHOD_GET_BANKS: _root_.io.grpc.MethodDescriptor[com.google.protobuf.empty.Empty, code.obp.grpc.api.BanksJson400Grpc] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.obp.grpc.ObpService", "getBanks"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(com.google.protobuf.empty.Empty))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.BanksJson400Grpc))
      .build()
  
  val METHOD_GET_PRIVATE_ACCOUNTS_AT_ONE_BANK: _root_.io.grpc.MethodDescriptor[code.obp.grpc.api.BankIdUserIdGrpc, code.obp.grpc.api.AccountsGrpc] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.obp.grpc.ObpService", "getPrivateAccountsAtOneBank"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.BankIdUserIdGrpc))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.AccountsGrpc))
      .build()
  
  val METHOD_GET_BANK_ACCOUNTS_BALANCES: _root_.io.grpc.MethodDescriptor[code.obp.grpc.api.BankIdGrpc, code.obp.grpc.api.AccountsBalancesV310JsonGrpc] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.obp.grpc.ObpService", "getBankAccountsBalances"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.BankIdGrpc))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.AccountsBalancesV310JsonGrpc))
      .build()
  
  val METHOD_GET_CORE_TRANSACTIONS_FOR_BANK_ACCOUNT: _root_.io.grpc.MethodDescriptor[code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc, code.obp.grpc.api.CoreTransactionsJsonV300Grpc] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("code.obp.grpc.ObpService", "getCoreTransactionsForBankAccount"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc))
      .setResponseMarshaller(new scalapb.grpc.Marshaller(code.obp.grpc.api.CoreTransactionsJsonV300Grpc))
      .build()
  
  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder("code.obp.grpc.ObpService")
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(code.obp.grpc.api.ApiProto.javaDescriptor))
      .addMethod(METHOD_GET_BANKS)
      .addMethod(METHOD_GET_PRIVATE_ACCOUNTS_AT_ONE_BANK)
      .addMethod(METHOD_GET_BANK_ACCOUNTS_BALANCES)
      .addMethod(METHOD_GET_CORE_TRANSACTIONS_FOR_BANK_ACCOUNT)
      .build()
  
  trait ObpService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion = ObpService
    def getBanks(request: com.google.protobuf.empty.Empty): scala.concurrent.Future[code.obp.grpc.api.BanksJson400Grpc]
    def getPrivateAccountsAtOneBank(request: code.obp.grpc.api.BankIdUserIdGrpc): scala.concurrent.Future[code.obp.grpc.api.AccountsGrpc]
    def getBankAccountsBalances(request: code.obp.grpc.api.BankIdGrpc): scala.concurrent.Future[code.obp.grpc.api.AccountsBalancesV310JsonGrpc]
    def getCoreTransactionsForBankAccount(request: code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc): scala.concurrent.Future[code.obp.grpc.api.CoreTransactionsJsonV300Grpc]
  }
  
  object ObpService extends _root_.scalapb.grpc.ServiceCompanion[ObpService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[ObpService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = code.obp.grpc.api.ApiProto.javaDescriptor.getServices().get(0)
  }
  
  trait ObpServiceBlockingClient {
    def serviceCompanion = ObpService
    def getBanks(request: com.google.protobuf.empty.Empty): code.obp.grpc.api.BanksJson400Grpc
    def getPrivateAccountsAtOneBank(request: code.obp.grpc.api.BankIdUserIdGrpc): code.obp.grpc.api.AccountsGrpc
    def getBankAccountsBalances(request: code.obp.grpc.api.BankIdGrpc): code.obp.grpc.api.AccountsBalancesV310JsonGrpc
    def getCoreTransactionsForBankAccount(request: code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc): code.obp.grpc.api.CoreTransactionsJsonV300Grpc
  }
  
  class ObpServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[ObpServiceBlockingStub](channel, options) with ObpServiceBlockingClient {
    override def getBanks(request: com.google.protobuf.empty.Empty): code.obp.grpc.api.BanksJson400Grpc = {
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_GET_BANKS, options), request)
    }
    
    override def getPrivateAccountsAtOneBank(request: code.obp.grpc.api.BankIdUserIdGrpc): code.obp.grpc.api.AccountsGrpc = {
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_GET_PRIVATE_ACCOUNTS_AT_ONE_BANK, options), request)
    }
    
    override def getBankAccountsBalances(request: code.obp.grpc.api.BankIdGrpc): code.obp.grpc.api.AccountsBalancesV310JsonGrpc = {
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_GET_BANK_ACCOUNTS_BALANCES, options), request)
    }
    
    override def getCoreTransactionsForBankAccount(request: code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc): code.obp.grpc.api.CoreTransactionsJsonV300Grpc = {
      _root_.io.grpc.stub.ClientCalls.blockingUnaryCall(channel.newCall(METHOD_GET_CORE_TRANSACTIONS_FOR_BANK_ACCOUNT, options), request)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ObpServiceBlockingStub = new ObpServiceBlockingStub(channel, options)
  }
  
  class ObpServiceStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[ObpServiceStub](channel, options) with ObpService {
    override def getBanks(request: com.google.protobuf.empty.Empty): scala.concurrent.Future[code.obp.grpc.api.BanksJson400Grpc] = {
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_GET_BANKS, options), request))
    }
    
    override def getPrivateAccountsAtOneBank(request: code.obp.grpc.api.BankIdUserIdGrpc): scala.concurrent.Future[code.obp.grpc.api.AccountsGrpc] = {
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_GET_PRIVATE_ACCOUNTS_AT_ONE_BANK, options), request))
    }
    
    override def getBankAccountsBalances(request: code.obp.grpc.api.BankIdGrpc): scala.concurrent.Future[code.obp.grpc.api.AccountsBalancesV310JsonGrpc] = {
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_GET_BANK_ACCOUNTS_BALANCES, options), request))
    }
    
    override def getCoreTransactionsForBankAccount(request: code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc): scala.concurrent.Future[code.obp.grpc.api.CoreTransactionsJsonV300Grpc] = {
      scalapb.grpc.Grpc.guavaFuture2ScalaFuture(_root_.io.grpc.stub.ClientCalls.futureUnaryCall(channel.newCall(METHOD_GET_CORE_TRANSACTIONS_FOR_BANK_ACCOUNT, options), request))
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ObpServiceStub = new ObpServiceStub(channel, options)
  }
  
  def bindService(serviceImpl: ObpService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
    _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
    .addMethod(
      METHOD_GET_BANKS,
      _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[com.google.protobuf.empty.Empty, code.obp.grpc.api.BanksJson400Grpc] {
        override def invoke(request: com.google.protobuf.empty.Empty, observer: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.api.BanksJson400Grpc]): Unit =
          serviceImpl.getBanks(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
      }))
    .addMethod(
      METHOD_GET_PRIVATE_ACCOUNTS_AT_ONE_BANK,
      _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[code.obp.grpc.api.BankIdUserIdGrpc, code.obp.grpc.api.AccountsGrpc] {
        override def invoke(request: code.obp.grpc.api.BankIdUserIdGrpc, observer: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.api.AccountsGrpc]): Unit =
          serviceImpl.getPrivateAccountsAtOneBank(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
      }))
    .addMethod(
      METHOD_GET_BANK_ACCOUNTS_BALANCES,
      _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[code.obp.grpc.api.BankIdGrpc, code.obp.grpc.api.AccountsBalancesV310JsonGrpc] {
        override def invoke(request: code.obp.grpc.api.BankIdGrpc, observer: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.api.AccountsBalancesV310JsonGrpc]): Unit =
          serviceImpl.getBankAccountsBalances(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
      }))
    .addMethod(
      METHOD_GET_CORE_TRANSACTIONS_FOR_BANK_ACCOUNT,
      _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc, code.obp.grpc.api.CoreTransactionsJsonV300Grpc] {
        override def invoke(request: code.obp.grpc.api.BankIdAccountIdAndUserIdGrpc, observer: _root_.io.grpc.stub.StreamObserver[code.obp.grpc.api.CoreTransactionsJsonV300Grpc]): Unit =
          serviceImpl.getCoreTransactionsForBankAccount(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
      }))
    .build()
  
  def blockingStub(channel: _root_.io.grpc.Channel): ObpServiceBlockingStub = new ObpServiceBlockingStub(channel)
  
  def stub(channel: _root_.io.grpc.Channel): ObpServiceStub = new ObpServiceStub(channel)
  
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = code.obp.grpc.api.ApiProto.javaDescriptor.getServices().get(0)
  
}