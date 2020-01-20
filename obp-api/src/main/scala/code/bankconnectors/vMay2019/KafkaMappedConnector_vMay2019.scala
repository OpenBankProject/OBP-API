package code.bankconnectors.vMay2019

/*
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID.randomUUID

import code.api.JSONFactoryGateway.PayloadOfJwtJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric, _}
import code.api.util.ErrorMessages._
import code.api.util.ExampleValue._
import code.api.util._
import code.api.v2_1_0.TransactionRequestBodyCommonJSON
import code.bankconnectors._
import code.bankconnectors.vJune2017.{InternalCustomer, JsonFactory_vJune2017}
import code.bankconnectors.vMar2017._
import code.bankconnectors.vSept2018.KafkaMappedConnector_vSept2018
import code.customer._
import code.kafka.{KafkaHelper, Topics}
import code.model._
import code.model.dataAccess._
import code.users.Users
import code.util.Helper.MdcLoggable
import code.views.Views
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model.{AmountOfMoneyTrait, CounterpartyTrait, CreditRatingTrait, _}
import com.sksamuel.avro4s.SchemaFor
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb
import net.liftweb.common.{Box, _}
import net.liftweb.json.{MappingException, parse}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, ProductAttributeType}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA

trait KafkaMappedConnector_vMay2019 extends Connector with KafkaHelper with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{CustomerFaceImage, CreditLimit, CreditRating, AmountOfMoney}

  implicit override val nameOfConnector = KafkaMappedConnector_vMay2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound...) are defined below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "May2019"

//---------------- dynamic start -------------------please don't modify this line

//---------------- dynamic end ---------------------please don't modify this line


  //-----helper methods

  private[this] def convertToTuple[T](callContext: Option[CallContext]) (inbound: Box[InBoundTrait[T]]): (Box[T], Option[CallContext]) = {
    val boxedResult = inbound match {
      case Full(in) if (in.status.hasNoError) => Full(in.data)
      case Full(inbound) if (inbound.status.hasError) =>
        Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
      case failureOrEmpty: Failure => failureOrEmpty
    }
    (boxedResult, callContext)
  }

}
object KafkaMappedConnector_vMay2019 extends KafkaMappedConnector_vMay2019{

}





