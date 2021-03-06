package io.elegans.orac.services

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 8/12/17.
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
import io.elegans.orac.entities.ReconcileHistory

/**
  * Implements reconciliation functions
  */

object ReconcileHistoryService {
  val elastic_client: SystemIndexManagementElasticClient.type = SystemIndexManagementElasticClient
  val log: LoggingAdapter = Logging(OracActorSystem.system, this.getClass.getCanonicalName)

  val itemService: ItemService.type = ItemService
  val oracUserService: OracUserService.type = OracUserService
  val actionService: ActionService.type = ActionService

  def getIndexName: String = {
    elastic_client.index_name + "." + elastic_client.reconcile_history_index_suffix
  }

  def create(document:ReconcileHistory, refresh: Int): Future[Option[IndexDocumentResult]] = Future {
    val builder : XContentBuilder = jsonBuilder().startObject()

    val timestamp: Long = Time.getTimestampMillis
    val end_timestamp: Long = document.end_timestamp.getOrElse(timestamp)
    val id: String = document.id
      .getOrElse(Checksum.sha512(document.toString + timestamp + RandomNumbers.getLong))

    builder.field("id", id)
    builder.field("old_id", document.old_id)
    builder.field("new_id", document.new_id)
    builder.field("index", document.index)
    builder.field("index_suffix", document.index_suffix)
    builder.field("type", document.`type`)
    builder.field("retry", document.retry)
    builder.field("end_timestamp", end_timestamp)
    builder.field("insert_timestamp", document.insert_timestamp)

    builder.endObject()

    val client: TransportClient = elastic_client.get_client()
    val response = client.prepareIndex().setIndex(getIndexName)
      .setType(elastic_client.reconcile_history_index_suffix)
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
    val response: BulkByScrollResponse =
      DeleteByQueryAction.INSTANCE.newRequestBuilder(client).setMaxRetries(10)
        .source(getIndexName)
        .filter(qb)
        .filter(QueryBuilders.typeQuery(elastic_client.reconcile_history_index_suffix))
        .get()

    val deleted: Long = response.getDeleted

    val result: DeleteDocumentsResult = DeleteDocumentsResult(message = "delete", deleted = deleted)
    Option {result}
  }

  def delete(id: String, refresh: Int): Future[Option[DeleteDocumentResult]] = Future {
    val client: TransportClient = elastic_client.get_client()
    val response: DeleteResponse = client.prepareDelete().setIndex(getIndexName)
      .setType(elastic_client.reconcile_history_index_suffix).setId(id).get()


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

  def read(ids: List[String]): Future[Option[List[ReconcileHistory]]] = {
    val client: TransportClient = elastic_client.get_client()
    val multiget_builder: MultiGetRequestBuilder = client.prepareMultiGet()

    if (ids.nonEmpty) {
      multiget_builder.add(getIndexName, elastic_client.reconcile_history_index_suffix, ids:_*)
    } else {
      throw new Exception(this.getClass.getCanonicalName + " : ids list is empty: (" + getIndexName + ")")
    }

    val response: MultiGetResponse = multiget_builder.get()

    val documents : List[ReconcileHistory] = response.getResponses
      .toList.filter((p: MultiGetItemResponse) => p.getResponse.isExists).map( { case(e) =>

      val item: GetResponse = e.getResponse

      val id : String = item.getId

      val source : Map[String, Any] = item.getSource.asScala.toMap

      val new_id: String = source.get("new_id") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val old_id: String = source.get("old_id") match {
        case Some(t) => t.asInstanceOf[String]
        case None => ""
      }

      val index: Option[String] = source.get("index") match {
        case Some(t) => Some(t.asInstanceOf[String])
        case None => Some("")
      }

      val index_suffix: Option[String] = source.get("index_suffix") match {
        case Some(t) => Some(t.asInstanceOf[String])
        case None => Some("")
      }

      val `type`: ReconcileType.Reconcile = source.get("type") match {
        case Some(t) => ReconcileType.getValue(t.asInstanceOf[String])
        case None => ReconcileType.unknown
      }

      val retry : Long = source.get("retry") match {
        case Some(t) => t.asInstanceOf[Integer].toLong
        case None => 0
      }

      val insert_timestamp : Long = source.get("insert_timestamp") match {
        case Some(t) => t.asInstanceOf[Long]
        case None => 0
      }

      val end_timestamp : Option[Long] = source.get("end_timestamp") match {
        case Some(t) => Option{t.asInstanceOf[Long]}
        case None => Option{0}
      }

      val document = ReconcileHistory(id = Option{id}, new_id = new_id, old_id = old_id,
        index = index, index_suffix = index_suffix,
        `type` = `type`, retry = retry, insert_timestamp = insert_timestamp, end_timestamp = end_timestamp)
      document
    })

    Future { Option { documents } }
  }

  def getAllDocuments: Iterator[ReconcileHistory] = {
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

        val new_id: String = source.get("new_id") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val old_id: String = source.get("old_id") match {
          case Some(t) => t.asInstanceOf[String]
          case None => ""
        }

        val index: Option[String] = source.get("index") match {
          case Some(t) => Some(t.asInstanceOf[String])
          case None => Some("")
        }

        val index_suffix: Option[String] = source.get("index_suffix") match {
          case Some(t) => Some(t.asInstanceOf[String])
          case None => Some("")
        }

        val `type`: ReconcileType.Reconcile = source.get("type") match {
          case Some(t) => ReconcileType.getValue(t.asInstanceOf[String])
          case None => ReconcileType.unknown
        }

        val retry : Long = source.get("retry") match {
          case Some(t) => t.asInstanceOf[Integer].toLong
          case None => 0
        }

        val insert_timestamp : Long = source.get("insert_timestamp") match {
          case Some(t) => t.asInstanceOf[Long]
          case None => 0
        }

        val end_timestamp : Option[Long] = source.get("end_timestamp") match {
          case Some(t) => Option{t.asInstanceOf[Long]}
          case None => Option{0}
        }

        val document = ReconcileHistory(id = Option{id}, new_id = new_id, old_id = old_id,
          index = index, index_suffix = index_suffix,
          `type` = `type`, retry = retry, insert_timestamp = insert_timestamp, end_timestamp = end_timestamp)
        document
      })

      scrollResp = elastic_client.get_client().prepareSearchScroll(scrollResp.getScrollId)
        .setScroll(new TimeValue(60000)).execute().actionGet()

      (documents, documents.nonEmpty)
    }.takeWhile(_._2).map(_._1).flatten

    iterator
  }
}
