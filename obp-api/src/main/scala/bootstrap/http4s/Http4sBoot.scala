/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
package bootstrap.http4s

import bootstrap.liftweb.ToSchemify
import code.api.Constant._
import code.api.util.ApiRole.CanCreateEntitlementAtAnyBank
import code.api.util.ErrorMessages.MandatoryPropertyIsNotSet
import code.api.util._
import code.api.util.migration.Migration
import code.api.util.migration.Migration.DbFunction
import code.entitlement.Entitlement
import code.model.dataAccess._
import code.scheduler._
import code.users._
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.util.Functions.Implicits._
import net.liftweb.common.Box.tryo
import net.liftweb.common._
import net.liftweb.db.{DB, DBLogEntry}
import net.liftweb.mapper.{DefaultConnectionIdentifier => _, _}
import net.liftweb.util._

import java.io.{File, FileInputStream}
import java.util.TimeZone




/**
 * Http4s Boot class for initializing OBP-API core components
 * This class handles database initialization, migrations, and system setup
 * without Lift Web framework dependencies
 */
class Http4sBoot extends MdcLoggable {

  /**
   * For the project scope, most early initiate logic should in this method.
   */
  override protected def initiate(): Unit = {
    val resourceDir = System.getProperty("props.resource.dir") ?: System.getenv("props.resource.dir")
    val propsPath = tryo{Box.legacyNullTest(resourceDir)}.toList.flatten

    val propsDir = for {
      propsPath <- propsPath
    } yield {
      Props.toTry.map {
        f => {
          val name = propsPath +  f() + "props"
          name -> { () => tryo{new FileInputStream(new File(name))} }
        }
      }
    }

    Props.whereToLook = () => {
      propsDir.flatten
    }

    if (Props.mode == Props.RunModes.Development) logger.info("OBP-API Props all fields : \n" + Props.props.mkString("\n"))
    logger.info("external props folder: " + propsPath)
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    logger.info("Current Project TimeZone: " + TimeZone.getDefault)


    // set dynamic_code_sandbox_enable to System.properties, so com.openbankproject.commons.ExecutionContext can read this value
    APIUtil.getPropsValue("dynamic_code_sandbox_enable")
      .foreach(it => System.setProperty("dynamic_code_sandbox_enable", it))
  }



  def boot: Unit = {
    implicit val formats = CustomJsonFormats.formats

    logger.info("Http4sBoot says: Hello from the Open Bank Project API. This is Http4sBoot.scala for Http4s runner. The gitCommit is : " + APIUtil.gitCommit)

    logger.debug("Boot says:Using database driver: " + APIUtil.driver)

    DB.defineConnectionManager(net.liftweb.util.DefaultConnectionIdentifier, APIUtil.vendor)

    /**
     * Function that determines if foreign key constraints are
     * created by Schemifier for the specified connection.
     *
     * Note: The chosen driver must also support foreign keys for
     * creation to happen
     *
     * In case of PostgreSQL it works
     */
    MapperRules.createForeignKeys_? = (_) => APIUtil.getPropsAsBoolValue("mapper_rules.create_foreign_keys", false)

    schemifyAll()

    logger.info("Mapper database info: " + Migration.DbFunction.mapperDatabaseInfo)

    DbFunction.tableExists(ResourceUser) match {
      case true => // DB already exist
        // Migration Scripts are used to update the model of OBP-API DB to a latest version.
        // Please note that migration scripts are executed before Lift Mapper Schemifier
        Migration.database.executeScripts(startedBeforeSchemifier = true)
        logger.info("The Mapper database already exits. The scripts are executed BEFORE Lift Mapper Schemifier.")
      case false => // DB is still not created. The scripts will be executed after Lift Mapper Schemifier
        logger.info("The Mapper database is still not created. The scripts are going to be executed AFTER Lift Mapper Schemifier.")
    }

    // Migration Scripts are used to update the model of OBP-API DB to a latest version.

    // Please note that migration scripts are executed after Lift Mapper Schemifier
    Migration.database.executeScripts(startedBeforeSchemifier = false)

    if (APIUtil.getPropsAsBoolValue("create_system_views_at_boot", true)) {
      // Create system views
      val owner = Views.views.vend.getOrCreateSystemView(SYSTEM_OWNER_VIEW_ID).isDefined
      val auditor = Views.views.vend.getOrCreateSystemView(SYSTEM_AUDITOR_VIEW_ID).isDefined
      val accountant = Views.views.vend.getOrCreateSystemView(SYSTEM_ACCOUNTANT_VIEW_ID).isDefined
      val standard = Views.views.vend.getOrCreateSystemView(SYSTEM_STANDARD_VIEW_ID).isDefined
      val stageOne = Views.views.vend.getOrCreateSystemView(SYSTEM_STAGE_ONE_VIEW_ID).isDefined
      val manageCustomViews = Views.views.vend.getOrCreateSystemView(SYSTEM_MANAGE_CUSTOM_VIEWS_VIEW_ID).isDefined
      // Only create Firehose view if they are enabled at instance.
      val accountFirehose = if (ApiPropsWithAlias.allowAccountFirehose)
        Views.views.vend.getOrCreateSystemView(SYSTEM_FIREHOSE_VIEW_ID).isDefined
      else Empty.isDefined

      APIUtil.getPropsValue("additional_system_views") match {
        case Full(value) =>
          val additionalSystemViewsFromProps = value.split(",").map(_.trim).toList
          val additionalSystemViews = List(
            SYSTEM_READ_ACCOUNTS_BASIC_VIEW_ID,
            SYSTEM_READ_ACCOUNTS_DETAIL_VIEW_ID,
            SYSTEM_READ_BALANCES_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_BASIC_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_DEBITS_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_DETAIL_VIEW_ID,
            SYSTEM_READ_ACCOUNTS_BERLIN_GROUP_VIEW_ID,
            SYSTEM_READ_BALANCES_BERLIN_GROUP_VIEW_ID,
            SYSTEM_READ_TRANSACTIONS_BERLIN_GROUP_VIEW_ID,
            SYSTEM_INITIATE_PAYMENTS_BERLIN_GROUP_VIEW_ID
          )
          for {
            systemView <- additionalSystemViewsFromProps
            if additionalSystemViews.exists(_ == systemView)
          } {
            Views.views.vend.getOrCreateSystemView(systemView)
          }
        case _ => // Do nothing
      }

    }

    ApiWarnings.logWarningsRegardingProperties()
    ApiWarnings.customViewNamesCheck()
    ApiWarnings.systemViewNamesCheck()

    //see the notes for this method:
    createDefaultBankAndDefaultAccountsIfNotExisting()

    createBootstrapSuperUser()
    
    if (APIUtil.getPropsAsBoolValue("logging.database.queries.enable", false)) {
      DB.addLogFunc
     {
       case (log, duration) =>
       {
         logger.debug("Total query time : %d ms".format(duration))
         log.allEntries.foreach
         {
           case DBLogEntry(stmt, duration) =>
             logger.debug("The query :  %s in %d ms".format(stmt, duration))
         }
       }
     }
    }

    // start RabbitMq Adapter(using mapped connector as mockded CBS)
    if (APIUtil.getPropsAsBoolValue("rabbitmq.adapter.enabled", false)) {
      code.bankconnectors.rabbitmq.Adapter.startRabbitMqAdapter.main(Array(""))
    }

    // ensure our relational database's tables are created/fit the schema
    val connector = code.api.Constant.CONNECTOR.openOrThrowException(s"$MandatoryPropertyIsNotSet. The missing prop is `connector` ")
    
    logger.info(s"ApiPathZero (the bit before version) is $ApiPathZero")
    logger.debug(s"If you can read this, logging level is debug")

    // API Metrics (logs of API calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (APIUtil.getPropsAsBoolValue("write_metrics", false)) {
      logger.info("writeMetrics is true. We will write API metrics")
    } else {
      logger.info("writeMetrics is false. We will NOT write API metrics")
    }

    // API Metrics (logs of Connector calls)
    // If set to true we will write each URL with params to a datastore / log file
    if (APIUtil.getPropsAsBoolValue("write_connector_metrics", false)) {
      logger.info("writeConnectorMetrics is true. We will write connector metrics")
    } else {
      logger.info("writeConnectorMetrics is false. We will NOT write connector metrics")
    }


    logger.info (s"props_identifier is : ${APIUtil.getPropsValue("props_identifier", "NONE-SET")}")

    val locale = I18NUtil.getDefaultLocale()
    logger.info("Default Project Locale is :" + locale)

  }

  def schemifyAll() = {
    Schemifier.schemify(true, Schemifier.infoF _, ToSchemify.models: _*)
  }

  
  /**
   *  there will be a default bank and two default accounts in obp mapped mode.
   *  These bank and accounts will be used for the payments.
   *  when we create transaction request over counterparty and if the counterparty do not link to an existing obp account
   *  then we will use the default accounts (incoming and outgoing) to keep the money.
   */
  private def createDefaultBankAndDefaultAccountsIfNotExisting() ={
    val defaultBankId= APIUtil.defaultBankId
    val incomingAccountId= INCOMING_SETTLEMENT_ACCOUNT_ID
    val outgoingAccountId= OUTGOING_SETTLEMENT_ACCOUNT_ID

    MappedBank.find(By(MappedBank.permalink, defaultBankId)) match {
      case Full(b) =>
        logger.debug(s"Bank(${defaultBankId}) is found.")
      case _ =>
        MappedBank.create
          .permalink(defaultBankId)
          .fullBankName("OBP_DEFAULT_BANK")
          .shortBankName("OBP")
          .national_identifier("OBP")
          .mBankRoutingScheme("OBP")
          .mBankRoutingAddress("obp1")
          .logoURL("")
          .websiteURL("")
          .saveMe()
        logger.debug(s"creating Bank(${defaultBankId})")
    }

    MappedBankAccount.find(By(MappedBankAccount.bank, defaultBankId), By(MappedBankAccount.theAccountId, incomingAccountId)) match {
      case Full(b) =>
        logger.debug(s"BankAccount(${defaultBankId}, $incomingAccountId) is found.")
      case _ =>
        MappedBankAccount.create
          .bank(defaultBankId)
          .theAccountId(incomingAccountId)
          .accountCurrency("EUR")
          .saveMe()
        logger.debug(s"creating BankAccount(${defaultBankId}, $incomingAccountId).")
    }

    MappedBankAccount.find(By(MappedBankAccount.bank, defaultBankId), By(MappedBankAccount.theAccountId, outgoingAccountId)) match {
      case Full(b) =>
        logger.debug(s"BankAccount(${defaultBankId}, $outgoingAccountId) is found.")
      case _ =>
        MappedBankAccount.create
          .bank(defaultBankId)
          .theAccountId(outgoingAccountId)
          .accountCurrency("EUR")
          .saveMe()
        logger.debug(s"creating BankAccount(${defaultBankId}, $outgoingAccountId).")
    }
  }


  /**
   * Bootstrap Super User
   * Given the following credentials, OBP will create a user *if it does not exist already*.
   * This user's password will be valid for a limited amount of time.
   * This user will be granted ONLY CanCreateEntitlementAtAnyBank
   * This feature can also be used in a "Break Glass scenario"
   */
  private def createBootstrapSuperUser() ={

    val superAdminUsername = APIUtil.getPropsValue("super_admin_username","")
    val superAdminInitalPassword = APIUtil.getPropsValue("super_admin_inital_password","")
    val superAdminEmail = APIUtil.getPropsValue("super_admin_email","")

    val isPropsNotSetProperly = superAdminUsername==""||superAdminInitalPassword ==""||superAdminEmail==""

    //This is the logic to check if an AuthUser exists for the `create sandbox` endpoint, AfterApiAuth, OpenIdConnect ,,,
    val existingAuthUser = AuthUser.find(By(AuthUser.username, superAdminUsername))

    if(isPropsNotSetProperly) {
      //Nothing happens, props is not set
    }else if(existingAuthUser.isDefined) {
      logger.error(s"createBootstrapSuperUser- Errors:  Existing AuthUser with username ${superAdminUsername} detected in data import where no ResourceUser was found")
    } else {
      val authUser = AuthUser.create
        .email(superAdminEmail)
        .firstName(superAdminUsername)
        .lastName(superAdminUsername)
        .username(superAdminUsername)
        .password(superAdminInitalPassword)
        .passwordShouldBeChanged(true)
        .validated(true)

      val validationErrors = authUser.validate

      if(!validationErrors.isEmpty)
        logger.error(s"createBootstrapSuperUser- Errors: ${validationErrors.map(_.msg)}")
      else {
        Full(authUser.save()) //this will create/update the resourceUser.

        val userBox = Users.users.vend.getUserByProviderAndUsername(authUser.getProvider(), authUser.username.get)

        val resultBox = userBox.map(user => Entitlement.entitlement.vend.addEntitlement("", user.userId, CanCreateEntitlementAtAnyBank.toString))

        if(resultBox.isEmpty){
          logger.error(s"createBootstrapSuperUser- Errors: ${resultBox}")
        }
      }

    }

  }
  

}
