/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */

package code.api.test

import org.scalatest._
import dispatch._, Defaults._
import net.liftweb.json.NoTypeHints
import net.liftweb.json.JsonAST.{JValue, JObject}
import _root_.net.liftweb.json.Serialization.write
import net.liftweb.json.parse
import net.liftweb.common._
import org.mortbay.jetty.Connector
import org.mortbay.jetty.Server
import org.mortbay.jetty.nio.SelectChannelConnector
import org.mortbay.jetty.webapp.WebAppContext
import net.liftweb.json.Serialization
import org.junit.runner.RunWith
import net.liftweb.mongodb._
import net.liftweb.util.Props
import code.model.dataAccess._
import java.util.Date
import _root_.net.liftweb.util._
import Helpers._
import org.bson.types.ObjectId
import scala.util.Random._
import scala.math.BigDecimal
import BigDecimal._
import scala.concurrent.duration._
import scala.concurrent.Await

case class APIResponse(code: Int, body: JValue)

trait ServerSetup extends FeatureSpec
  with BeforeAndAfterAll with GivenWhenThen
  with ShouldMatchers with Loggable{

  val server = ServerSetup
  implicit val formats = Serialization.formats(NoTypeHints)
  val h = Http
  def baseRequest = host(server.host, server.port)

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

    accounts.map(account => {
      val hostedaccount =
        HostedAccount.
          create.
          accountID(account.id.get.toString).
          saveMe
      ownerView(hostedaccount)
      publicView(hostedaccount)
      randomView(hostedaccount)
      hostedaccount
    })

    //fake transactions
    accounts.foreach(account => {
      for(i <- 0 until 10){
        val thisAccountBank = OBPBank.createRecord.
          IBAN(randomString(5)).
          national_identifier(randomString(5)).
          name(account.bankName)

        val thisAccount = OBPAccount.createRecord.
          holder(account.holder.get).
          number(account.number.get).
          kind(account.kind.get).
          bank(thisAccountBank)

        val otherAccountBank = OBPBank.createRecord.
          IBAN(randomString(5)).
          national_identifier(randomString(5)).
          name(randomString(5))

        val otherAccount = OBPAccount.createRecord.
          holder(randomString(5)).
          number(randomString(5)).
          kind(randomString(5)).
          bank(otherAccountBank)

        val transactionAmount = BigDecimal(nextDouble * 1000).setScale(2,RoundingMode.HALF_UP)

        val newBalance : OBPBalance = OBPBalance.createRecord.
          currency(account.currency.get).
          amount(account.balance.get + transactionAmount)

        val newValue : OBPValue = OBPValue.createRecord.
          currency(account.currency.get).
          amount(transactionAmount)

        val details = OBPDetails.createRecord.
          kind(randomString(5)).
          posted(now).
          other_data(randomString(5)).
          new_balance(newBalance).
          value(newValue).
          completed(now).
          label(randomString(5))

        val transaction = OBPTransaction.createRecord.
          this_account(thisAccount).
          other_account(otherAccount).
          details(details)

        val env = OBPEnvelope.createRecord.
          obp_transaction(transaction).save
        account.balance(newBalance.amount.get).lastUpdate(now).save
        env.createAliases
        env.save
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
    ViewImpl.findAll.map(_.delete_!)
    ViewPrivileges.findAll.map(_.delete_!)
    HostedAccount.findAll.map(_.delete_!)
  }

  private def getAPIResponse(req : Req) : APIResponse = {
    Await.result(
      for(response <- Http(req > as.Response(p => p)))
      yield
      {
        val body = if(response.getResponseBody().isEmpty) "{}" else response.getResponseBody()
        APIResponse(response.getStatusCode, parse(body))
      }
    , Duration.Inf)
  }

  /**
   this method do a post request given a URL, a JSON and an optional Headers Map
  */
  def makePostRequest(req: Req, json: String = ""): APIResponse = {
    req.addHeader("Content-Type", "application/json")
    req.setBody(json)
    val jsonReq = (req).POST
    getAPIResponse(jsonReq)
  }

  def makePutRequest(req: Req, json: String = "") : APIResponse = {
    req.addHeader("Content-Type", "application/json")
    req.setBody(json)
    val jsonReq = (req).PUT
    getAPIResponse(jsonReq)
  }

  /**
  * this method do a post request given a URL
  */
  def makeGetRequest(req: Req) : APIResponse = {
    val jsonReq = req.GET
    getAPIResponse(jsonReq)
  }

  /**
  * this method do a delete request given a URL
  */
  def makeDeleteRequest(req: Req) : APIResponse = {
    val jsonReq = req.DELETE
    getAPIResponse(jsonReq)
  }

  def ownerView(account: HostedAccount) =
    ViewImpl.create.
    name_("Owner").
    description_(randomString(3)).
    permalink_("owner").
    isPublic_(false).
    account(account).
    usePrivateAliasIfOneExists_(false).
    usePublicAliasIfOneExists_(false).
    hideOtherAccountMetadataIfAlias_(false).
    canSeeTransactionThisBankAccount_(true).
    canSeeTransactionOtherBankAccount_(true).
    canSeeTransactionMetadata_(true).
    canSeeTransactionDescription_(true).
    canSeeTransactionAmount_(true).
    canSeeTransactionType_(true).
    canSeeTransactionCurrency_(true).
    canSeeTransactionStartDate_(true).
    canSeeTransactionFinishDate_(true).
    canSeeTransactionBalance_(true).
    canSeeComments_(true).
    canSeeOwnerComment_(true).
    canSeeTags_(true).
    canSeeImages_(true).
    canSeeBankAccountOwners_(true).
    canSeeBankAccountType_(true).
    canSeeBankAccountBalance_(true).
    canSeeBankAccountCurrency_(true).
    canSeeBankAccountLabel_(true).
    canSeeBankAccountNationalIdentifier_(true).
    canSeeBankAccountSwift_bic_(true).
    canSeeBankAccountIban_(true).
    canSeeBankAccountNumber_(true).
    canSeeBankAccountBankName_(true).
    canSeeBankAccountBankPermalink_(true).
    canSeeOtherAccountNationalIdentifier_(true).
    canSeeOtherAccountSWIFT_BIC_(true).
    canSeeOtherAccountIBAN_ (true).
    canSeeOtherAccountBankName_(true).
    canSeeOtherAccountNumber_(true).
    canSeeOtherAccountMetadata_(true).
    canSeeOtherAccountKind_(true).
    canSeeMoreInfo_(true).
    canSeeUrl_(true).
    canSeeImageUrl_(true).
    canSeeOpenCorporatesUrl_(true).
    canSeeCorporateLocation_(true).
    canSeePhysicalLocation_(true).
    canSeePublicAlias_(true).
    canSeePrivateAlias_(true).
    canAddMoreInfo_(true).
    canAddURL_(true).
    canAddImageURL_(true).
    canAddOpenCorporatesUrl_(true).
    canAddCorporateLocation_(true).
    canAddPhysicalLocation_(true).
    canAddPublicAlias_(true).
    canAddPrivateAlias_(true).
    canDeleteCorporateLocation_(true).
    canDeletePhysicalLocation_(true).
    canEditOwnerComment_(true).
    canAddComment_(true).
    canDeleteComment_(true).
    canAddTag_(true).
    canDeleteTag_(true).
    canAddImage_(true).
    canDeleteImage_(true).
    canAddWhereTag_(true).
    canSeeWhereTag_(true).
    canDeleteWhereTag_(true).
    save

  def publicView(account: HostedAccount) =
    ViewImpl.create.
    name_("Public").
    description_(randomString(3)).
    permalink_("public").
    isPublic_(true).
    account(account).
    usePrivateAliasIfOneExists_(false).
    usePublicAliasIfOneExists_(true).
    hideOtherAccountMetadataIfAlias_(true).
    canSeeTransactionThisBankAccount_(true).
    canSeeTransactionOtherBankAccount_(true).
    canSeeTransactionMetadata_(true).
    canSeeTransactionDescription_(true).
    canSeeTransactionAmount_(true).
    canSeeTransactionType_(true).
    canSeeTransactionCurrency_(true).
    canSeeTransactionStartDate_(true).
    canSeeTransactionFinishDate_(true).
    canSeeTransactionBalance_(true).
    canSeeComments_(true).
    canSeeOwnerComment_(true).
    canSeeTags_(true).
    canSeeImages_(true).
    canSeeBankAccountOwners_(true).
    canSeeBankAccountType_(true).
    canSeeBankAccountBalance_(true).
    canSeeBankAccountCurrency_(true).
    canSeeBankAccountLabel_(true).
    canSeeBankAccountNationalIdentifier_(true).
    canSeeBankAccountSwift_bic_(true).
    canSeeBankAccountIban_(true).
    canSeeBankAccountNumber_(true).
    canSeeBankAccountBankName_(true).
    canSeeBankAccountBankPermalink_(true).
    canSeeOtherAccountNationalIdentifier_(true).
    canSeeOtherAccountSWIFT_BIC_(true).
    canSeeOtherAccountIBAN_ (true).
    canSeeOtherAccountBankName_(true).
    canSeeOtherAccountNumber_(true).
    canSeeOtherAccountMetadata_(true).
    canSeeOtherAccountKind_(true).
    canSeeMoreInfo_(true).
    canSeeUrl_(true).
    canSeeImageUrl_(true).
    canSeeOpenCorporatesUrl_(true).
    canSeeCorporateLocation_(true).
    canSeePhysicalLocation_(true).
    canSeePublicAlias_(true).
    canSeePrivateAlias_(true).
    canAddMoreInfo_(true).
    canAddURL_(true).
    canAddImageURL_(true).
    canAddOpenCorporatesUrl_(true).
    canAddCorporateLocation_(true).
    canAddPhysicalLocation_(true).
    canAddPublicAlias_(true).
    canAddPrivateAlias_(true).
    canDeleteCorporateLocation_(true).
    canDeletePhysicalLocation_(true).
    canEditOwnerComment_(true).
    canAddComment_(true).
    canDeleteComment_(true).
    canAddTag_(true).
    canDeleteTag_(true).
    canAddImage_(true).
    canDeleteImage_(true).
    canAddWhereTag_(true).
    canSeeWhereTag_(true).
    canDeleteWhereTag_(true).
    save

  def randomView(account: HostedAccount) =
    ViewImpl.create.
    name_(randomString(5)).
    description_(randomString(3)).
    permalink_(randomString(3)).
    isPublic_(false).
    account(account).
    usePrivateAliasIfOneExists_(false).
    usePublicAliasIfOneExists_(false).
    hideOtherAccountMetadataIfAlias_(false).
    canSeeTransactionThisBankAccount_(true).
    canSeeTransactionOtherBankAccount_(true).
    canSeeTransactionMetadata_(true).
    canSeeTransactionDescription_(true).
    canSeeTransactionAmount_(true).
    canSeeTransactionType_(true).
    canSeeTransactionCurrency_(true).
    canSeeTransactionStartDate_(true).
    canSeeTransactionFinishDate_(true).
    canSeeTransactionBalance_(true).
    canSeeComments_(true).
    canSeeOwnerComment_(true).
    canSeeTags_(true).
    canSeeImages_(true).
    canSeeBankAccountOwners_(true).
    canSeeBankAccountType_(true).
    canSeeBankAccountBalance_(true).
    canSeeBankAccountCurrency_(true).
    canSeeBankAccountLabel_(true).
    canSeeBankAccountNationalIdentifier_(true).
    canSeeBankAccountSwift_bic_(true).
    canSeeBankAccountIban_(true).
    canSeeBankAccountNumber_(true).
    canSeeBankAccountBankName_(true).
    canSeeBankAccountBankPermalink_(true).
    canSeeOtherAccountNationalIdentifier_(true).
    canSeeOtherAccountSWIFT_BIC_(true).
    canSeeOtherAccountIBAN_ (true).
    canSeeOtherAccountBankName_(true).
    canSeeOtherAccountNumber_(true).
    canSeeOtherAccountMetadata_(true).
    canSeeOtherAccountKind_(true).
    canSeeMoreInfo_(true).
    canSeeUrl_(true).
    canSeeImageUrl_(true).
    canSeeOpenCorporatesUrl_(true).
    canSeeCorporateLocation_(true).
    canSeePhysicalLocation_(true).
    canSeePublicAlias_(true).
    canSeePrivateAlias_(true).
    canAddMoreInfo_(true).
    canAddURL_(true).
    canAddImageURL_(true).
    canAddOpenCorporatesUrl_(true).
    canAddCorporateLocation_(true).
    canAddPhysicalLocation_(true).
    canAddPublicAlias_(true).
    canAddPrivateAlias_(true).
    canDeleteCorporateLocation_(true).
    canDeletePhysicalLocation_(true).
    canEditOwnerComment_(true).
    canAddComment_(true).
    canDeleteComment_(true).
    canAddTag_(true).
    canDeleteTag_(true).
    canAddImage_(true).
    canDeleteImage_(true).
    canAddWhereTag_(true).
    canSeeWhereTag_(true).
    canDeleteWhereTag_(true).
    save

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