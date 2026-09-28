package code.api.util

import code.api.Constant
import code.api.util.ApiTag.ResourceDocTag
import code.dynamicEntity.{DynamicEntityCommons, DynamicEntityProvider}
import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

/**
 * This suite checks ResourceDocFilters and ResourceDocVocabulary: filters are sorted, without
 * repeats and limited to values a ResourceDoc carries, and the vocabulary covers the dynamic docs,
 * including those of a Dynamic Entity registered after start-up.
 */
class ResourceDocFiltersTest extends ServerSetupWithTestData {

  object Filters extends Tag("ResourceDocFilters")

  feature("ResourceDocFilters.forResourceDocs") {

    scenario("sorts, removes repeats and leaves out values no ResourceDoc carries", Filters) {
      val filters = ResourceDocFilters.forResourceDocs(
        Some(List("Bank", "not-a-tag-7f3e", "Account", "Bank")),
        Some(List("getBanks", "notAFunction7f3e", "getBank", "getBanks")))
      filters.tags shouldBe Some(List(ResourceDocTag("Account"), ResourceDocTag("Bank")))
      filters.functions shouldBe Some(List("getBank", "getBanks"))
    }

    scenario("a filter whose values are all unknown stays a filter, and matches nothing", Filters) {
      val filters = ResourceDocFilters.forResourceDocs(Some(List("junk1", "junk2")), Some(List("noSuchFunction")))
      filters.tags shouldBe Some(Nil)
      filters.functions shouldBe Some(Nil)
      code.api.ResourceDocs1_4_0.ResourceDocsAPIMethodsUtil.filterResourceDocs(APIUtil.allStaticResourceDocs, filters.tags, None) shouldBe empty
    }

    scenario("no filter stays no filter", Filters) {
      val filters = ResourceDocFilters.forResourceDocs(None, None)
      filters.tags shouldBe None
      filters.functions shouldBe None
    }

    scenario("normalisedOnly sorts and removes repeats but keeps every value", Filters) {
      val filters = ResourceDocFilters.normalisedOnly(Some(List("b", "a", "b")), Some(List("z", "y")))
      filters.tags shouldBe Some(List(ResourceDocTag("a"), ResourceDocTag("b")))
      filters.functions shouldBe Some(List("y", "z"))
    }
  }

  feature("ResourceDocVocabulary") {

    scenario("it holds every tag and function of the static ResourceDocs", Filters) {
      val vocabulary = ResourceDocVocabulary.current()
      APIUtil.allStaticResourceDocs.flatMap(_.tags.map(_.tag)).toSet.subsetOf(vocabulary.tags) shouldBe true
      APIUtil.allStaticResourceDocs.map(_.partialFunctionName).toSet.subsetOf(vocabulary.functions) shouldBe true
    }

    scenario("a Dynamic Entity registered after start-up enters it when the dynamic docs version changes", Filters) {
      ResourceDocVocabulary.current() // build a snapshot before the entity exists
      val entity = s"vocabulary_probe_${java.util.UUID.randomUUID().toString.replace("-", "").take(8)}"
      DynamicEntityProvider.connectorMethodProvider.vend.createOrUpdate(
        DynamicEntityCommons(
          entityName = entity,
          metadataJson = s"""{"$entity":{"description":"d","required":[],"properties":{"name":{"type":"string","example":"Alice","description":"a name"}}}}""",
          dynamicEntityId = None,
          userId = resourceUser1.userId,
          bankId = None,
          hasPersonalEntity = false,
          hasCommunityAccess = true
        )
      ).openOrThrowException(s"could not register $entity")
      // What NewStyle does after every Dynamic Entity write.
      Constant.incrementCacheNamespaceVersion(Constant.RD_DYNAMIC_NAMESPACE)

      val dynamicDocs = APIUtil.allDynamicResourceDocs ++ code.api.dynamic.entity.helper.DynamicEntityHelper.v700Doc
      withClue(s"no dynamic ResourceDoc mentions $entity: ") { dynamicDocs.exists(_.requestUrl.contains(entity)) shouldBe true }
      // No refreshDynamic(): the version bump alone must make the snapshot rebuild.
      val vocabulary = ResourceDocVocabulary.current()
      dynamicDocs.flatMap(_.tags.map(_.tag)).toSet.subsetOf(vocabulary.tags) shouldBe true
      dynamicDocs.map(_.partialFunctionName).toSet.subsetOf(vocabulary.functions) shouldBe true
    }
  }
}
