package io.elegans.orac.resources

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 22/11/17.
  */

import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.server.Route
import io.elegans.orac.entities._
import io.elegans.orac.routing._
import io.elegans.orac.services.{ActionService}
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.CircuitBreaker
import io.elegans.orac.OracActorSystem

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

trait ActionResource extends MyResource {

  val actionService = ActionService

  def actionRoutes: Route =
    pathPrefix("""^(index_(?:[A-Za-z0-9_]+))$""".r ~ Slash ~ """action""") { index_name =>
      pathEnd {
        post {
          authenticateBasicPFAsync(realm = auth_realm,
            authenticator = authenticator.authenticator) { user =>
            authorizeAsync(_ =>
              authenticator.hasPermissions(user, index_name, Permissions.write)) {
              extractMethod { method =>
                parameters("refresh".as[Int] ? 0) { refresh =>
                  entity(as[Action]) { document =>
                    val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(actionService.create(index_name, user.id, document, refresh)) {
                      case Success(t) =>
                        completeResponse(StatusCodes.Created, StatusCodes.BadRequest, Option {
                          t
                        })
                      case Failure(e) =>
                        log.error(this.getClass.getCanonicalName + " index(" + index_name + ")" +
                          "method=" + method.toString + " : " + e.getMessage)
                        completeResponse(StatusCodes.BadRequest,
                          Option {
                            ReturnMessageData(code = 100, message = e.getMessage)
                          })
                    }
                  }
                }
              }
            }
          }
        } ~
          get {
            authenticateBasicPFAsync(realm = auth_realm,
              authenticator = authenticator.authenticator) { user =>
              authorizeAsync(_ =>
                authenticator.hasPermissions(user, index_name, Permissions.read)) {
                extractMethod { method =>
                  parameters("ids".as[String].*) { ids =>
                    val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                    onCompleteWithBreaker(breaker)(actionService.read(index_name, ids.toList)) {
                      case Success(t) =>
                        completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                          t
                        })
                      case Failure(e) =>
                        log.error(this.getClass.getCanonicalName + " index(" + index_name + ")" +
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
            authenticateBasicPFAsync(realm = auth_realm,
              authenticator = authenticator.authenticator) { user =>
              authorizeAsync(_ =>
                authenticator.hasPermissions(user, index_name, Permissions.write)) {
                extractMethod { method =>
                  entity(as[UpdateAction]) { update =>
                    parameters("refresh".as[Int] ? 0) { refresh =>
                      val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(actionService.update(index_name, id, update, refresh)) {
                        case Success(t) =>
                          completeResponse(StatusCodes.OK, StatusCodes.BadRequest, Option {
                            t
                          })
                        case Failure(e) =>
                          log.error(this.getClass.getCanonicalName + " index(" + index_name + ")" +
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
          } ~
            delete {
              authenticateBasicPFAsync(realm = auth_realm,
                authenticator = authenticator.authenticator) { user =>
                authorizeAsync(_ =>
                  authenticator.hasPermissions(user, index_name, Permissions.read)) {
                  extractMethod { method =>
                    parameters("refresh".as[Int] ? 0) { refresh =>
                      val breaker: CircuitBreaker = OracCircuitBreaker.getCircuitBreaker()
                      onCompleteWithBreaker(breaker)(actionService.delete(index_name, id, refresh)) {
                        case Success(t) =>
                          if (t.isDefined) {
                            completeResponse(StatusCodes.OK, t)
                          } else {
                            completeResponse(StatusCodes.BadRequest, t)
                          }
                        case Failure(e) =>
                          log.error(this.getClass.getCanonicalName + " index(" + index_name + ")" +
                            "method=" + method.toString + " : " + e.getMessage)
                          completeResponse(StatusCodes.BadRequest,
                            Option {
                              ReturnMessageData(code = 105, message = e.getMessage)
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
