package code.api

import code.api.test.ServerSetup
import code.model.dataAccess._
import net.liftweb.util.Helpers._
import org.bson.types.ObjectId

trait SandboxPaymentTestingHelpers {

  self: ServerSetup with DefaultUsers =>

  def createPaymentTestBank() = HostedBank.createRecord.
    name(randomString(5)).
    alias(randomString(5)).
    permalink("payments-test-bank").
    national_identifier(randomString(5)).
    save

  def createAccount(bankMongoId : String, bankPermalink: String, accountPermalink : String, currency : String) = {

    val created = Account.createRecord.
      balance(1000).
      holder(randomString(4)).
      number(randomString(4)).
      kind(randomString(4)).
      name(randomString(4)).
      permalink(accountPermalink).
      bankID(new ObjectId(bankMongoId)).
      label(randomString(4)).
      currency(currency).
      save

    val hostedAccount = HostedAccount.
      create.
      accountID(created.id.get.toString).
      saveMe

    val owner = ownerView(bankPermalink, accountPermalink, hostedAccount)

    //give to user1 owner view
    ViewPrivileges.create.
      view(owner).
      user(obpuser1).
      save

    created
  }

}
