package com.parking.backend.pricing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ParkingPricingProperties.class)
class ParkingPricingConfiguration {
}
