package code.api.test

import org.scalatest._
import dispatch._
import dispatch.liftjson.Js._
import net.liftweb.json.NoTypeHints
import net.liftweb.json.JsonAST.{JValue, JObject}
import _root_.net.liftweb.json.Serialization.write
import net.liftweb.common._
import org.mortbay.jetty.Connector
import org.mortbay.jetty.Server
import org.mortbay.jetty.nio.SelectChannelConnector
import org.mortbay.jetty.webapp.WebAppContext
import net.liftweb.json.Serialization
import org.junit.runner.RunWith
import net.liftweb.mongodb._
import net.liftweb.util.Props
import code.model.dataAccess.{OBPUser, Privilege, HostedAccount, Account, HostedBank}
import java.util.Date
import _root_.net.liftweb.util._
import Helpers._
import org.bson.types.ObjectId
import scala.util.Random._
case class APIResponse(code: Int, body: JValue)

trait ServerSetup extends FeatureSpec
  with BeforeAndAfterAll with GivenWhenThen
  with ShouldMatchers with Loggable{

  val server = ServerSetup

  implicit val formats = Serialization.formats(NoTypeHints)

  val h = new Http with thread.Safety
  val baseRequest = (:/(server.host, Integer.valueOf(server.port)))


  override def beforeAll() = {
    implicit val dateFormats = net.liftweb.json.DefaultFormats
    //create fake data for the tests

    //fake banks
    val banks = for{i <- 0 until 3} yield {
        HostedBank.createRecord.
          name(randomString(5)).
          alias(randomString(5)).
          permalink(randomString(5)).
          save
      }

    //fake bank accounts
    val accounts = banks.flatMap(bank => {
      for { i <- 0 until 2 } yield {
          Account.createRecord.
            balance(0).
            anonAccess(true).
            holder(randomString(4)).
            number(randomString(4)).
            kind(randomString(4)).
            name(randomString(4)).
            permalink(randomString(4)).
            bankID(new ObjectId(bank.id.get.toString)).
            label(randomString(4)).
            currency(randomString(4)).
            save
        }
      })

    val hostedAccounts = accounts.map(account => {
      HostedAccount.create.accountID(account.id.get.toString).saveMe
    })

    case class OBPTransactionWrapper(
      obp_transaction: OBPTransaction)

    case class OBPTransaction(
      this_account: OBPAccount,
      other_account: OBPAccount,
      details: OBPDetails)

    case class OBPAccount(
      holder: String,
      number: String,
      kind: String,
      bank: OBPBank)

    case class OBPBank(
      IBAN: String,
      national_identifier: String,
      name: String)

    case class OBPDetails(
      type_en: String,
      type_de: String,
      posted: Date,
      completed: Date,
      new_balance: OBPAmount,
      value: OBPAmount,
      label: String,
      other_data: String)

    case class OBPAmount(
      currency: String,
      amount: String) {
      override def toString = "OBPAmount(" + currency + ",***)"
    }

    //fake transactions
    val postTransaction = baseRequest / "api"/ "transactions" <<? List(("secret", Props.get("importer_secret","")))
    accounts.foreach(account => {
      val transactions =
        for{i <- 0 until 10} yield{
          val thisAccountBank = OBPBank("", "", account.bankName)
          val thisAccount = OBPAccount(account.holder.get, account.number.get, account.kind.get, thisAccountBank)
          val otherAccountBank = OBPBank("", "", randomString(5))
          val otherAccount = OBPAccount(randomString(5), randomString(5), randomString(5), otherAccountBank)
          val balance = OBPAmount(randomString(3), nextInt(100).toString)
          val value = OBPAmount(randomString(3), nextInt(100).toString)
          val details = OBPDetails(randomString(5), randomString(5), now, now, balance, value, randomString(10), "")
          val obpTransaction = OBPTransaction(thisAccount, otherAccount, details)
          OBPTransactionWrapper(obpTransaction)
        }
      val reply = makePostRequest(postTransaction, write(transactions))
      if(reply.code != 200)
      {
        logger.warn("could not post transactions: " + reply.body)
      }
    })
    specificSetup()
  }

  //this method is to run a specific behavior before running each test class
  def specificSetup() = {
  }

  override def afterAll() = {
    //drop the Database after the tests
    MongoDB.getDb(DefaultMongoIdentifier).map(_.dropDatabase())
  }
  /**
   this method do a post request given a URL, a JSON and an optional Headers Map
  */
  def makePostRequest(req: Request, json: String = "", headers : Map[String,String] = Map()) : h.HttpPackage[APIResponse] = {
    val jsonReq = req << (json, "application/json") <:< headers
    val jsonHandler = jsonReq ># {json => json}
    h x jsonHandler{
       case (code, _, _, json) => APIResponse(code, json())
    }
  }

  def makePutRequest(req: Request, json: String, headers : Map[String,String] = Map(("Content-type","application/json"))) : h.HttpPackage[APIResponse] = {
    val jsonReq = req <<< json <:< headers
    val jsonHandler = jsonReq ># {json => json}
    h x jsonHandler{
       case (code, _, _, json) => APIResponse(code, json())
    }
  }

  /**
  * this method do a post request given a URL
  */
  def makeGetRequest(req: Request, headers : Map[String,String] = Map()) : h.HttpPackage[APIResponse] = {
    val jsonReq = req <:< headers
    val jsonHandler = jsonReq ># {json => json}
    h x jsonHandler{
       case (code, _, _, json) => APIResponse(code, json())
    }
  }

  /**
  * this method do a delete request given a URL
  */
  def makeDeleteRequest(req: Request, headers : Map[String,String] = Map()) : h.HttpPackage[APIResponse] = {
    val jsonReq = (req <:< headers).DELETE
    val jsonHandler = jsonReq.>|
    h x jsonHandler{
       case (code, _, _, _) => APIResponse(code, null)
    }
  }
}

object ServerSetup {

  val host = "localhost"
  val port = 8000
  val server = new Server
  val scc = new SelectChannelConnector
  scc.setPort(port)
  server.setConnectors(Array(scc))

  val context = new WebAppContext()
  context.setServer(server)
  context.setContextPath("/")
  context.setWar("src/main/webapp")

  server.addHandler(context)

  server.start()
}