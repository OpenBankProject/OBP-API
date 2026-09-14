/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.productfee.ProductFee
import net.liftweb.common.Full
import net.liftweb.mapper.{DB, Schemifier}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfFastFireHoseView {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def addFastFireHoseView(name: String): Boolean = {
    DbFunction.tableExists(ProductFee) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        val executedSql =
          DbFunction.maybeWrite(true, Schemifier.infoF _) {
            APIUtil.getPropsValue("db.driver") openOr("org.h2.Driver") match {
              case value if value.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver") =>
                () =>""  //TODO: do not support mssql server yet.
              case _ =>
                ()=>
              """
                |CREATE VIEW v_fast_firehose_accounts AS select
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
          }}

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