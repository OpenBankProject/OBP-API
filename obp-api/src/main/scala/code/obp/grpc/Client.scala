package code.obp.grpc

import code.obp.grpc.api._
import com.google.protobuf.empty.Empty
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object Client extends App {
  private val channelBuilder = ManagedChannelBuilder.forAddress("demo.openbankproject.com", HelloWorldServer.port)
    .usePlaintext()
    .asInstanceOf[ManagedChannelBuilder[_]]
  val channel: ManagedChannel = channelBuilder.build()

  private val obpService: ObpServiceGrpc.ObpServiceBlockingStub = ObpServiceGrpc.blockingStub(channel)
  // get all banks
  private val banks: BanksJson400Grpc = obpService.getBanks(Empty.defaultInstance)
  println(banks)

  // get accounts according bankId and userId
  private val bankIdUserIdGrpc = BankIdUserIdGrpc("dmo.07.de.de", "0986f84c-78ce-4ce9-a3b7-fa2451acd882")
  private val accounts: AccountsGrpc = obpService.getPrivateAccountsAtOneBank(bankIdUserIdGrpc)
  println(accounts)

  //get accounts by bankId, accountId and userId
  private val bankIdAccountIdAndUserId = BankIdAccountIdAndUserIdGrpc("psd201-bank-y--uk", "my_account_id", "4850d4c3-220a-4a72-9d3c-eeeacaf4b63b")
  private val transactionsJsonV300Grpc: CoreTransactionsJsonV300Grpc = obpService.getCoreTransactionsForBankAccount(bankIdAccountIdAndUserId)
  println(transactionsJsonV300Grpc)
}
