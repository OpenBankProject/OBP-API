package code.featuredapicollection

import code.setup.ServerSetup

/**
 * Characterization of the featured-api-collections provider, written before the implementation
 * moves to Doobie.
 *
 * There is no endpoint or provider test for this table anywhere in the suite - grep finds only
 * commented-out ResourceDoc registrations and one mention in frozen_type_meta_data (an endpoint
 * name, not behaviour). The v6.0.0 endpoints that use it (createFeaturedApiCollection,
 * getFeaturedApiCollectionsAdmin, updateFeaturedApiCollection, deleteFeaturedApiCollection) are
 * live, wired into Http4s600's route chain, and none of them are covered either. So this is
 * pinning the contract from the ground up rather than checking an existing one:
 *
 *  - create then read back by both the generated id and the api collection id;
 *  - getAllFeaturedApiCollections is sorted by sortOrder ascending - NewStyle.
 *    getFeaturedApiCollections relies on this ordering for how featured collections are presented;
 *  - update rewrites sortOrder in place rather than adding a row, checked by counting after;
 *  - delete by either key removes the row.
 */
class FeaturedApiCollectionsProviderTest extends ServerSetup {

  private def provider = DoobieFeaturedApiCollectionsProvider

  private val collA = "featured-provider-test-collection-A"
  private val collB = "featured-provider-test-collection-B"
  private val collC = "featured-provider-test-collection-C"

  override def beforeEach() = {
    super.beforeEach()
    List(collA, collB, collC).foreach(provider.deleteFeaturedApiCollectionByApiCollectionId)
  }

  Feature("featured api collection storage") {

    Scenario("a featured collection can be created and read back by either key") {
      val created = provider.createFeaturedApiCollection(collA, 5)
      created.isDefined should equal(true)
      val id = created.openOrThrowException("just created").featuredApiCollectionId

      val byId = provider.getFeaturedApiCollectionById(id)
      byId.isDefined should equal(true)
      byId.openOrThrowException("found").apiCollectionId should equal(collA)

      val byCollectionId = provider.getFeaturedApiCollectionByApiCollectionId(collA)
      byCollectionId.isDefined should equal(true)
      byCollectionId.openOrThrowException("found").sortOrder should equal(5)
    }

    Scenario("getAllFeaturedApiCollections is sorted by sortOrder ascending") {
      provider.createFeaturedApiCollection(collC, 30)
      provider.createFeaturedApiCollection(collA, 10)
      provider.createFeaturedApiCollection(collB, 20)

      val all = provider.getAllFeaturedApiCollections()
        .filter(f => Set(collA, collB, collC).contains(f.apiCollectionId))

      all.map(_.apiCollectionId) should equal(List(collA, collB, collC))
    }

    Scenario("update rewrites sortOrder on the existing row instead of adding one") {
      val created = provider.createFeaturedApiCollection(collA, 1)
      val id = created.openOrThrowException("just created").featuredApiCollectionId

      provider.updateFeaturedApiCollection(id, 99)

      val after = provider.getFeaturedApiCollectionByApiCollectionId(collA)
      after.openOrThrowException("updated").sortOrder should equal(99)

      provider.getAllFeaturedApiCollections().count(_.apiCollectionId == collA) should equal(1)
    }

    Scenario("delete by featured id removes the row") {
      val created = provider.createFeaturedApiCollection(collA, 1)
      val id = created.openOrThrowException("just created").featuredApiCollectionId

      provider.deleteFeaturedApiCollectionById(id)

      provider.getFeaturedApiCollectionByApiCollectionId(collA).isDefined should equal(false)
    }

    Scenario("delete by api collection id removes the row") {
      provider.createFeaturedApiCollection(collA, 1)

      provider.deleteFeaturedApiCollectionByApiCollectionId(collA)

      provider.getFeaturedApiCollectionByApiCollectionId(collA).isDefined should equal(false)
    }
  }
}
