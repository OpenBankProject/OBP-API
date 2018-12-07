package code.bankconnectors.akka

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpLookupSystem
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc}
import code.api.util.CallContext
import code.bankconnectors.Connector
import code.bankconnectors.akka.actor.{AkkaConnectorActorInit, AkkaConnectorHelperActor}
import code.bankconnectors.vMar2017.InboundAdapterInfoInternal
import code.model.{BankId, Bank => BankTrait}
import com.sksamuel.avro4s.SchemaFor
import net.liftweb.common.{Box, Full}
import net.liftweb.json.Extraction.decompose
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.parse

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object AkkaConnector_vDec2018 extends Connector with AkkaConnectorActorInit {

  implicit override val nameOfConnector = AkkaConnector_vDec2018.toString

  val messageFormat: String = "Dec2018"
  implicit val formats = net.liftweb.json.DefaultFormats
  override val messageDocs = ArrayBuffer[MessageDoc]()
  val emptyObjectJson: JValue = decompose(Nil)
  
  lazy val southSideActor = ObpLookupSystem.getAkkaConnectorActor(AkkaConnectorHelperActor.actorName)

  messageDocs += MessageDoc(
    process = "obp.get.AdapterInfo",
    messageFormat = messageFormat,
    description = "Gets information about the active general (non bank specific) Adapter that is responding to messages sent by OBP.",
    outboundTopic = None,
    inboundTopic = None,
    emptyObjectJson,
    emptyObjectJson,
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetAdapterInfo]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundAdapterInfo]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )
  override def getAdapterInfoFuture(callContext: Option[CallContext]): Future[Box[(InboundAdapterInfoInternal, Option[CallContext])]] = {
    val req = OutboundGetAdapterInfo((new Date()).toString, callContext.map(_.toCallContextAkka))
    val response = (southSideActor ? req).mapTo[InboundAdapterInfo]
    response.map(r =>
      Full(
        (
          InboundAdapterInfoInternal(
            errorCode = "",
            backendMessages = Nil,
            name = r.name,
            version = r.version,
            git_commit = r.git_commit,
            date = r.date
          )
          ,
          callContext
        )
      )
    )
  }

  messageDocs += MessageDoc(
    process = "obp.get.Banks",
    messageFormat = messageFormat,
    description = "Gets the banks list on this OBP installation.",
    outboundTopic = None,
    inboundTopic = None,
    emptyObjectJson,
    emptyObjectJson,
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBanks]().toString(true))),
    inboundAvroSchema =  Some(parse(SchemaFor[InboundGetBanks]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 2))
  )
  override def getBanksFuture(callContext: Option[CallContext]): Future[Box[(List[BankTrait], Option[CallContext])]] = {
    val req = OutboundGetBanks(callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetBanks] = (southSideActor ? req).mapTo[InboundGetBanks]
    response.map(_.banks.map(r => (r.map(BankAkka(_)), callContext)))
  }

  messageDocs += MessageDoc(
    process = "obp.get.Bank",
    messageFormat = messageFormat,
    description = "Get a specific Bank as specified by bankId",
    outboundTopic = None,
    inboundTopic = None,
    emptyObjectJson,
    emptyObjectJson,
    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBank]().toString(true))),
    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBank]().toString(true))),
    adapterImplementation = Some(AdapterImplementation("- Core", 5))
  )
  override def getBankFuture(bankId : BankId, callContext: Option[CallContext]): Future[Box[(BankTrait, Option[CallContext])]] = {
    val req = OutboundGetBank(bankId.value, callContext.map(_.toCallContextAkka))
    val response: Future[InboundGetBank] = (southSideActor ? req).mapTo[InboundGetBank]
    response.map(_.bank.map(r => (BankAkka(r), callContext)))
  }

}
