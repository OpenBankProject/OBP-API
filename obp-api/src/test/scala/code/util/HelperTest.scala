/**
 * Open Bank Project - API
 * Copyright (C) 2011-2019, TESOBE GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Email: contact@tesobe.com
 * TESOBE GmbH.
 * Osloer Strasse 16/17
 * Berlin 13359, Germany
 *
 * This product includes software developed at
 * TESOBE (http://www.tesobe.com/)
 *
 */

package code.util


import code.api.Constant.ALL_CONSUMERS
import code.api.util._
import code.setup.PropsReset
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class HelperTest extends FeatureSpec with Matchers with GivenWhenThen with PropsReset {

  feature(s"test Helper.getIfNotExistsAddedColumLengthForMsSqlServer method") {

    scenario(s"test case addColumnIfNotExists") {
      val expectedValue =
        s"""
           |IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'accountaccess' AND COLUMN_NAME = 'consumer_id')
           |BEGIN
           |    ALTER TABLE accountaccess ADD consumer_id VARCHAR(255) DEFAULT '$ALL_CONSUMERS';
           |END""".stripMargin

      Helper.addColumnIfNotExists("com.microsoft.sqlserver.jdbc.SQLServerDriver","accountaccess", "consumer_id", ALL_CONSUMERS) should be(expectedValue)
    }

    scenario(s"test case dropIndexIfExists") {
      val expectedValue =
        s"""
           |IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'accountaccess_bank_id_account_id_view_fk_user_fk' AND object_id = OBJECT_ID('accountaccess'))
           |BEGIN
           |    DROP INDEX accountaccess.accountaccess_bank_id_account_id_view_fk_user_fk;
           |END""".stripMargin

      Helper.dropIndexIfExists("com.microsoft.sqlserver.jdbc.SQLServerDriver","accountaccess", "accountaccess_bank_id_account_id_view_fk_user_fk") should be(expectedValue)
    }

    scenario(s"test case createIndexIfNotExists") {
      val expectedValue =
        s"""
           |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'authuser_username_provider' AND object_id = OBJECT_ID('authUser'))
           |BEGIN
           |    CREATE INDEX authuser_username_provider on authUser(username,provider);
           |END""".stripMargin

      Helper.createIndexIfNotExists("com.microsoft.sqlserver.jdbc.SQLServerDriver","authUser", "authuser_username_provider") should be(expectedValue)
    }
    
  }

}