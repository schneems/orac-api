package io.elegans.orac.resources

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.elegans.orac.serializers.JsonSupport
import io.elegans.orac.OracService
import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.RouteTestTimeout
import scala.concurrent.duration._
import akka.testkit._
import akka.http.scaladsl.server


class RootAPIResourceTest extends WordSpec with Matchers with ScalatestRouteTest with JsonSupport {
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(10.seconds.dilated(system))
  val service = new OracService
  val routes: server.Route = service.routes

  "StarChat" should {
    "return a 200 if the service responds (Health Check)" in {
      Get(s"/") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}

