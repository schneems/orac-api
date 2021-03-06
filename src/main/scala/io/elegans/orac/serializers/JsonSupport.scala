package io.elegans.orac.serializers

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 27/06/16.
  */

import io.elegans.orac.entities._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val updateActionFormat = jsonFormat8(UpdateAction)
  implicit val actionFormat = jsonFormat9(Action)
  implicit val actionsFormat = jsonFormat1(Actions)
  implicit val numericalPropertiesFormat = jsonFormat2(NumericalProperties)
  implicit val timestampPropertiesFormat = jsonFormat2(TimestampProperties)
  implicit val stringPropertiesFormat = jsonFormat2(StringProperties)
  implicit val geoPointFormat = jsonFormat2(OracGeoPoint)
  implicit val geoPointPropertiesFormat = jsonFormat2(GeoPointProperties)
  implicit val itemPropertiesFormat = jsonFormat5(OracProperties)
  implicit val updateItemFormat = jsonFormat4(UpdateItem)
  implicit val itemFormat = jsonFormat5(Item)
  implicit val itemListFormat = jsonFormat1(Items)
  implicit val updateOracUserFormat = jsonFormat4(UpdateOracUser)
  implicit val oracUserFormat = jsonFormat5(OracUser)
  implicit val oracUsers = jsonFormat1(OracUsers)
  implicit val returnMessageDataFormat = jsonFormat2(ReturnMessageData)
  implicit val updateDocumentResultFormat = jsonFormat5(UpdateDocumentResult)
  implicit val indexManagementResponseFormat = jsonFormat1(IndexManagementResponse)
  implicit val indexDocumentResultFormat = jsonFormat3(IndexDocumentResult)
  implicit val deleteDocumentResultFormat = jsonFormat3(DeleteDocumentResult)
  implicit val deleteDocumentsResultFormat = jsonFormat2(DeleteDocumentsResult)
  implicit val failedShardFormat = jsonFormat4(FailedShard)
  implicit val refreshIndexResultFormat = jsonFormat5(RefreshIndexResult)
  implicit val refreshIndexResultsFormat = jsonFormat1(RefreshIndexResults)
  implicit val recommendationFormat = jsonFormat7(Recommendation)
  implicit val recommendationsFormat = jsonFormat1(Recommendations)
  implicit val updateRecommendationFormat = jsonFormat6(UpdateRecommendation)
  implicit val recommendationHistoryFormat = jsonFormat10(RecommendationHistory)
  implicit val recommendationsHistoryFormat = jsonFormat1(RecommendationsHistory)
  implicit val updateRecommendationHistoryFormat = jsonFormat9(UpdateRecommendationHistory)

  implicit object PermissionsJsonFormat extends JsonFormat[Permissions.Value] {
    def write(obj: Permissions.Value): JsValue = JsString(obj.toString)
    def read(json: JsValue): Permissions.Value = json match {
      case JsString(str) => Permissions.withName(str)
      case _ => throw DeserializationException("Permission string expected")
    }
  }

  implicit object ReconcileTypeJsonFormat extends JsonFormat[ReconcileType.Value] {
    def write(obj: ReconcileType.Value): JsValue = JsString(obj.toString)
    def read(json: JsValue): ReconcileType.Value = json match {
      case JsString(str) => ReconcileType.withName(str)
      case _ => throw DeserializationException("ReconcileType string expected")
    }
  }

  implicit val userFormat = jsonFormat4(User)
  implicit val userUpdateFormat = jsonFormat3(UserUpdate)
  implicit val forwardFormat = jsonFormat6(Forward)
  implicit val forwardAllFormat = jsonFormat2(ForwardAll)
  implicit val itemInfoFormat = jsonFormat7(ItemInfo)
  implicit val updateItemInfoFormat = jsonFormat6(UpdateItemInfo)
  implicit val itemInfoRecordsFormat = jsonFormat1(ItemInfoRecords)
  implicit val reconcileFormat = jsonFormat8(Reconcile)
  implicit val reconcileHistoryFormat = jsonFormat9(ReconcileHistory)
  implicit val updateReconcileFormat = jsonFormat7(UpdateReconcile)
}
