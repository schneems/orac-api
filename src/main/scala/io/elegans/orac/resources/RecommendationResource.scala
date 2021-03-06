package io.elegans.orac.resources

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 23/11/17.
  */

import akka.http.scaladsl.server.Route
import io.elegans.orac.entities._
import io.elegans.orac.routing._
import io.elegans.orac.services.RecommendationService
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.CircuitBreaker
import org.elasticsearch.index.engine.VersionConflictEngineException

import scala.util.{Failure, Success, Try}


trait RecommendationResource extends MyResource {

  val recommendationService: RecommendationService.type = RecommendationService

  def recommendationRoutes: Route =
    pathPrefix("""^(index_(?:[A-Za-z0-9_]+))$""".r ~ Slash ~ "recommendation") { index_name =>
    pathEnd {
        post {
          authenticateBasicAsync(realm = auth_realm,
            authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, index_name, Permissions.create_recomm)) {
              extractMethod { method =>
                parameters("refresh".as[Int] ? 0) { refresh =>
                  entity(as[Recommendation]) { document =>
                    val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(recommendationService.create(index_name, document, refresh)) {
                      case Success(t) =>
                        completeResponse(StatusCodes.Created, StatusCodes.BadRequest, Option {
                          t
                        })
                      case Failure(e) => e match {
                        case vcee: VersionConflictEngineException =>
                          log.error(this.getClass.getCanonicalName + " index(" + index_name + ") " +
                            "method=" + method.toString + " : " + vcee.getMessage)
                          completeResponse(StatusCodes.Conflict, Option.empty[String])
                        case e: Exception =>
                          log.error(this.getClass.getCanonicalName + " index(" + index_name + ") " +
                            "method=" + method.toString + " : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest, Option.empty[String])
                      }
                    }
                  }
                }
              }
            }
          }
        } ~
          get {
            authenticateBasicAsync(realm = auth_realm,
              authenticator = authenticator.authenticator) { user =>
              authorizeAsync(_ =>
                authenticator.hasPermissions(user, index_name, Permissions.read_recomm)) {
                extractMethod { method =>
                  parameters("id".as[String].*) { id =>
                    val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(recommendationService.read(index_name, user.id, id.toList)) {
                      case Success(t) =>
                        completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                          t
                        })
                      case Failure(e) =>
                        log.error(this.getClass.getCanonicalName + " index(" + index_name + ") " +
                          "method=" + method.toString + " : " + e.getMessage)
                        completeResponse(StatusCodes.BadRequest,
                          Option {
                            ReturnMessageData(code = 101, message = e.getMessage)
                          })
                    }
                  }
                }
              }
            }
          }
      } ~
        path(Segment) { id =>
          put {
            authenticateBasicAsync(realm = auth_realm,
              authenticator = authenticator.authenticator) { user =>
              authorizeAsync(_ =>
                authenticator.hasPermissions(user, index_name, Permissions.update_recomm)) {
                extractMethod { method =>
                  entity(as[UpdateRecommendation]) { update =>
                    parameters("refresh".as[Int] ? 0) { refresh =>
                      val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(recommendationService.update(index_name, id, update, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                            t
                          })
                        case Failure(e) =>
                          log.error(this.getClass.getCanonicalName + " index(" + index_name + ") " +
                            "method=" + method.toString + " : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 102, message = e.getMessage)
                            })
                      }
                    }
                  }
                }
              }
            }
          } ~
            delete {
              authenticateBasicAsync(realm = auth_realm,
                authenticator = authenticator.authenticator) { user =>
                authorizeAsync(_ =>
                  authenticator.hasPermissions(user, index_name, Permissions.delete_recomm)) {
                  extractMethod { method =>
                    parameters("refresh".as[Int] ? 0) { refresh =>
                      val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(recommendationService.delete(index_name, id, refresh)) {
                        case Success(t) =>
                          if (t.isDefined) {
                            completeResponse(StatusCodes.OK, t)
                          } else {
                            completeResponse(StatusCodes.BadRequest, t)
                          }
                        case Failure(e) =>
                          log.error(this.getClass.getCanonicalName + " index(" + index_name + ") " +
                            "method=" + method.toString + " : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 103, message = e.getMessage)
                            })
                      }
                    }
                  }
                }
              }
            }
        }
    }

  def userRecommendationRoutes: Route =
    pathPrefix("""^(index_(?:[A-Za-z0-9_]+))$""".r ~ Slash ~ """user_recommendation""") { index_name =>
      path(Segment) { id =>
        get {
          authenticateBasicAsync(realm = auth_realm,
            authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, index_name, Permissions.read_recomm)) {
              extractMethod { method =>
                parameters("from".as[Int] ? 0, "size".as[Int] ? 10) { (from, size) =>
                  val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                  onCompleteWithBreaker(breaker)(
                    recommendationService.getRecommendationsByUser(index_name, user.id, id, from, size)) {
                    case Success(t) =>
                      completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                        t
                      })
                    case Failure(e) =>
                      log.error(this.getClass.getCanonicalName + " index(" + index_name + ") " +
                        "method=" + method.toString + " : " + e.getMessage)
                      completeResponse(StatusCodes.BadRequest,
                        Option {
                          ReturnMessageData(code = 104, message = e.getMessage)
                        })
                  }
                }
              }
            }
          }
        }
      }
    }
}

