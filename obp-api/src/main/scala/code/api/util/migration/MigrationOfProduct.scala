package code.api.util.migration

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.products.MappedProduct

object MigrationOfProduct {

  def populateMissingProductUUIDs(name: String): Boolean = {
    DbFunction.tableExists(MappedProduct) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        var isSuccessful = false

        // Make back up
        DbFunction.makeBackUpOfTable(MappedProduct)

        val updatedProducts =
          for {
            product <- MappedProduct.findAll() if product.mProductId.get == null || product.mProductId.get.isEmpty
          } yield {
            product.mProductId(APIUtil.generateUUID()).saveMe()
          }

        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Updated number of rows:
             |${updatedProducts.size}
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
          s"""${MappedProduct._dbTableNameLC} table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
