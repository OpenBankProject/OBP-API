package code.util

import java.util.regex.Pattern

import net.liftweb.mapper.Mapper
import org.apache.commons.lang3.StringUtils
import org.scalatest.Matchers._
import org.scalatest.{FeatureSpec, Tag}

/**
 * Avoid new DB entity type name start with Mapped, and field name start with m.
 */
class MappedClassNameTest extends FeatureSpec {

  object ClassTag extends Tag("MappedClassName")
  val mapperClazz= classOf[Mapper[_]]

  {// the oldMappedTypeNames created with the follow code.
//    val mapperClazz= classOf[Mapper[_]]
//    val oldMappedTypes = ClassScanUtils.findTypes{ info =>
//      mapperClazz.isAssignableFrom(Class.forName(info.name, false, mapperClazz.getClassLoader))
//    }.toSet
//    val setString = oldMappedTypes.map(it => s""""$it"""").mkString("Set(", ",\n", ")")
  }

  val oldMappedTypeNames = Set("code.transactionrequests.MappedTransactionRequest",
    "code.methodrouting.MethodRouting",
    "code.metadata.tags.MappedTag",
    "code.yearlycustomercharges.MappedYearlyCharge",
    "code.model.Token",
    "code.transaction.MappedTransaction",
    "code.metadata.comments.MappedComment",
    "code.userlocks.UserLocks",
    "code.kycchecks.MappedKycCheck",
    "code.metadata.counterparties.MappedCounterpartyWhereTag",
    "code.metadata.counterparties.MappedCounterpartyBespoke",
    "code.model.dataAccess.internalMapping.AccountIdMapping",
    "code.cards.CardAction",
    "code.cards.PinReset",
    "code.meetings.MappedMeeting",
    "code.transactionrequests.TransactionRequestReasons",
    "code.accountapplication.MappedAccountApplication",
    "code.model.dataAccess.MappedBankAccount",
    "code.accountholders.MapperAccountHolders",
    "code.metadata.narrative.MappedNarrative",
    "code.dynamicEntity.DynamicEntity",
    "code.taxresidence.MappedTaxResidence",
    "code.atms.MappedAtm",
    "code.meetings.MappedMeetingInvitee",
    "code.api.pemusage.PemUsage",
    "code.transactionrequests.MappedTransactionRequestTypeCharge",
    "code.usercustomerlinks.MappedUserCustomerLink",
    "code.views.system.ViewDefinition",
    "code.customeraddress.MappedCustomerAddress",
    "code.kycstatuses.MappedKycStatus",
    "code.consent.MappedConsent",
    "code.model.dataAccess.BankAccountRouting",
    "code.fx.MappedFXRate",
    "code.webhook.MappedAccountWebhook",
    "code.standingorders.StandingOrder",
    "code.metrics.MappedConnectorMetric",
    "code.crm.MappedCrmEvent",
    "code.loginattempts.MappedBadLoginAttempt",
    "code.fx.MappedCurrency",
    "code.api.builder.MappedTemplate_2188356573920200339",
    "code.directdebit.DirectDebit",
    "code.model.Nonce",
    "code.kycmedias.MappedKycMedia",
    "code.transactionChallenge.MappedExpectedChallengeAnswer",
    "code.migration.MigrationScriptLog",
    "code.productcollection.MappedProductCollection") ++
    Set("code.model.dataAccess.MappedBankAccountData",
      "code.model.Consumer",
      "code.metadata.wheretags.MappedWhereTag",
      "code.database.authorisation.Authorisation",
      "code.productAttributeattribute.MappedProductAttribute",
      "code.context.MappedUserAuthContextUpdate",
      "code.metadata.counterparties.MappedCounterparty",
      "code.metrics.MappedMetric",
      "code.metadata.transactionimages.MappedTransactionImage",
      "code.kycdocuments.MappedKycDocument",
      "code.model.dataAccess.Admin",
      "code.webuiprops.WebUiProps",
      "code.customer.MappedCustomerMessage",
      "code.entitlementrequest.MappedEntitlementRequest",
      "code.accountattribute.MappedAccountAttribute",
      "code.branches.MappedBranch",
      "code.scope.MappedUserScope",
      "code.context.MappedUserAuthContext",
      "code.model.dataAccess.ViewImpl",
      "code.metadata.counterparties.MappedCounterpartyMetadata",
      "code.transaction_types.MappedTransactionType",
      "code.examplething.MappedThing",
      "code.model.dataAccess.ViewPrivileges",
      "code.scope.MappedScope",
      "code.ratelimiting.RateLimiting",
      "code.api.attributedefinition.AttributeDefinition",
      "code.token.OpenIDConnectToken",
      "code.transactionattribute.MappedTransactionAttribute",
      "code.customerattribute.MappedCustomerAttribute",
      "code.cards.MappedPhysicalCard",
      "code.cardattribute.MappedCardAttribute",
      "code.model.dataAccess.ResourceUser",
      "code.views.system.AccountAccess",
      "code.products.MappedProduct",
      "code.customer.internalMapping.MappedCustomerIdMapping",
      "code.model.dataAccess.AuthUser",
      "code.entitlement.MappedEntitlement",
      "code.model.dataAccess.DoubleEntryBookTransaction",
      "code.productcollectionitem.MappedProductCollectionItem",
      "code.customer.MappedCustomer",
      "code.socialmedia.MappedSocialMedia",
      "code.DynamicData.DynamicData",
      "code.model.dataAccess.MappedBank",
      "code.UserRefreshes.MappedUserRefreshes",
      "code.DynamicEndpoint.DynamicEndpoint",
      "code.CustomerDependants.MappedCustomerDependant")

  val newMappedTypes = ClassScanUtils.findTypes{ info =>
    val typeName = info.name
    !typeName.endsWith("$") &&
      !oldMappedTypeNames.contains(typeName) &&
      mapperClazz.isAssignableFrom(Class.forName(typeName, false, mapperClazz.getClassLoader))
  }.toSet
  feature("Validate New Entity name and column name") {

    scenario(s"new entity names start with Mapped should be empty", ClassTag) {

      // the new entity names those name start with Mapped
      val wrongTypes = newMappedTypes.filter(it => StringUtils.substringAfterLast(it, ".").startsWith("Mapped"))
      wrongTypes should equal(Set.empty[String])
    }

    scenario(s"new entity column names should not start with m", ClassTag) {
      val wrongFileNamePattern = Pattern.compile("m[^a-z].*\\$module")

      val typeNameMapWrongFields: Map[String, Array[String]] =
        newMappedTypes.map(Class.forName(_, false, mapperClazz.getClassLoader))
          .map { clazz =>
              val wrongFileNames = clazz.getDeclaredFields.map(_.getName)
                .filter(it => it.endsWith("$module") && it.charAt(0) == 'm' && it.charAt(1).isUpper)
                .map(StringUtils.substringBeforeLast(_, "$module"))
              clazz.getName -> wrongFileNames
          }.toMap.filter(_._2.nonEmpty)

      typeNameMapWrongFields should equal(Map.empty[String, Array[String]])
    }
  }

}
