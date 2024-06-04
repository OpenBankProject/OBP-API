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

  feature("test Helper.getStaticPortionOfRedirectURL method") {
    // The redirectURl is `http://localhost:8082/oauthcallback`
    val testString1 = "http://localhost:8082/oauthcallback?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018"
    val testString2 = "http://localhost:8082?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018"
    val testString3 = "myapp://callback?oauth_token=%3DEBRZBMOPDXEUGGJP421FPFGK01IY2DGM5O3TLVSK%26oauth_verifier%3D63461"
    val testString4 = "fb00000000:://callback?oauth_token=%3DEBRZBMOPDXEUGGJP421FPFGK01IY2DGM5O3TLVSK%26oauth_verifier%3D63461"
    val testString5 = "http://127.0.0.1:8000/oauth/authorize?next=/en/metrics/api/&oauth_token=TN0124OCPRCL4KUJRF5LNLVMRNHTVZPJDBS2PNWU&oauth_verifier=10470"

    Helper.getStaticPortionOfRedirectURL(testString1).head should be("http://localhost:8082/oauthcallback")
    Helper.getStaticPortionOfRedirectURL(testString2).head should be("http://localhost:8082")
    Helper.getStaticPortionOfRedirectURL(testString3).head should be("myapp://callback")
    Helper.getStaticPortionOfRedirectURL(testString4).head should be("fb00000000:://callback")
    Helper.getStaticPortionOfRedirectURL(testString5).head should be("http://127.0.0.1:8000/oauth/authorize")
  }
  
  feature("test Helper.getHostOnlyOfRedirectURL method") {
    // The redirectURl is `http://localhost:8082/oauthcallback`
    val testString1 = "http://localhost:8082/oauthcallback?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018"
    val testString2 = "http://localhost:8082/oauthcallback"
    val testString3 = "http://localhost:8082?oauth_token=G5AEA2U1WG404EGHTIGBHKRR4YJZAPPHWKOMNEEV&oauth_verifier=53018"
    val testString4 = "http://localhost:8082"

    Helper.getHostOnlyOfRedirectURL(testString1).head should be("http://localhost:8082")
    Helper.getHostOnlyOfRedirectURL(testString2).head should be("http://localhost:8082")
    Helper.getHostOnlyOfRedirectURL(testString3).head should be("http://localhost:8082")
    Helper.getHostOnlyOfRedirectURL(testString4).head should be("http://localhost:8082")
  }

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