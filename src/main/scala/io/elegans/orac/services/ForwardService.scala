package io.elegans.orac.services

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 30/11/17.
  */

import io.elegans.orac.entities._

import scala.concurrent.Future
import scala.collection.immutable.{List, Map}
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.get.{GetResponse, MultiGetItemResponse, MultiGetRequestBuilder, MultiGetResponse}

import scala.collection.JavaConverters._
import org.elasticsearch.rest.RestStatus
import akka.event.{Logging, LoggingAdapter}
import io.elegans.orac.OracActorSystem
import io.elegans.orac.tools.{Checksum, Time}
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.{QueryBuilder, QueryBuilders}
import org.elasticsearch.index.reindex.{BulkByScrollResponse, DeleteByQueryAction}
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.sort.SortOrder
import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Implements forwarding functions
  */

object  ForwardService {
  val elastic_client: SystemIndexManagementElasticClient.type = SystemIndexManagementElasticClient
  val log: LoggingAdapter = Logging(OracActorSystem.system, this.getClass.getCanonicalName)

  val itemService: ItemService.type = ItemService
  val oracUserService: OracUserService.type = OracUserService
  val actionService: ActionService.type = ActionService
  val cronForwardEventsService: CronForwardEventsService.type = CronForwardEventsService

  val forwardingDestinations: Map[String, List[(ForwardingDestination, AbstractForwardingImplService)]] =
    elastic_client.forwarding.map(forwarding_index => {
      val forwarders = forwarding_index._2.map(item => {
        val forwarding_destination =
          ForwardingDestination(index = forwarding_index._1, url = item._1,
            service_type = SupportedForwardingServicesImpl.getValue(item._2),
            item_info_id = item._3)
        val forwarder = ForwardingServiceImplFactory.apply(forwarding_destination)
        (forwarding_destination, forwarder)
      })
      (forwarding_index._1, forwarders)
    })

  def forwardEnabled(index_name: String): Boolean = {
    forwardingDestinations.contains(index_name)
  }

  def getIndexName: String = {
    elastic_client.index_name + "." + elastic_client.forward_index_suffix
  }

  def create(document: Forward, refresh: Int): Future[Option[IndexDocumentResult]] = Future {
    val builder : XContentBuilder = jsonBuilder().startObject()

    val id: String = document.id
      .getOrElse(Checksum.sha512(document.doc_id + document.index + document.index_suffix +
        document.operation + RandomNumbers.getLong))
    builder.field("id", id)
    builder.field("doc_id", document.doc_id)
    builder.field("index", document.index)
    builder.field("index_suffix", document.index_suffix)
    builder.field("operation", document.operation)
    val timestamp: Long = Time.getTimestampMillis
    builder.field("timestamp", timestamp)

    builder.endObject()

    val client: TransportClient = elastic_client.get_client()
    val response = client.prepareIndex().setIndex(getIndexName)
      .setType(elastic_client.forward_index_suffix)
      .setId(id)
      .setCreate(true)
      .setSource(builder).get()

    if (refresh != 0) {
      val refresh_index = elastic_client.refresh_index(getIndexName)
      if(refresh_index.failed_shards_n > 0) {
        throw new Exception(this.getClass.getCanonicalName + " : index refresh failed: (" + getIndexName + ")")
      }
    }

    val doc_result: IndexDocumentResult = IndexDocumentResult(id = response.getId,
      version = response.getVersion,
      created = response.status == RestStatus.CREATED
    )

    Option {doc_result}
  }

  def deleteAll(index_name: String): Future[Option[DeleteDocumentsResult]] = Future {
    val client: TransportClient = elastic_client.get_client()
    val qb = QueryBuilders.termQuery("index", index_name)
    //val qb: QueryBuilder = QueryBuilders.matchAllQuery()
    val response: BulkByScrollResponse =
      DeleteByQueryAction.INSTANCE.newRequestBuilder(client).setMaxRetries(10)
        .source(getIndexName)
        .filter(qb)
        .filter(QueryBuilders.typeQuery(elastic_client.forward_index_suffix))
        .get()

    val deleted: Long = response.getDeleted

    val result: DeleteDocumentsResult = DeleteDocumentsResult(message = "delete", deleted = deleted)
    Option {result}
  }

  def forwardDeleteAll(index_name: String): Future[Unit] = Future {
    val item_iterator = itemService.getAllDocuments(index_name)
    item_iterator.map(doc => {
      val forward = Forward(doc_id = doc.id, index = index_name,
        index_suffix = itemService.elastic_client.item_index_suffix,
        operation = "delete")
      forward
    }).foreach(forward => {
      create(forward, 0)
    })

    val orac_user_iterator = oracUserService.getAllDocuments(index_name)
    orac_user_iterator.map(doc => {
      val forward = Forward(doc_id = doc.id, index = index_name,
        index_suffix = oracUserService.elastic_client.orac_user_index_suffix,
        operation = "delete")
      forward
    }).foreach(forward => {
      create(forward, 0)
    })

    val action_iterator = actionService.getAllDocuments(index_name)
    action_iterator.map(doc => {
      val forward = Forward(doc_id = doc.id.get, index = index_name,
        index_suffix = actionService.elastic_client.action_index_suffix,
        operation = "delete")
      forward
    }).foreach(forward => {
      create(forward, 0)
    })

    cronForwardEventsService.sendEvent()
  }

  def forwardReloadAll(index_name: String): Future[Unit] = Future {
    val item_iterator = itemService.getAllDocuments(index_name)
    item_iterator.map(doc => {
      val forward = Forward(doc_id = doc.id, index = index_name,
        index_suffix = itemService.elastic_client.item_index_suffix,
        operation = "create")
      forward
    }).foreach(forward => {
      create(forward, 0)
    })

    val orac_user_iterator = oracUserService.getAllDocuments(index_name)
    orac_user_iterator.map(doc => {
      val forward = Forward(doc_id = doc.id, index = index_name,
        index_suffix = oracUserService.elastic_client.orac_user_index_suffix,
        operation = "create")
      forward
    }).foreach(forward => {
      create(forward, 0)
    })

    val action_iterator = actionService.getAllDocuments(index_name)
    action_iterator.map(doc => {
      val forward = Forward(doc_id = doc.id.get, index = index_name,
        index_suffix = actionService.elastic_client.action_index_suffix,
        operation = "create")
      forward
    }).foreach(forward => {
      create(forward, 0)
    })

    cronForwardEventsService.sendEvent()
  }

  def delete(id: String, refresh: Int): Future[Option[DeleteDocumentResult]] = Future {
    val client: TransportClient = elastic_client.get_client()
    val response: DeleteResponse = client.prepareDelete().setIndex(getIndexName)
      .setType(elastic_client.forward_index_suffix).setId(id).get()


    if (refresh != 0) {
      val refresh_index = elastic_client.refresh_index(getIndexName)
      if(refresh_index.failed_shards_n > 0) {
        throw new Exception(this.getClass.getCanonicalName + " : index refresh failed: (" + getIndexName + ")")
      }
    }

    val doc_result: DeleteDocumentResult = DeleteDocumentResult(id = response.getId,
      version = response.getVersion,
      found = response.status != RestStatus.NOT_FOUND
    )

    Option {doc_result}
  }

  def read(ids: List[String]): Future[Option[List[Forward]]] = {
    val client: TransportClient = elastic_client.get_client()
    val multiget_builder: MultiGetRequestBuilder = client.prepareMultiGet()

    if (ids.nonEmpty) {
      multiget_builder.add(getIndexName, elastic_client.forward_index_suffix, ids:_*)
    } else {
      throw new Exception(this.getClass.getCanonicalName + " : ids list is empty: (" + getIndexName + ")")
    }

    val response: MultiGetResponse = multiget_builder.get()

    val documents : List[Forward] = response.getResponses
      .toList.filter((p: MultiGetItemResponse) => p.getResponse.isExists).map( { case(e) =>

      val item: GetResponse = e.getResponse

      val id : String = item.getId

      val source : Map[String, Any] = item.getSource.asScala.toMap

      val doc_id: String = source.get("doc_id") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val index: String = source.get("index") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val index_suffix: String = source.get("index_suffix") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val operation: String = source.get("operation") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val timestamp : Option[Long] = source.get("timestamp") match {
        case Some(t) => Option{t.asInstanceOf[Long]}
        case None => Option{0}
      }

      val document = Forward(id = Option{id}, doc_id = doc_id, index = index, index_suffix = index_suffix,
        operation = operation, timestamp = timestamp)
      document
    })

    Future { Option { documents } }
  }

  def getAllDocuments: Iterator[Forward] = {
    val qb: QueryBuilder = QueryBuilders.matchAllQuery()
    var scrollResp: SearchResponse = elastic_client.get_client()
      .prepareSearch(getIndexName)
      .addSort("timestamp", SortOrder.ASC)
      .setScroll(new TimeValue(60000))
      .setQuery(qb)
      .setSize(100).get()

    val iterator = Iterator.continually {

      val documents = scrollResp.getHits.getHits.toList.map( { case(e) =>
        val item: SearchHit = e

        val id : String = item.getId

        val source : Map[String, Any] = item.getSourceAsMap.asScala.toMap

        val doc_id: String = source.get("doc_id") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val index: String = source.get("index") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val index_suffix: String = source.get("index_suffix") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val operation: String = source.get("operation") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val timestamp : Option[Long] = source.get("timestamp") match {
          case Some(t) => Option{t.asInstanceOf[Long]}
          case None => Option{0}
        }

        val document = Forward(id = Option{id}, doc_id = doc_id, index = index, index_suffix = index_suffix,
          operation = operation, timestamp = timestamp)
        document
      })

      scrollResp = elastic_client.get_client().prepareSearchScroll(scrollResp.getScrollId)
        .setScroll(new TimeValue(60000)).execute().actionGet()

      (documents, documents.nonEmpty)
    }.takeWhile(_._2).map(_._1).flatten

    iterator
  }
}
