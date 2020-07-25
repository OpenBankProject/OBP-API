package com.openbankproject.commons.dto

import com.openbankproject.commons.model.TopicTrait

import scala.concurrent.Future

/**
 * transfer one OutBound instance to remote and get corresponding InBound instance
 */
trait OutInBoundTransfer {

  def transfer(outbound: TopicTrait): Future[InBoundTrait[_]]
}
