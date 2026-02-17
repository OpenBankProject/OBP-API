package code.accountaccessrequest

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object AccountAccessRequestTrait extends SimpleInjector {
  val accountAccessRequest = new Inject(buildOne _) {}

  def buildOne: AccountAccessRequestProvider = MappedAccountAccessRequestProvider
}

trait AccountAccessRequestProvider {
  def createAccountAccessRequest(
    bankId: String,
    accountId: String,
    viewId: String,
    isSystemView: Boolean,
    requestorUserId: String,
    targetUserId: String,
    businessJustification: String
  ): Box[AccountAccessRequestTrait]

  def getById(accountAccessRequestId: String): Box[AccountAccessRequestTrait]

  def getByAccount(bankId: String, accountId: String): Box[List[AccountAccessRequestTrait]]

  def getByAccountAndStatus(bankId: String, accountId: String, status: String): Box[List[AccountAccessRequestTrait]]

  def getByRequestorUserId(requestorUserId: String): Box[List[AccountAccessRequestTrait]]

  def getByUserAccountView(
    targetUserId: String,
    bankId: String,
    accountId: String,
    viewId: String
  ): Box[AccountAccessRequestTrait]

  def updateStatus(
    accountAccessRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[AccountAccessRequestTrait]
}

trait AccountAccessRequestTrait {
  def accountAccessRequestId: String
  def bankId: String
  def accountId: String
  def viewId: String
  def isSystemView: Boolean
  def requestorUserId: String
  def targetUserId: String
  def businessJustification: String
  def status: String
  def checkerUserId: String
  def checkerComment: String
  def created: Date
  def updated: Date
}
