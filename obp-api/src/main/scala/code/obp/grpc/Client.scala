/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.obp.grpc

import scala.language.existentials
import code.obp.grpc.api._
import com.google.protobuf.empty.Empty
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

object Client extends App {
  private val channelBuilder = ManagedChannelBuilder.forAddress("demo.openbankproject.com", ObpGrpcServer.port)
    .usePlaintext()
    .asInstanceOf[ManagedChannelBuilder[_]]
  val channel: ManagedChannel = channelBuilder.build()

  private val obpService: ObpServiceGrpc.ObpServiceBlockingStub = ObpServiceGrpc.blockingStub(channel)
  // get all banks
  private val banks: BanksJson400Grpc = obpService.getBanks(Empty.defaultInstance)
  println(banks)

  // Temporarily disabled — see api.proto, ApiProto.scala javaDescriptor filter,
  // ObpServiceGrpc.scala, and ObpGrpcServer.scala for the matching changes.
  //
  //// get accounts according bankId and userId
  //private val bankIdUserIdGrpc = BankIdUserIdGrpc("dmo.07.de.de", "0986f84c-78ce-4ce9-a3b7-fa2451acd882")
  //private val accounts: AccountsGrpc = obpService.getPrivateAccountsAtOneBank(bankIdUserIdGrpc)
  //println(accounts)
  //
  ////get accounts by bankId, accountId and userId
  //private val bankIdAccountIdAndUserId = BankIdAccountIdAndUserIdGrpc("psd201-bank-y--uk", "my_account_id", "4850d4c3-220a-4a72-9d3c-eeeacaf4b63b")
  //private val transactionsJsonV300Grpc: CoreTransactionsJsonV300Grpc = obpService.getCoreTransactionsForBankAccount(bankIdAccountIdAndUserId)
  //println(transactionsJsonV300Grpc)
}
