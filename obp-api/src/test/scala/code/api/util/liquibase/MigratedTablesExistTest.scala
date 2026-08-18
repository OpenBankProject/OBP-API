package code.api.util.liquibase

import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie._
import doobie.implicits._

/**
 * Every table that has been taken off Lift Mapper must still exist.
 *
 * This exists because of a real failure mode rather than a hypothetical one. Schemifier used to
 * create these tables from the entity definitions; once the entity is deleted, the only thing that
 * creates them is the changelog under src/main/resources/db/changelog. That directory sits under a
 * .gitignore rule which excludes all of src/main/resources, so the DDL was present on the machine
 * that wrote it and absent from the repository - ten tables' worth. Everything stayed green
 * locally and would have failed on any clean checkout, at the point where the first query hit a
 * table that was never created.
 *
 * Missing DDL is not a compile error and not a schema error either: the migration tool simply has
 * nothing to apply, and the failure surfaces much later as an unrelated-looking SQL error inside
 * whichever endpoint touched the table first. Asserting existence directly turns that into one
 * obvious red.
 *
 * With the Flyway scripts gone this is also what stands in for the schema-equivalence checks that
 * compared the changelog against them: those needed both sides to exist, and their conclusion -
 * that the generated changelog builds the schema the scripts built - is recorded in the commit
 * that generated it. What survives is the ongoing assertion, that the tables and indexes the code
 * depends on are actually there.
 *
 * When a table moves off Mapper, add it here in the same commit.
 */
class MigratedTablesExistTest extends ServerSetup {

  // Names as the database holds them, which is not always the entity name: several entities
  // overrode dbTableName (connector_trace, consent_item), so deriving these from Scala names
  // would give a list that looks right and tests nothing.
  private val migratedTables = List(
    "mappedatm",
    "mappednarrative",
    "mappedcomment",
    "mappedtag",
    "mappedwheretag",
    "mappedtransactionimage",
    "producttag",
    "connector_trace",
    "consent_item",
    "jsonschemavalidation",
    "mappedtransactiontype",
    "etag",
    "authenticationtypevalidation",
    "userlocks",
    "connectormethod",
    "apicollectionendpoint",
    "featuredapicollection",
    "consentauthcontext",
    "mappeduserauthcontext",
    "userinitaction",
    "accountidmapping",
    "transactionidmapping",
    "mappedcustomeridmapping",
    "mappedbankaccountdata",
    "apicollection",
    "mappedbadloginattempt",
    "bankaccountrouting",
    "mappedfxrate",
    "migrationscriptlog",
    "transactionrequestreasons",
    "apiproductattribute",
    "mappeduserauthcontextupdate",
    "mappedcardattribute",
    "atmattribute",
    "bankattribute",
    "counterpartyattribute",
    "regulatedentityattribute",
    "mappedproductattribute",
    "mappedcustomerattribute",
    "mappedaccountattribute",
    "mappedtransactionattribute",
    "transactionrequestattribute",
    "mappedtaxresidence",
    "customerlink",
    "counterpartylimit",
    "customeraccountlink",
    "mappedusercustomerlink",
    "mappedcrmevent",
    "mappeduserrefreshes",
    "payeelookup",
    "metricsarchiverun",
    "open_corridor_fee_accrual",
    "utilitypaymentcallback",
    "webuiprops",
    "groupofroles",
    "organisation",
    "attributedefinition",
    "jobscheduler",
    "bankaccountbalance",
    "endpointtag",
    "apiproduct",
    "amqp_bank_broker",
    "productfee",
    "message_outbox",
    "openidconnecttoken",
    "useragreement",
    "userinvitation",
    "methodrouting",
    "accountaccessrequest",
    "bulkpayment",
    "bulkbatchreference",
    "mappedkycstatus",
    "mappedkycmedia",
    "mappedkyccheck",
    "mappedkycdocument",
    "mappedsocialmedia",
    "chatroom",
    "chatmessage",
    "participant",
    "reaction",
    "mappedproductcollection",
    "mappedproductcollectionitem",
    "directdebit",
    "standingorder",
    "mappedaccountwebhook",
    "bankaccountnotificationwebhook",
    "systemaccountnotificationwebhook",
    "mappedscope",
    "mappedaccountapplication",
    "mappedcustomeraddress",
    "mappedentitlementrequest",
    "mappedcustomerdependant",
    "mappedcounterpartybespoke",
    "expectedchallengeanswer",
    "userattribute",
    "regulatedentity",
    "routingscheme",
    "banksupportedroutingscheme",
    "abacrule",
    "endpointmapping",
    "dynamicentityindex",
    "mappedmeeting",
    "mappedmeetinginvitee",
    "mappedcustomermessage",
    "mappedtransactionrequesttypecharge",
    "mappedphysicalcard",
    "pinreset",
    "doubleentrybooktransaction",
    "dynamicendpoint",
    "mappedconnectormetric",
    "mappedentitlement",
    "ratelimiting",
    "mappedproduct",
    "mappedbranch",
    "mapperaccountholders",
    "dynamicmessagedoc",
    "dynamicresourcedoc",
    "dynamicdataaccess",
    "dynamicentity",
    "dynamicdata",
    "viewpermission",
    "accountaccess",
    "mandate",
    "mandateprovision",
    "signatorypanel",
    "signingbasket",
    "signingbasketpayment",
    "signingbasketconsent",
    "consentrequest",
    "mappedcounterparty",
    "mappedcounterpartymetadata",
    "mappedcounterpartywheretag",
    "mappedbank",
    "mappedtransaction",
    "mappedtransactionrequest",
    "mappedcustomer",
    "metric",
    "metricarchive",
    "mappedconsent",
    "mappedbankaccount",
    "viewdefinition",
    "nonce",
    "token",
    "consumer",
    "resourceuser",
    "authuser"
  )

  /**
   * Unique indexes the migrated tables must still have, as index_name per table.
   *
   * These are the ones Schemifier built from dbIndexes-declared UniqueIndex. They need their own
   * assertion because the export tool that produced the original DDL did not emit them: a table copied straight out of
   * that export looks complete and quietly loses its unique constraint, which does not fail -
   * inserts that should have been rejected simply start succeeding. Only tables that genuinely
   * have one are listed; most migrated tables have none.
   *
   * When a table moves off Mapper, read the truth from a booted instance before writing its
   * migration:
   *   SELECT table_name, index_name, index_type_name FROM information_schema.indexes
   *   WHERE table_name = 'YOUR_TABLE';
   * and add every UNIQUE INDEX row both to the changelog and to this list.
   */
  private val expectedUniqueIndexes = List(
    "PRODUCTTAG" -> "PRODUCTTAG_BANKID_PRODUCTCODE_TAG",
    "JSONSCHEMAVALIDATION" -> "JSONSCHEMAVALIDATION_OPERATIONID",
    "MAPPEDTRANSACTIONTYPE" -> "MAPPEDTRANSACTIONTYPE_MTRANSACTIONTYPEID",
    "MAPPEDTRANSACTIONTYPE" -> "MAPPEDTRANSACTIONTYPE_MBANKID_MSHORTCODE",
    "ETAG" -> "ETAG_ETAGRESOURCE",
    "AUTHENTICATIONTYPEVALIDATION" -> "AUTHENTICATIONTYPEVALIDATION_OPERATIONID",
    "USERLOCKS" -> "USERLOCKS_USERID",
    "CONNECTORMETHOD" -> "CONNECTORMETHOD_CONNECTORMETHODID",
    "CONNECTORMETHOD" -> "CONNECTORMETHOD_METHODNAME",
    "APICOLLECTIONENDPOINT" -> "APICOLLECTIONENDPOINT_APICOLLECTIONENDPOINTID",
    "APICOLLECTIONENDPOINT" -> "APICOLLECTIONENDPOINT_APICOLLECTIONID_OPERATIONID",
    "FEATUREDAPICOLLECTION" -> "FEATUREDAPICOLLECTION_FEATUREDAPICOLLECTIONID",
    "FEATUREDAPICOLLECTION" -> "FEATUREDAPICOLLECTION_APICOLLECTIONID",
    "CONSENTAUTHCONTEXT" -> "CONSENTAUTHCONTEXT_CONSENTID_KEY_C_CREATEDAT",
    "MAPPEDUSERAUTHCONTEXT" -> "MAPPEDUSERAUTHCONTEXT_MUSERID_MKEY_CREATEDAT",
    "USERINITACTION" -> "USERINITACTION_USERID_ACTIONNAME_ACTIONVALUE",
    // The three id-mapping tables: the reference column carries the real constraint as of
    // V057. The composite (id, reference) indexes these replaced were strictly implied by the
    // single-column unique index on the id column and constrained nothing - see V057 for why.
    "ACCOUNTIDMAPPING" -> "ACCOUNTIDMAPPING_MACCOUNTID",
    "ACCOUNTIDMAPPING" -> "ACCOUNTIDMAPPING_MACCOUNTPLAINTEXTREFERENCE",
    "TRANSACTIONIDMAPPING" -> "TRANSACTIONIDMAPPING_TRANSACTIONID",
    "TRANSACTIONIDMAPPING" -> "TRANSACTIONIDMAPPING_TRANSACTIONPLAINTEXTREFERENCE",
    "MAPPEDCUSTOMERIDMAPPING" -> "MAPPEDCUSTOMERIDMAPPING_MCUSTOMERID",
    "MAPPEDCUSTOMERIDMAPPING" -> "MAPPEDCUSTOMERIDMAPPING_MCUSTOMERPLAINTEXTREFERENCE",
    "MAPPEDBANKACCOUNTDATA" -> "MAPPEDBANKACCOUNTDATA_BANKID_ACCOUNTID",
    "APICOLLECTION" -> "APICOLLECTION_APICOLLECTIONID",
    "APICOLLECTION" -> "APICOLLECTION_USERID_APICOLLECTIONNAME",
    "MAPPEDBADLOGINATTEMPT" -> "MAPPEDBADLOGINATTEMPT_PROVIDER_MUSERNAME",
    "BANKACCOUNTROUTING" -> "BANKACCOUNTROUTING_BANKID_ACCOUNTID_ACCOUNTROUTINGSCHEME",
    "BANKACCOUNTROUTING" -> "BANKACCOUNTROUTING_BANKID_ACCOUNTROUTINGSCHEME_ACCOUNTROUTINGADDRESS",
    "MIGRATIONSCRIPTLOG" -> "MIGRATIONSCRIPTLOG_NAME_ISSUCCESSFUL",
    "APIPRODUCTATTRIBUTE" -> "APIPRODUCTATTRIBUTE_APIPRODUCTATTRIBUTEID",
    "MAPPEDTAXRESIDENCE" -> "MAPPEDTAXRESIDENCE_MCUSTOMERID_MDOMAIN_MTAXNUMBER",
    "CUSTOMERLINK" -> "CUSTOMERLINK_CUSTOMERLINKID",
    "COUNTERPARTYLIMIT" -> "COUNTERPARTYLIMIT_COUNTERPARTYLIMITID",
    "COUNTERPARTYLIMIT" -> "COUNTERPARTYLIMIT_BANKID_ACCOUNTID_VIEWID_COUNTERPARTYID",
    "CUSTOMERACCOUNTLINK" -> "CUSTOMERACCOUNTLINK_CUSTOMERACCOUNTLINKID",
    "CUSTOMERACCOUNTLINK" -> "CUSTOMERACCOUNTLINK_ACCOUNTID_CUSTOMERID",
    "MAPPEDUSERCUSTOMERLINK" -> "MAPPEDUSERCUSTOMERLINK_MUSERCUSTOMERLINKID",
    "MAPPEDUSERCUSTOMERLINK" -> "MAPPEDUSERCUSTOMERLINK_MUSERID_MCUSTOMERID",
    "MAPPEDCRMEVENT" -> "MAPPEDCRMEVENT_MCRMEVENTID",
    "MAPPEDUSERREFRESHES" -> "MAPPEDUSERREFRESHES_MUSERID",
    "PAYEELOOKUP" -> "PAYEELOOKUP_LOOKUPID",
    "METRICSARCHIVERUN" -> "METRICSARCHIVERUN_RUNID",
    "OPEN_CORRIDOR_FEE_ACCRUAL" -> "OPEN_CORRIDOR_FEE_ACCRUAL_TRANSACTION_REQUEST_ID",
    "UTILITYPAYMENTCALLBACK" -> "UTILITYPAYMENTCALLBACK_CALLBACKID",
    "WEBUIPROPS" -> "WEBUIPROPS_WEBUIPROPSID",
    "WEBUIPROPS" -> "WEBUIPROPS_NAME",
    "ORGANISATION" -> "ORGANISATION_ORGANISATIONID",
    "ATTRIBUTEDEFINITION" -> "ATTRIBUTEDEFINITION_BANKID_NAME_CATEGORY",
    "JOBSCHEDULER" -> "JOBSCHEDULER_JOBID",
    "ENDPOINTTAG" -> "ENDPOINTTAG_ENDPOINTTAGID",
    "APIPRODUCT" -> "APIPRODUCT_BANKID_APIPRODUCTCODE",
    "AMQP_BANK_BROKER" -> "AMQP_BANK_BROKER_BANK_ID",
    "USERAGREEMENT" -> "USERAGREEMENT_USERAGREEMENTID",
    "USERINVITATION" -> "USERINVITATION_USERINVITATIONID",
    "METHODROUTING" -> "METHODROUTING_METHODROUTINGID",
    "BULKPAYMENT" -> "BULKPAYMENT_TRANSACTIONREQUESTID_ITEMINDEX",
    "BULKBATCHREFERENCE" -> "BULKBATCHREFERENCE_FROMBANKID_FROMACCOUNTID_BATCHREFERENCE",
    "MAPPEDKYCMEDIA" -> "MAPPEDKYCMEDIA_MID",
    "MAPPEDKYCCHECK" -> "MAPPEDKYCCHECK_MID",
    "MAPPEDKYCDOCUMENT" -> "MAPPEDKYCDOCUMENT_MID",
    "MAPPEDSOCIALMEDIA" -> "MAPPEDSOCIALMEDIA_MCUSTOMERNUMBER",
    "CHATROOM" -> "CHATROOM_BANKID_NAME",
    "CHATMESSAGE" -> "CHATMESSAGE_CHATMESSAGEID",
    "PARTICIPANT" -> "PARTICIPANT_CHATROOMID_USERID",
    "REACTION" -> "REACTION_CHATMESSAGEID_USERID_EMOJI",
    "MAPPEDPRODUCTCOLLECTION" -> "MAPPEDPRODUCTCOLLECTION_MCOLLECTIONCODE_MPRODUCTCODE",
    "MAPPEDPRODUCTCOLLECTIONITEM" -> "MAPPEDPRODUCTCOLLECTIONITEM_MCOLLECTIONCODE_MMEMBERPRODUCTCODE",
    "DIRECTDEBIT" -> "DIRECTDEBIT_BANKID_ACCOUNTID_CUSTOMERID_COUNTERPARTYID",
    "MAPPEDACCOUNTWEBHOOK" -> "MAPPEDACCOUNTWEBHOOK_MACCOUNTWEBHOOKID",
    "BANKACCOUNTNOTIFICATIONWEBHOOK" -> "BANKACCOUNTNOTIFICATIONWEBHOOK_WEBHOOKID",
    "SYSTEMACCOUNTNOTIFICATIONWEBHOOK" -> "SYSTEMACCOUNTNOTIFICATIONWEBHOOK_WEBHOOKID",
    "MAPPEDSCOPE" -> "MAPPEDSCOPE_MSCOPEID",
    "MAPPEDACCOUNTAPPLICATION" -> "MAPPEDACCOUNTAPPLICATION_MACCOUNTAPPLICATIONID",
    "MAPPEDCUSTOMERADDRESS" -> "MAPPEDCUSTOMERADDRESS_MCUSTOMERADDRESSID",
    "MAPPEDENTITLEMENTREQUEST" -> "MAPPEDENTITLEMENTREQUEST_MENTITLEMENTREQUESTID",
    "EXPECTEDCHALLENGEANSWER" -> "EXPECTEDCHALLENGEANSWER_CHALLENGEID",
    "ROUTINGSCHEME" -> "ROUTINGSCHEME_SCHEME",
    "BANKSUPPORTEDROUTINGSCHEME" -> "BANKSUPPORTEDROUTINGSCHEME_BANKID_SCHEME",
    "ENDPOINTMAPPING" -> "ENDPOINTMAPPING_OPERATIONID",
    "MAPPEDMEETING" -> "MAPPEDMEETING_MMEETINGID",
    "MAPPEDCUSTOMERMESSAGE" -> "MAPPEDCUSTOMERMESSAGE_MMESSAGEID",
    "MAPPEDPHYSICALCARD" -> "MAPPEDPHYSICALCARD_MBANKID_MBANKCARDNUMBER_MISSUENUMBER",
    "DOUBLEENTRYBOOKTRANSACTION" -> "DOUBLEENTRYBOOKTRANSACTION_DEBITTRANSACTIONBANKID_DEBITTRANSACTIONACCOUNTID_DEBITTRANSACTIONID",
    "DYNAMICENDPOINT" -> "DYNAMICENDPOINT_DYNAMICENDPOINTID",
    "MAPPEDENTITLEMENT" -> "MAPPEDENTITLEMENT_MBANKID_MUSERID_MROLENAME",
    "RATELIMITING" -> "RATELIMITING_RATELIMITINGID",
    "MAPPEDPRODUCT" -> "MAPPEDPRODUCT_MBANKID_MCODE",
    "MAPPEDBRANCH" -> "MAPPEDBRANCH_MBANKID_MBRANCHID",
    "MAPPERACCOUNTHOLDERS" -> "MAPPERACCOUNTHOLDERS_USER_C_ACCOUNTBANKPERMALINK_ACCOUNTPERMALINK",
    "DYNAMICMESSAGEDOC" -> "DYNAMICMESSAGEDOC_PROCESS",
    "DYNAMICRESOURCEDOC" -> "DYNAMICRESOURCEDOC_REQUESTURL_REQUESTVERB",
    "DYNAMICDATAACCESS" -> "DYNAMICDATAACCESS_DYNAMICDATAID_USERID",
    "DYNAMICENTITY" -> "DYNAMICENTITY_DYNAMICENTITYID",
    "DYNAMICDATA" -> "DYNAMICDATA_DYNAMICDATAID",
    "VIEWPERMISSION" -> "VIEWPERMISSION_BANK_ID_ACCOUNT_ID_VIEW_ID_PERMISSION",
    "ACCOUNTACCESS" -> "ACCOUNTACCESS_BANK_ID_ACCOUNT_ID_VIEW_ID_USER_FK_CONSUMER_ID",
    "MANDATE" -> "MANDATE_MANDATEID",
    "MANDATEPROVISION" -> "MANDATEPROVISION_PROVISIONID",
    "SIGNATORYPANEL" -> "SIGNATORYPANEL_PANELID",
    "CONSENTREQUEST" -> "CONSENTREQUEST_CONSENTREQUESTID",
    "MAPPEDCOUNTERPARTY" -> "MAPPEDCOUNTERPARTY_MCOUNTERPARTYID",
    "MAPPEDCOUNTERPARTY" -> "MAPPEDCOUNTERPARTY_MNAME_MTHISBANKID_MTHISACCOUNTID_MTHISVIEWID",
    "MAPPEDCOUNTERPARTYMETADATA" -> "MAPPEDCOUNTERPARTYMETADATA_COUNTERPARTYID",
    "MAPPEDTRANSACTION" -> "MAPPEDTRANSACTION_TRANSACTIONID_BANK_ACCOUNT",
    "MAPPEDTRANSACTIONREQUEST" -> "MAPPEDTRANSACTIONREQUEST_MTRANSACTIONREQUESTID",
    "MAPPEDCUSTOMER" -> "MAPPEDCUSTOMER_MCUSTOMERID",
    "MAPPEDCUSTOMER" -> "MAPPEDCUSTOMER_MBANK_MNUMBER",
    "MAPPEDCONSENT" -> "MAPPEDCONSENT_MCONSENTID",
    "MAPPEDCONSENT" -> "MAPPEDCONSENT_CONSENT_REFERENCE_ID",
    "MAPPEDBANKACCOUNT" -> "MAPPEDBANKACCOUNT_BANK_THEACCOUNTID",
    "VIEWDEFINITION" -> "VIEWDEFINITION_COMPOSITE_UNIQUE_KEY",
    "CONSUMER" -> "CONSUMER_KEY_C",
    "CONSUMER" -> "CONSUMER_AZP_SUB",
    "RESOURCEUSER" -> "RESOURCEUSER_PROVIDER__PROVIDERID",
    "RESOURCEUSER" -> "RESOURCEUSER_USERID_UNIQUE",
    "AUTHUSER" -> "AUTHUSER_USERNAME_PROVIDER",
    // Restored late in the migration: the five tables migrated before the discovery that the export tool
    // omits dbIndexes-declared unique indexes had dropped these silently.
    "MAPPEDATM" -> "MAPPEDATM_MBANKID_MATMID",
    "MAPPEDCOMMENT" -> "MAPPEDCOMMENT_APIID",
    "MAPPEDTAG" -> "MAPPEDTAG_TAGID",
    "MAPPEDTRANSACTIONIMAGE" -> "MAPPEDTRANSACTIONIMAGE_IMAGEID",
    "CONSENT_ITEM" -> "CONSENT_ITEM_CONSENT_ITEM_ID"
  )

  /**
   * Plain indexes the same early scripts dropped, restored by V117.
   *
   * A missing one changes no answer, only the cost - but the first five are the lookup every
   * transaction-metadata read performs, against tables that grow with transaction volume, so
   * losing them turns a hot path into a full scan on any database built from the scripts alone.
   */
  private val expectedPlainIndexes = List(
    "MAPPEDNARRATIVE" -> "MAPPEDNARRATIVE_BANK_ACCOUNT_TRANSACTION_C",
    "MAPPEDCOMMENT" -> "MAPPEDCOMMENT_VIEW_C_BANK_ACCOUNT_TRANSACTION_C",
    "MAPPEDTAG" -> "MAPPEDTAG_BANK_ACCOUNT_TRANSACTION_C_VIEW_C",
    "MAPPEDWHERETAG" -> "MAPPEDWHERETAG_BANK_ACCOUNT_TRANSACTION_C_VIEW_C",
    "MAPPEDTRANSACTIONIMAGE" -> "MAPPEDTRANSACTIONIMAGE_BANK_ACCOUNT_TRANSACTION_C_VIEW_C",
    "CONNECTOR_TRACE" -> "CONNECTOR_TRACE_DATE_C",
    "CONSENT_ITEM" -> "CONSENT_ITEM_CONSENT_REFERENCE_ID",
    "CONSENT_ITEM" -> "CONSENT_ITEM_CONSENT_REFERENCE_ID_BANK_ID",
    // V118: the rest of what each entity's own dbIndexes list declared. V117 restored the ones
    // that were visible from reading the early scripts; enumerating the declarations instead
    // turns up six more, all on tables read per request.
    "CONNECTOR_TRACE" -> "CONNECTOR_TRACE_CORRELATIONID",
    "CONNECTOR_TRACE" -> "CONNECTOR_TRACE_CONNECTORNAME",
    "CONNECTOR_TRACE" -> "CONNECTOR_TRACE_FUNCTIONNAME",
    "CONNECTOR_TRACE" -> "CONNECTOR_TRACE_USERID",
    "CONNECTOR_TRACE" -> "CONNECTOR_TRACE_BANKID",
    "CONSENT_ITEM" -> "CONSENT_ITEM_BANK_ID"
  )

  /**
   * The indexes this database actually has, as (TABLE, INDEX) in upper case.
   *
   * Two things are vendor-specific here. `information_schema.indexes` is an H2 extension that
   * Postgres does not have, which keeps the same information in `pg_index`. And Postgres
   * truncates an identifier to 63 bytes, so five of these index names arrive shortened - the
   * index is there and covers the right columns, but `..._ACCOUNTROUTINGADDRESS` comes back as
   * `..._ACCOUNTROUTINGAD`. Checked at the time: no two names collide once truncated, so nothing
   * is lost, but a name comparison cannot be written the one way for both vendors.
   *
   * So the expectation is met by a name that matches OR is the vendor's truncation of it, which
   * `hasIndex` does. Postgres also folds unquoted names to lower case, hence the upper()s.
   */
  private def indexesInDatabase(uniqueOnly: Boolean): Set[(String, String)] =
    if (DoobieUtil.dbUrl.startsWith("jdbc:postgresql:")) {
      val unique = if (uniqueOnly) fr"AND i.indisunique" else Fragment.empty
      DoobieUtil.runQuery(
        (fr"""SELECT upper(t.relname), upper(c.relname)
              FROM pg_index i
              JOIN pg_class c ON c.oid = i.indexrelid
              JOIN pg_class t ON t.oid = i.indrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public'""" ++ unique)
          .query[(String, String)].to[List]).toSet
    } else {
      val unique = if (uniqueOnly) fr"WHERE index_type_name = 'UNIQUE INDEX'" else Fragment.empty
      DoobieUtil.runQuery(
        (fr"SELECT upper(table_name), upper(index_name) FROM information_schema.indexes" ++ unique)
          .query[(String, String)].to[List]).toSet
    }

  /** Postgres cuts an identifier at 63 bytes; NAMEDATALEN is 64 and the last byte is the NUL. */
  private val PostgresIdentifierLimit = 63

  /** True when the database has this index, allowing for the vendor shortening its name. */
  private def hasIndex(actual: Set[(String, String)], table: String, index: String): Boolean =
    actual.contains(table -> index) ||
      actual.contains(table -> index.take(PostgresIdentifierLimit))

  Feature("tables owned by the changelog rather than Schemifier") {

    Scenario("the unique indexes survived the move off Schemifier") {
      val actual = indexesInDatabase(uniqueOnly = true)

      expectedUniqueIndexes.foreach { case (table, index) =>
        withClue(s"unique index $index on $table is missing - the changelog does not create " +
          s"it (looked for the name and for its 63-byte truncation): ") {
          hasIndex(actual, table, index) should equal(true)
        }
      }
    }

    Scenario("the plain indexes on the metadata read paths survived too") {
      val actual = indexesInDatabase(uniqueOnly = false)

      expectedPlainIndexes.foreach { case (table, index) =>
        withClue(s"index $index on $table is missing - the changelog does not create it " +
          s"(looked for the name and for its 63-byte truncation): ") {
          hasIndex(actual, table, index) should equal(true)
        }
      }
    }

    Scenario("each migrated table exists and is queryable") {
      migratedTables.foreach { table =>
        withClue(s"table $table is missing - the changelog is not on the classpath: ") {
          noException should be thrownBy DoobieUtil.runQuery(
            (fr"SELECT COUNT(*) FROM " ++ Fragment.const(table)).query[Int].unique)
        }
      }
    }
  }
}
