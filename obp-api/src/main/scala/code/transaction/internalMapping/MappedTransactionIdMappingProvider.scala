/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.transaction.internalMapping

import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TransactionId
import net.liftweb.common._
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo


object MappedTransactionIdMappingProvider extends TransactionIdMappingProvider with MdcLoggable
{

  override def getOrCreateTransactionId(
    transactionPlainTextReference: String
  ) =
  {

    val transactionIdMapping = TransactionIdMapping.find(
      By(TransactionIdMapping.TransactionPlainTextReference, transactionPlainTextReference)
    )

    transactionIdMapping match
    {
      case Full(vImpl) =>
      {
        logger.debug(s"getOrCreateTransactionId --> the TransactionIdMapping has been existing in server !")
        transactionIdMapping.map(_.transactionId)
      }
      case Empty =>
        tryo {
          TransactionIdMapping
            .create
            .TransactionPlainTextReference(transactionPlainTextReference)
            .saveMe
        } match {
          case Full(m) =>
            logger.debug(s"getOrCreateTransactionId--> create mappedTransactionIdMapping : $m")
            Full(m.transactionId)
          case Failure(_, _, _) =>
            // UniqueIndex violation from concurrent insert — re-fetch the committed row
            TransactionIdMapping.find(
              By(TransactionIdMapping.TransactionPlainTextReference, transactionPlainTextReference)
            ).map(_.transactionId)
          case other => other.map(_.transactionId)
        }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }


  override def getTransactionPlainTextReference(transactionId: TransactionId) = {
    TransactionIdMapping.find(
      By(TransactionIdMapping.TransactionId, transactionId.value),
    ).map(_.transactionPlainTextReference)
  }
}

