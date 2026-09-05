package code.payeelookup

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Failure, Full}

case class PayeeLookupRow(
  lookupId: String,
  identifierType: String,
  identifier: String,
  fspId: Option[String],
  networkProvider: Option[String],
  fullName: String,
  accountCategory: Option[String],
  accountType: Option[String],
  identityType: Option[String],
  identityValue: Option[String],
  fromBankId: String,
  fromAccountId: String,
  createdByUserId: String,
  createdAt: java.util.Date,
  expiresAt: java.util.Date
) extends PayeeLookupTrait

object DoobiePayeeLookupProvider extends PayeeLookupProvider {

  private def opt(s: String): Option[String] =
    if (s == null || s.isEmpty) None else Some(s)

  override def createPayeeLookup(
    lookupId: String,
    identifierType: String,
    identifier: String,
    fspId: Option[String],
    networkProvider: Option[String],
    fullName: String,
    accountCategory: Option[String],
    accountType: Option[String],
    identityType: Option[String],
    identityValue: Option[String],
    fromBankId: String,
    fromAccountId: String,
    createdByUserId: String,
    ttlSeconds: Long
  ): Box[PayeeLookupTrait] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    val expiresAt = new java.sql.Timestamp(now.getTime + ttlSeconds * 1000)
    try {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO payeelookup
              (lookupid, identifiertype, identifier, fspid, networkprovider, fullname,
               accountcategory, accounttype, identitytype, identityvalue,
               frombankid, fromaccountid, createdbyuserid, creationdate, expiresat)
              VALUES
              ($lookupId, $identifierType, $identifier, ${fspId.getOrElse("")}, ${networkProvider.getOrElse("")}, $fullName,
               ${accountCategory.getOrElse("")}, ${accountType.getOrElse("")}, ${identityType.getOrElse("")}, ${identityValue.getOrElse("")},
               $fromBankId, $fromAccountId, $createdByUserId, $now, $expiresAt)"""
          .update.run)
      Full(PayeeLookupRow(
        lookupId, identifierType, identifier, fspId, networkProvider, fullName,
        accountCategory, accountType, identityType, identityValue,
        fromBankId, fromAccountId, createdByUserId, now, expiresAt))
    } catch {
      case e: Exception => Failure(e.getMessage, Full(e), Empty)
    }
  }

  override def getActivePayeeLookup(lookupId: String): Box[PayeeLookupTrait] = {
    DoobieUtil.runQuery(
      sql"""SELECT lookupid, identifiertype, identifier, fspid, networkprovider, fullname,
                   accountcategory, accounttype, identitytype, identityvalue,
                   frombankid, fromaccountid, createdbyuserid, creationdate, expiresat
            FROM payeelookup WHERE lookupid = $lookupId"""
        .query[(String, String, String, String, String, String, String, String, String, String, String, String, String, java.sql.Timestamp, java.sql.Timestamp)]
        .option) match {
      case Some((lId, idType, id, fsp, netProv, fullName, accCat, accType, idnType, idnValue, fromBank, fromAccount, createdBy, createdAt, expiresAt)) =>
        val row = PayeeLookupRow(
          lId, idType, id, opt(fsp), opt(netProv), fullName,
          opt(accCat), opt(accType), opt(idnType), opt(idnValue),
          fromBank, fromAccount, createdBy, createdAt, expiresAt)
        if (row.isExpired) Empty else Full(row)
      case None => Empty
    }
  }
}
