package code.api

import code.search._
import net.liftweb.common.{Full, Loggable}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{GetRequest, InMemoryResponse, Req}

/**
  * Created by petar on 6/1/16.
  */

object ElasticSearchMetrics extends RestHelper with Loggable {

  val es = new elasticsearchMetrics

  serve {
    case Req("search" :: Nil, json , GetRequest) => {
      /*user =>
        for {
          u <- user //?~ ErrorMessages.UserNotLoggedIn
          hasEntitlement <- booleanToBox(Entitlements.entitlementProvider.vend.getEntitlement(u.bankId, u.userId, ApiRole.CanSearchAllAccounts)) ?~ "Entitlement already exists"
        } yield {
            Full(InMemoryResponse(es.searchProxy().getBytes(), Nil, Nil, 200))
        }
        else {
          //Full(errorJsonResponse(ErrorMessages.UserNotLoggedIn))
          */
          Full(InMemoryResponse(es.searchProxy(json).toString.getBytes(), Nil, Nil, 200))
        }

    }
  }

