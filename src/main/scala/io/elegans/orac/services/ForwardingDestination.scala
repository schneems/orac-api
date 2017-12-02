package io.elegans.orac.services

/**
  * Created by Angelo Leto <angelo.leto@elegans.io> on 1/12/17.
  */

case class ForwardingDestination(
                                  url: String,
                                  service_type: SupportedForwardingServicesImpl.Value
                                )