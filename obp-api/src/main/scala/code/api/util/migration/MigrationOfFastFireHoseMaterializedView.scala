package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.productfee.ProductFee
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object MigrationOfFastFireHoseMaterializedView {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def addFastFireHoseMaterializedView(name: String): Boolean = {
    DbFunction.tableExists(ProductFee) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        def migrationSql(isMaterializedView:Boolean) =s"""
             |CREATE ${if(isMaterializedView) "MATERIALIZED" else ""} VIEW mv_fast_firehose_accounts AS select
             |    mappedbankaccount.theaccountid as account_id,
             |    mappedbankaccount.bank as bank_id,
             |    mappedbankaccount.accountlabel as account_label,
             |    mappedbankaccount.accountnumber as account_number,
             |    (select
             |        string_agg(
             |            'user_id:'
             |            || resourceuser.userid_
             |            ||',provider:'
             |            ||resourceuser.provider_
             |            ||',user_name:'
             |            ||resourceuser.name_,
             |         ',') as owners
             |     from resourceuser
             |     where
             |        resourceuser.id = mapperaccountholders.user_c
             |    ),
             |    mappedbankaccount.kind as kind,
             |    mappedbankaccount.accountcurrency as account_currency ,
             |    mappedbankaccount.accountbalance as account_balance,
             |    (select 
             |        string_agg(
             |            'bank_id:'
             |            ||bankaccountrouting.bankid 
             |            ||',account_id:' 
             |            ||bankaccountrouting.accountid,
             |            ','
             |            ) as account_routings
             |        from bankaccountrouting
             |        where 
             |              bankaccountrouting.accountid = mappedbankaccount.theaccountid
             |     ),                                                          
             |    (select 
             |        string_agg(
             |                'type:'
             |                || mappedaccountattribute.mtype
             |                ||',code:'
             |                ||mappedaccountattribute.mcode
             |                ||',value:'
             |                ||mappedaccountattribute.mvalue,
             |            ',') as account_attributes
             |    from mappedaccountattribute
             |    where
             |         mappedaccountattribute.maccountid = mappedbankaccount.theaccountid
             |     )
             |from mappedbankaccount
             |         LEFT JOIN mapperaccountholders
             |                   ON (mappedbankaccount.bank = mapperaccountholders.accountbankpermalink and mappedbankaccount.theaccountid = mapperaccountholders.accountpermalink);
             |""".stripMargin
        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") openOr("org.h2.Driver") match {
              case value if value.contains("org.h2.Driver") =>
                () => migrationSql(false)//Note: H2 database, do not support the MATERIALIZED view
              case value if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () => "" //TODO: do not support mssql server yet.
              case _ =>
                () => migrationSql(true)
            }
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Executed SQL: 
             |$executedSql
             |""".stripMargin
        isSuccessful = true
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""${ProductFee._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }

}