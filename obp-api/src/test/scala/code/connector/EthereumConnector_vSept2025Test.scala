//package code.connector
//
//import code.api.util.ErrorMessages
//import code.api.v5_1_0.V510ServerSetup
//import code.api.v6_0_0.TransactionRequestBodyEthereumJsonV600
//import code.bankconnectors.ethereum.EthereumConnector_vSept2025
//import com.github.dwickern.macros.NameOf
//import com.openbankproject.commons.model._
//import net.liftweb.common.Full
//import org.scalatest.Tag
//
//import scala.concurrent.Await
//import scala.concurrent.duration._
///**
//  * Minimal unit test to invoke makePaymentv210 against local Anvil.
//  * Assumptions:
//  *  - ethereum.rpc.url points to http://127.0.0.1:8545
//  *  - The RPC allows eth_sendTransaction (Anvil unlocked accounts)
//  *  - We pass BankAccount stubs with accountId holding 0x addresses
//  */
//class EthereumConnector_vSept2025Test extends V510ServerSetup{
//
//  object ConnectorTestTag extends Tag(NameOf.nameOfType[EthereumConnector_vSept2025Test])
//  
//  object StubConnector extends EthereumConnector_vSept2025
//
//  private case class StubBankAccount(id: String) extends BankAccount {
//    override val accountId: AccountId = AccountId(id)
//    override val bankId: BankId = BankId("bank-x")
//    override val accountType: String = "checking"
//    override val balance: BigDecimal = BigDecimal(0)
//    override val currency: String = "ETH"
//    override val name: String = "stub"
//    override val label: String = "stub"
//    override val number: String = "stub"
//    override val lastUpdate: java.util.Date = new java.util.Date()
//    override val accountHolder: String = "stub"
//    override val accountRoutings: List[AccountRouting] = Nil
//    override def branchId: String = "stub"
//    override def accountRules: List[AccountRule] = Nil
//  }
//
//  feature("Anvil local Ethereum Node, need to start the Anvil, and set `ethereum.rpc.url=http://127.0.0.1:8545` in props, and prepare the from, to account") {
////    setPropsValues("ethereum.rpc.url"-> "https://nkotb.openbankproject.com")
//    scenario("successful case", ConnectorTestTag) {
//      val from = StubBankAccount("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
//      val to = StubBankAccount("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
//      val amount = BigDecimal("0.0001")
//
//      val trxBody = TransactionRequestBodyEthereumJsonV600(
//        to = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
//        value = AmountOfMoneyJsonV121("ETH", amount.toString),
//        description="test"
//      )
//
//      ////       This is only for testing; you can comment it out when the local Anvil is running.
////      val resF = StubConnector.makePaymentv210(
////        from,
////        to,
////        TransactionRequestId(java.util.UUID.randomUUID().toString),
////        trxBody,
////        amount,
////        "test", 
////        TransactionRequestType("ETH_SEND_TRANSACTION"),
////        "none",
////        None
////      )
////
////      val res = Await.result(resF, 10.seconds)
////      res._1 shouldBe a[Full[_]]
////      val txId = res._1.openOrThrowException(ErrorMessages.UnknownError)
////      txId.value should startWith("0x")
//    }
//  }
//  
//  feature("need to start the Anvil, and set `ethereum.rpc.url=https://nkotb.openbankproject.com` in props, and prepare the from, to accounts and the rawTx") {
////    setPropsValues("ethereum.rpc.url"-> "http://127.0.0.1:8545")
//    scenario("successful case", ConnectorTestTag) {
//
//      val from = StubBankAccount("0xf17f52151EbEF6C7334FAD080c5704D77216b732")
//      val to   = StubBankAccount("0x627306090abaB3A6e1400e9345bC60c78a8BEf57")
//      val amount = BigDecimal("0.0001")
//
//      // Use a fixed rawTx variable for testing eth_sendRawTransaction path (no external params)
//      val rawTx = "0xf86a058203e882520894627306090abab3a6e1400e9345bc60c78a8bef57872386f26fc1000080820ff5a06de864bc825c4e976f5c432d0a57e3b3c9e19ec9843b5bb893a78a1389be650ea04fa063aba3984ff97e6454595f170e17e117f046568960aacf96f223c71ca0e2"
//      
//      val trxBody = TransactionRequestBodyEthereumJsonV600(
//        params= Some(rawTx),
//        to = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
//        value = AmountOfMoneyJsonV121("ETH", amount.toString),
//        description="test"
//      )
////
////      // Enable integration test against private chain
////      val resF = StubConnector.makePaymentv210(
////        from,
////        to,
////        TransactionRequestId(java.util.UUID.randomUUID().toString),
////        trxBody,
////        amount,
////        "test",
////        TransactionRequestType("ETH_SEND_RAW_TRANSACTION") ,
////        "none",
////        None
////      )
////
////      val res = Await.result(resF, 30.seconds)
////      res._1 shouldBe a [Full[_]]
////      val txId = res._1.openOrThrowException(ErrorMessages.UnknownError)
////      txId.value should startWith ("0x")
//    }
//  }
//  
//}
//
//
