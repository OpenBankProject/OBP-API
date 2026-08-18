package code.api.util

import code.abacrule.AbacRule
import code.amqpbroker.AmqpBankBroker
import code.api.attributedefinition.AttributeDefinition
import code.apiproduct.ApiProduct
import code.counterpartylimit.DoobieCounterpartyLimitProvider
import code.customer.MappedCustomer
import code.mandate.MandateProvision
import code.model.dataAccess.ResourceUser
import code.productfee.ProductFee
import code.ratelimiting.RateLimiting
import code.routingscheme.BankSupportedRoutingScheme
import code.setup.ServerSetup
import code.users.{UserAttribute, UserInvitation}
import doobie.implicits._

import scala.concurrent.Await
import scala.concurrent.duration._
import net.liftweb.common.Full
import net.liftweb.util.Helpers

/**
 * A NULL column has to read back the way Mapper read it, and it has to read back at all.
 *
 * Two separate mistakes are pinned here, both of them invisible on a database built by the current
 * writers - every writer binds a value - and both reachable on one that has been through an
 * upgrade. Schemifier added a new field to an existing table with ALTER TABLE ADD COLUMN and no
 * backfill, so every row written before the field existed holds NULL in it.
 *
 *  1. The value. MappedBoolean's getter is `i_is_! = data openOr false` and a NULL column sets
 *     `data = Empty` on read, so Lift read a NULL flag as false whatever the field declared -
 *     `override def defaultValue = true` only seeds a NEW in-memory instance. Reading such a
 *     column as `getOrElse(true)` inverts the answer. MappedLong/Int are the opposite case:
 *     their reader is `if (isNull) defaultValue else v`, so a NULL number really did come back as
 *     the declared default, and dropping that default loses the value.
 *
 *  2. Whether it reads at all. Doobie's Get for a non-nullable type throws NonNullableColumnRead
 *     on a NULL, and it fails the whole query rather than the one row - so a single legacy row
 *     turns a listing into a 500. Mapper never failed a read.
 *
 * Each scenario writes the row with raw SQL on purpose: the stores' own writers always bind a
 * value, so this is the only way to produce the row an upgraded database carries.
 */
class NullDefaultReadFidelityTest extends ServerSetup {

  private def uid = Helpers.randomString(10).toLowerCase

  feature("a boolean column that is NULL") {

    scenario("a mandate provision reads isActive as false, the way Mapper read it") {
      val provisionId = "prov-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mandateprovision
              (mandateid, provisionid, provisionname, legalreference, provisiontype, conditions,
               linkedviewid, linkedabacruleid, isactive, sortorder, provisiondescription,
               signatoryrequirements, linkedchallengetype)
              VALUES ('m-null', $provisionId, 'p', 'ref', 'type', 'cond', 'v', 'r',
                      NULL, NULL, 'desc', 'sig', 'chal')""".update.run)

      MandateProvision.findByProvisionId(provisionId) match {
        case Full(p) =>
          p.isActive should equal(false)
          p.sortOrder should equal(0)
        case other => fail(s"the provision that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM mandateprovision WHERE provisionid = $provisionId".update.run)
    }

    scenario("a resource user reads isNaturalPerson as false, the way Mapper read it") {
      val userId = "ru-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO resourceuser
              (userid_, email, name_, provider_, providerid, company, createdbyconsentid,
               createdbyuserinvitationid, isdeleted, lastusedlocale, isnaturalperson,
               principaluserid)
              VALUES ($userId, ${userId + "@example.com"}, $userId, 'test', $userId, '', '', '',
                      false, 'en_GB', NULL, '')""".update.run)

      ResourceUser.findByUserId(userId) match {
        case Full(u) => u.isNaturalPerson should equal(false)
        case other   => fail(s"the user that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM resourceuser WHERE userid_ = $userId".update.run)
    }

    scenario("a customer reads isPendingAgent as false, the way Mapper read it") {
      val customerId = "cust-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO mappedcustomer
              (mcustomerid, mbank, mnumber, mmobilenumber, mlegalname, memail, mfaceimageurl,
               mrelationshipstatus, mhighesteducationattained, memploymentstatus, mcreditrating,
               mcreditsource, mcreditlimitcurrency, mcreditlimitamount, mtitle, mbranchid,
               mnamesuffix, mcustomertype, mparentcustomerid, mispendingagent, misconfirmedagent)
              VALUES ($customerId, 'bank-x', $customerId, '', 'legal name', '', '', '', '', '', '',
                      '', '', '', '', '', '', 'INDIVIDUAL', '', NULL, NULL)""".update.run)

      MappedCustomer.findByCustomerId(customerId) match {
        case Full(c) =>
          c.isPendingAgent should equal(false)
          c.isConfirmedAgent should equal(false)
        case other => fail(s"the customer that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM mappedcustomer WHERE mcustomerid = $customerId".update.run)
    }

    scenario("an ABAC rule still reads, with isActive false") {
      val ruleId = "abac-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO abacrule
              (createdbyuserid, description, updatedbyuserid, abacruleid, rulename, isactive,
               rulecode, policy)
              VALUES ('u', 'd', 'u', $ruleId, ${"rule " + ruleId}, NULL, 'code', 'policy')"""
          .update.run)

      AbacRule.findById(ruleId) match {
        case Full(r) => r.isActive should equal(false)
        case other   => fail(s"the rule that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM abacrule WHERE abacruleid = $ruleId".update.run)
    }

    scenario("a product fee still reads, with isActive false") {
      val feeId = "fee-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO productfee
              (moreinfo, bankid, currency, productcode, productfeeid, isactive, frequency, name,
               type_c, amount)
              VALUES ('info', 'bank-x', 'EUR', 'code-x', $feeId, NULL, 'MONTHLY', 'a fee',
                      'TYPE', 1.0)""".update.run)

      ProductFee.findByProductFeeId(feeId) match {
        case Full(f) => f.isActive should equal(false)
        case other   => fail(s"the fee that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM productfee WHERE productfeeid = $feeId".update.run)
    }

    scenario("a bank's supported routing scheme still reads, with enabled false") {
      val bankId = "brs-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO banksupportedroutingscheme (bankid, scheme, enabled, banknotes)
              VALUES ($bankId, 'IBAN', NULL, 'notes')""".update.run)

      BankSupportedRoutingScheme.find(bankId, "IBAN") match {
        case Full(s) => s.enabled should equal(false)
        case other   => fail(s"the scheme that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM banksupportedroutingscheme WHERE bankid = $bankId".update.run)
    }

    scenario("a user attribute still reads, with isPersonal false") {
      val attributeId = "ua-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO userattribute
              (userattributeid, value, userid, ispersonal, name, type_c, createdat)
              VALUES ($attributeId, 'v', ${"u-" + uid}, NULL, 'a name', 'STRING', CURRENT_TIMESTAMP)"""
          .update.run)

      UserAttribute.findById(attributeId) match {
        case Full(a) => a.isPersonal should equal(false)
        case other   => fail(s"the attribute that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM userattribute WHERE userattributeid = $attributeId".update.run)
    }

    scenario("an attribute definition still reads, with isActive false") {
      val definitionId = "ad-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO attributedefinition
              (bankid, isactive, description, typeofvalue, alias, canbeseenonviews,
               attributedefinitionid, name, category)
              VALUES ('bank-x', NULL, 'd', 'STRING', 'alias', '[]', $definitionId,
                      ${"n-" + uid}, 'Customer')""".update.run)

      AttributeDefinition.findByAttributeDefinitionId(definitionId) match {
        case Full(d) => d.isActive should equal(false)
        case other   => fail(s"the definition that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(
        sql"DELETE FROM attributedefinition WHERE attributedefinitionid = $definitionId".update.run)
    }
  }

  feature("a numeric column that is NULL") {

    scenario("an API product reads its call limits as the field default, not a failure") {
      val code = "prod-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO apiproduct
              (tags, description, persecondcalllimit, perminutecalllimit, perhourcalllimit,
               perdaycalllimit, perweekcalllimit, permonthcalllimit, bankid, apiproductid,
               moreinfourl, collectionid, apiproductcode, parentapiproductcode,
               termsandconditionsurl, monthlysubscriptioncurrency, monthlysubscriptionamount,
               name, category)
              VALUES ('', 'd', NULL, NULL, NULL, NULL, NULL, NULL, 'bank-x', ${"id-" + uid}, '',
                      '', $code, '', '', 'EUR', '0', 'a product', 'cat')""".update.run)

      ApiProduct.findByBankIdAndCode("bank-x", code) match {
        case Full(p) =>
          p.perSecondCallLimit should equal(-1L)
          p.perMonthCallLimit should equal(-1L)
        case other => fail(s"the product that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM apiproduct WHERE apiproductcode = $code".update.run)
    }

    scenario("a rate limit reads its call limits as the configured limit, not a failure") {
      // A value no default could produce, so the assertion cannot pass by accident.
      setPropsValues("rate_limiting_per_minute" -> "83")
      val rateLimitingId = "rl-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO ratelimiting
              (bankid, consumerid, persecondcalllimit, perminutecalllimit, perhourcalllimit,
               perdaycalllimit, perweekcalllimit, permonthcalllimit, apiname, apiversion,
               ratelimitingid, createdat, updatedat)
              VALUES (NULL, ${"c-" + uid}, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                      $rateLimitingId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""".update.run)

      RateLimiting.findByRateLimitingId(rateLimitingId) match {
        case Full(r) =>
          r.perMinuteCallLimit should equal(83L)
          r.perSecondCallLimit should equal(-1L)
        case other => fail(s"the rate limit that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM ratelimiting WHERE ratelimitingid = $rateLimitingId".update.run)
    }

    scenario("a counterparty limit reads its transaction counts as the field default") {
      val counterpartyId = "cp-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO counterpartylimit
              (bankid, accountid, currency, viewid, counterpartyid, counterpartylimitid,
               maxnumberofmonthlytransactions, maxnumberofyearlytransactions,
               maxnumberoftransactions, maxsingleamount, maxmonthlyamount, maxyearlyamount,
               maxtotalamount)
              VALUES ('bank-x', 'acc-x', 'EUR', 'owner', $counterpartyId, ${"cl-" + uid},
                      NULL, NULL, NULL, 0, 0, 0, 0)""".update.run)

      Await.result(DoobieCounterpartyLimitProvider.getCounterpartyLimit(
        "bank-x", "acc-x", "owner", counterpartyId), 30.seconds) match {
        case Full(l) =>
          l.maxNumberOfTransactions should equal(-1)
          l.maxNumberOfMonthlyTransactions should equal(-1)
          l.maxNumberOfYearlyTransactions should equal(-1)
        case other => fail(s"the limit that was just inserted must be readable, got $other")
      }

      DoobieUtil.runUpdate(
        sql"DELETE FROM counterpartylimit WHERE counterpartyid = $counterpartyId".update.run)
    }

    scenario("an AMQP broker reads its port as the field default, not a failure") {
      val bankId = "amqp-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO amqp_bank_broker
              (bank_id, host, port, virtual_host, username, password, use_ssl)
              VALUES ($bankId, 'localhost', NULL, '/', 'guest', 'guest', NULL)""".update.run)

      AmqpBankBroker.findByBankId(bankId) match {
        case Full(b) =>
          b.port should equal(5672)
          b.useSsl should equal(false)
        case other => fail(s"the broker that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(sql"DELETE FROM amqp_bank_broker WHERE bank_id = $bankId".update.run)
    }

    scenario("a user invitation still reads when its secret key is NULL") {
      val invitationId = "inv-" + uid
      DoobieUtil.runUpdate(
        sql"""INSERT INTO userinvitation
              (userinvitationid, firstname, lastname, purpose, secretkey, bankid, company, status,
               country, email, createdat)
              VALUES ($invitationId, 'first', 'last', 'DEVELOPER', NULL, 'bank-x', 'co', 'CREATED',
                      'DE', ${uid + "@example.com"}, CURRENT_TIMESTAMP)""".update.run)

      UserInvitation.findByUserInvitationId(invitationId) match {
        case Full(i) => i.userInvitationId should equal(invitationId)
        case other   => fail(s"the invitation that was just inserted must be readable, got $other")
      }
      DoobieUtil.runUpdate(
        sql"DELETE FROM userinvitation WHERE userinvitationid = $invitationId".update.run)
    }
  }
}
