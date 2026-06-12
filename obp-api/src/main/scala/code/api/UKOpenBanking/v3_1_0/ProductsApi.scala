//package code.api.UKOpenBanking.v3_1_0
//
//import scala.language.implicitConversions
//import code.api.berlin.group.v1_3.JvalueCaseClass
//import code.api.util.APIUtil._
//import code.api.util.ApiTag
//import code.api.util.ApiTag._
//import code.api.util.ErrorMessages._
//import com.github.dwickern.macros.NameOf.nameOf
//import net.liftweb.common.Full
//import com.openbankproject.commons.util.json
//import org.json4s._
//
//import scala.collection.immutable.Nil
//import scala.collection.mutable.ArrayBuffer
//import com.openbankproject.commons.ExecutionContext.Implicits.global
//
//object APIMethods_ProductsApi extends RestHelper {
//    val apiVersion = OBP_UKOpenBanking_310.apiVersion
//    val resourceDocs = ArrayBuffer[ResourceDoc]()
//    val apiRelations = ArrayBuffer[ApiRelation]()
//    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)
//
//    val endpoints = 
//      getAccountsAccountIdProduct ::
//      getProducts ::
//      Nil
//
//
//     resourceDocs += ResourceDoc(
//       getAccountsAccountIdProduct, 
//       apiVersion, 
//       nameOf(getAccountsAccountIdProduct),
//       "GET", 
//       "/accounts/ACCOUNTID/product", 
//       "Get Products",
//       s"""${mockedDataText(true)}""", 
//       EmptyBody,
//       EmptyBody,
//       List(AuthenticatedUserIsRequired, UnknownError),
//       ApiTag("Products") :: apiTagMockedData :: Nil
//     )
//
//     lazy val getAccountsAccountIdProduct : OBPEndpoint = {
//       case "accounts" :: accountid:: "product" :: Nil JsonGet _ => {
//         cc =>
//           for {
//             (Full(u), callContext) <- authenticatedAccess(cc)
//             } yield {
//             (NotImplemented, callContext)
//           }
//         }
//       }
//
//     resourceDocs += ResourceDoc(
//       getProducts, 
//       apiVersion, 
//       nameOf(getProducts),
//       "GET", 
//       "/products", 
//       "Get Products",
//       s"""${mockedDataText(true)}
//""", 
//       EmptyBody,
//       EmptyBody,
//       List(AuthenticatedUserIsRequired, UnknownError),
//       ApiTag("Products") :: apiTagMockedData :: Nil
//     )
//
//     lazy val getProducts : OBPEndpoint = {
//       case "products" :: Nil JsonGet _ => {
//         cc =>
//           for {
//             (Full(u), callContext) <- authenticatedAccess(cc)
//             } yield {
//             (NotImplemented, callContext)
//           }
//         }
//       }
//
//}
//
//
//
//