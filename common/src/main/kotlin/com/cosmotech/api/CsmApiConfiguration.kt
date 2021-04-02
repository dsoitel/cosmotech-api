// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api

import com.azure.cosmos.CosmosAsyncClient
import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import springfox.documentation.spring.web.DocumentationCache
import springfox.documentation.spring.web.plugins.DocumentationPluginsManager
import springfox.documentation.swagger.web.InMemorySwaggerResourcesProvider
import springfox.documentation.swagger.web.SwaggerResource

@Configuration
class CsmApiConfiguration {

  @Value("\${azure.cosmosdb.serviceEndpoint}")
  private lateinit var azureCosmosServiceEndpoint: String

  @Value("\${azure.cosmosdb.key}") private lateinit var azureCosmosKey: String

  @Bean
  fun cosmosClientBuilder(): CosmosClientBuilder =
      CosmosClientBuilder().endpoint(azureCosmosServiceEndpoint).key(azureCosmosKey)

  @Bean fun cosmosClient(): CosmosClient = cosmosClientBuilder().buildClient()

  @Bean fun cosmosAsyncClient(): CosmosAsyncClient = cosmosClientBuilder().buildAsyncClient()
}

@Component
@Primary
class CsmApiSwaggerResourcesProvider(
    environment: Environment?,
    documentationCache: DocumentationCache?,
    pluginsManager: DocumentationPluginsManager?
) : InMemorySwaggerResourcesProvider(environment, documentationCache, pluginsManager) {

  override fun get(): List<SwaggerResource> {
    return listOf(
        SwaggerResource().apply {
          name = "Main definition"
          url = "/openapi.json"
        })
  }
}
