/**
  * Open Bank Project - API
  * Copyright (C) 2011-2015, TESOBE Ltd

  * This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.

  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.

  * You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.

  * Email: contact@tesobe.com
  * TESOBE / Music Pictures Ltd
  * Osloerstrasse 16/17
  * Berlin 13359, Germany

  * This product includes software developed at
  * TESOBE (http://www.tesobe.com/)
  * by
  * Simon Redfern : simon AT tesobe DOT com
  * Stefan Bethge : stefan AT tesobe DOT com
  * Everett Sochowski : everett AT tesobe DOT com
  * Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v2_1_0

import java.util.Date

import code.api.v1_2_1.AmountOfMoneyJSON
import code.api.v1_4_0.JSONFactory1_4_0.{ChallengeJSON, TransactionRequestAccountJSON}
import code.api.v2_0_0.{TransactionRequestWithChargeJSONs, TransactionRequestChargeJSON, TransactionRequestBodyJSON}
import code.branches.Branches.{BranchId, DriveUp, Lobby, Branch}
import code.model.AmountOfMoney
import code.transactionrequests.TransactionRequests._
import code.common.{Meta, License, Location, Address}

case class AddressImpl(line1 : String, line2 : String, line3 : String, city : String, county : String,
                       state : String, postCode : String, countryCode : String) extends Address

case class TransactionRequestTypeJSON(transaction_request_type: String)
case class TransactionRequestTypesJSON(transaction_request_types: List[TransactionRequestTypeJSON])

trait TransactionRequestDetailsJSON {
  val value : AmountOfMoneyJSON
}

case class TransactionRequestDetailsSandBoxTanJSON(
                                        to: TransactionRequestAccountJSON,
                                        value : AmountOfMoneyJSON,
                                        description : String
                                      ) extends TransactionRequestDetailsJSON

case class TransactionRequestDetailsSEPAJSON(
                                                  value : AmountOfMoneyJSON,
                                                  IBAN: String,
                                                  description : String
                                          ) extends TransactionRequestDetailsJSON

case class TransactionRequestDetailsFreeFormJSON(
                                                  value : AmountOfMoneyJSON
                                            ) extends TransactionRequestDetailsJSON

case class TransactionRequestWithChargeJSON210(
                                             id: String,
                                             `type`: String,
                                             from: TransactionRequestAccountJSON,
                                             details: String,
                                             transaction_ids: String,
                                             status: String,
                                             start_date: Date,
                                             end_date: Date,
                                             challenge: ChallengeJSON,
                                             charge : TransactionRequestChargeJSON
                                           )

case class TransactionRequestWithChargeJSONs210(
                                              transaction_requests_with_charges : List[TransactionRequestWithChargeJSON210]
                                            )

case class BranchJSON (branchId : String,
  name : String,
  address : AddressJSON,
  location : LocationJSON,
  lobby : LobbyJSON,
  driveUp : DriveUpJSON,
  meta : MetaJSON
                      )

case class LobbyJSON (
  hours : String
)

case class DriveUpJSON (
                         hours : String
)

case class MetaJSON (
                      license : LicenseJSON
)

case class LicenseJSON (
  id : String,
  name : String
)

case class AddressJSON (
                         line1 : String,
                         line2 : String,
                         line3 : String,
                         city : String,
                         county : String,
                         state : String,
                         postCode : String,
                         countryCode : String
)

case class LocationJSON (
  latitude: Double,
  longitude: Double
)






object JSONFactory210{
  def createTransactionRequestTypeJSON(transactionRequestType : String ) : TransactionRequestTypeJSON = {
    new TransactionRequestTypeJSON(
      transactionRequestType
    )
  }

  def createTransactionRequestTypeJSON(transactionRequestTypes : List[String]) : TransactionRequestTypesJSON = {
    TransactionRequestTypesJSON(transactionRequestTypes.map(createTransactionRequestTypeJSON))
  }

  //transaction requests
  def getTransactionRequestDetailsSandBoxTanFromJson(details: TransactionRequestDetailsSandBoxTanJSON) : TransactionRequestDetailsSandBoxTan = {
    val toAcc = TransactionRequestAccount (
      bank_id = details.to.bank_id,
      account_id = details.to.account_id
    )
    val amount = AmountOfMoney (
      currency = details.value.currency,
      amount = details.value.amount
    )

    TransactionRequestDetailsSandBoxTan (
      to = toAcc,
      value = amount,
      description = details.description
    )
  }

  def getTransactionRequestDetailsSEPAFromJson(details: TransactionRequestDetailsSEPAJSON) : TransactionRequestDetailsSEPA = {
    val amount = AmountOfMoney (
      currency = details.value.currency,
      amount = details.value.amount
    )

    TransactionRequestDetailsSEPA (
      value = amount,
      description = details.description
    )
  }

  def getTransactionRequestDetailsFreeFormFromJson(details: TransactionRequestDetailsFreeFormJSON) : TransactionRequestDetailsFreeForm = {
    val amount = AmountOfMoney (
      currency = details.value.currency,
      amount = details.value.amount
    )

    TransactionRequestDetailsFreeForm (
      value = amount
    )
  }

  def getBranchFromJson(branchJSON: BranchJSON) : Branch = {
    val address = new Address {
      override def line1 = branchJSON.address.line1
      override def line2 = branchJSON.address.line2
      override def line3 = branchJSON.address.line3
      override def city = branchJSON.address.city
      override def county = branchJSON.address.county
      override def state = branchJSON.address.state
      override def postCode = branchJSON.address.postCode
      override def countryCode = branchJSON.address.countryCode
    }

    val location = new Location {
      override def latitude = branchJSON.location.latitude
      override def longitude = branchJSON.location.longitude
    }

    val lobby = new Lobby {
      override def hours = branchJSON.lobby.hours
    }

    val driveUp = new DriveUp {
      override def hours = branchJSON.driveUp.hours
    }

    val license = new License {
      override def id = branchJSON.meta.license.id
      override def name = branchJSON.meta.license.name
    }

    val meta = new Meta {
      override def license = license
    }

    new Branch {
      override def branchId = BranchId(branchJSON.branchId)
      override def name = branchJSON.name
      override def address = address
      override def location = location
      override def lobby = lobby
      override def driveUp = driveUp
      override def meta = meta
    }
  }

  def createBranchJSON(branch : Branch) : BranchJSON = {
    new BranchJSON(
      branchId = branch.branchId.value,
      name = branch.name,
      address = AddressJSON(
        line1 = branch.address.line1,
        line2 = branch.address.line2,
        line3 = branch.address.line3,
        city = branch.address.city,
        county = branch.address.county,
        state = branch.address.state,
        postCode = branch.address.postCode,
        countryCode = branch.address.countryCode
      ),
      location = LocationJSON(
        latitude = branch.location.latitude,
        longitude = branch.location.longitude
      ),
      lobby = LobbyJSON(
        hours = branch.lobby.hours
      ),
      driveUp = DriveUpJSON(
        hours = branch.driveUp.hours
      ),
      meta = MetaJSON(
        license = LicenseJSON(
          id = branch.meta.license.id,
          name = branch.meta.license.name
        )
      )
    )
  }

  /** Creates v2.1.0 representation of a TransactionType
    *
    * @param tr An internal TransactionRequest instance
    * @return a v2.1.0 representation of a TransactionRequest
    */

  def createTransactionRequestWithChargeJSON(tr : TransactionRequest210) : TransactionRequestWithChargeJSON210 = {
    new TransactionRequestWithChargeJSON210(
      id = tr.id.value,
      `type` = tr.`type`,
      from = TransactionRequestAccountJSON (
        bank_id = tr.from.bank_id,
        account_id = tr.from.account_id),
      details = tr.details,
      transaction_ids = tr.transaction_ids,
      status = tr.status,
      start_date = tr.start_date,
      end_date = tr.end_date,
      // Some (mapped) data might not have the challenge. TODO Make this nicer
      challenge = {
        try {ChallengeJSON (id = tr.challenge.id, allowed_attempts = tr.challenge.allowed_attempts, challenge_type = tr.challenge.challenge_type)}
        // catch { case _ : Throwable => ChallengeJSON (id = "", allowed_attempts = 0, challenge_type = "")}
        catch { case _ : Throwable => null}
      },
      charge = TransactionRequestChargeJSON (summary = tr.charge.summary,
        value = AmountOfMoneyJSON(currency = tr.charge.value.currency,
          amount = tr.charge.value.amount)
      )
    )
  }

  def createTransactionRequestJSONs(trs : List[TransactionRequest210]) : TransactionRequestWithChargeJSONs210 = {
    TransactionRequestWithChargeJSONs210(trs.map(createTransactionRequestWithChargeJSON))
  }
}