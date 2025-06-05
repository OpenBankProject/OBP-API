package code.cardano


import code.util.Helper.MdcLoggable

import java.io.{File, PrintWriter}
import java.security.MessageDigest
import scala.sys.process._


object CardanoMetadataWriter extends MdcLoggable{

  // Function to generate SHA-256 hash of a string
  def generateHash(transactionData: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(transactionData.getBytes("UTF-8"))
    hashBytes.map("%02x".format(_)).mkString
  }

  // Function to write metadata JSON file
  def writeMetadataFile(transactionHash: String, filePath: String): Unit = {
    val jsonContent =
      s"""
         |{
         |  "674": {
         |    "transaction_hash": "$transactionHash"
         |  }
         |}
         |""".stripMargin

    val file = new File(filePath)
    val writer = new PrintWriter(file)
    writer.write(jsonContent)
    writer.close()
    logger.debug(s"Metadata file written to: $filePath")
  }

  // Function to submit transaction to Cardano
  def submitHashToCardano(transactionHash: String, txIn: String, txOut: String, signingKey: String, network: String): Unit = {
    val metadataFilePath = "metadata.json"

    // Write metadata to file
    writeMetadataFile(transactionHash, metadataFilePath)

    // Build transaction
    val buildCommand = s"cardano-cli transaction build-raw --tx-in $txIn --tx-out $txOut --metadata-json-file $metadataFilePath --out-file tx.raw"
    buildCommand.!

    // Sign transaction
    val signCommand = s"cardano-cli transaction sign --tx-body-file tx.raw --signing-key-file $signingKey --$network --out-file tx.signed"
    signCommand.!

    // Submit transaction
    val submitCommand = s"cardano-cli transaction submit --tx-file tx.signed --$network"
    submitCommand.!

    logger.debug("Transaction submitted to Cardano blockchain.")
  }

  // Example Usage
  def main(args: Array[String]): Unit = {
    val transactionData = "123|100.50|EUR|2025-03-16 12:30:00"
    val transactionHash = generateHash(transactionData)

    val txIn = "8c293647e5cb51c4d29e57e162a0bb4a0500096560ce6899a4b801f2b69f2813:0" // This is a  tx_id:0 ///"YOUR_UTXO_HERE"   // Replace with actual UTXO
    val txOut = "addr_test1qruvtthh7mndxu2ncykn47tksar9yqr3u97dlkq2h2dhzwnf3d755n99t92kp4rydpzgv7wmx4nx2j0zzz0g802qvadqtczjhn:1234" // "YOUR_RECEIVER_ADDRESS+LOVELACE" // Replace with receiver address and amount
    val signingKey = "payment.skey" // Path to your signing key file
    val network = "--testnet-magic" // "--testnet-magic 1097911063" // Use --mainnet for mainnet transactions

    submitHashToCardano(transactionHash, txIn, txOut, signingKey, network)
  }
}

// TODO
// Create second wallet
// Find version of Pre Prod i'm running
// Get CLI for that version
// Use faucet to get funds





/*
import com.bloxbean.cardano.client.account.Account
import com.bloxbean.cardano.client.api.UtxoSupplier
import com.bloxbean.cardano.client.backend.impl.local.LocalNodeBackendService
import com.bloxbean.cardano.client.backend.api.TransactionService
import com.bloxbean.cardano.client.backend.api.UtxoService
import com.bloxbean.cardano.client.backend.model.Utxo
import com.bloxbean.cardano.client.common.model.Network
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata
import com.bloxbean.cardano.client.transaction.spec.Transaction
import com.bloxbean.cardano.client.api.helper.TransactionBuilder
import java.security.MessageDigest

object CardanoMetadataWriter {

  // Function to generate SHA-256 hash
  def generateHash(transactionData: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(transactionData.getBytes("UTF-8"))
    hashBytes.map("%02x".format(_)).mkString
  }

  // Function to submit metadata transaction
  def submitMetadataToCardano(mnemonic: String, transactionData: String): Unit = {
    val network = Network.TESTNET  // Change to Network.MAINNET for mainnet

    // Load Daedalus wallet from mnemonic
    val account = new Account(network, mnemonic)

    // Generate hash of transaction data
    val transactionHash = generateHash(transactionData)

    logger.debug(s"Generated Hash: $transactionHash")

    // Create metadata object
    val metadata = new CBORMetadata()
    metadata.put("674", Map("transaction_hash" -> transactionHash))

    // Initialize local Cardano node backend
    val backendService = new LocalNodeBackendService("http://localhost:8080")
    val transactionService: TransactionService = backendService.getTransactionService
    val utxoService: UtxoService = backendService.getUtxoService

    // Get available UTXOs from the wallet
    val utxos: java.util.List[Utxo] = utxoService.getUtxos(account.baseAddress, 1, 10).getValue

    if (utxos.isEmpty) {
      logger.debug("No UTXOs found. Please fund your wallet.")
      return
    }

    // Build transaction
    val transaction = TransactionBuilder.create()
      .account(account)
      .metadata(metadata)
      .utxos(utxos)
      .changeAddress(account.baseAddress)
      .network(network)
      .build()

    // Sign transaction
    val signedTransaction: Transaction = account.sign(transaction)

    // Submit transaction
    val txHash: String = transactionService.submitTransaction(signedTransaction).getValue
    logger.debug(s"Transaction submitted! TxHash: $txHash")
  }

  // Main method
  def main(args: Array[String]): Unit = {
    val mnemonic = "YOUR_12_OR_24_WORD_MNEMONIC_HERE"
    val transactionData = "123|100.50|USD|2025-03-16 12:30:00"

    submitMetadataToCardano(mnemonic, transactionData)
  }
}
*/
