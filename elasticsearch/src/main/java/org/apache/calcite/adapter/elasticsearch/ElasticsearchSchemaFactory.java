/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaFactory;
import org.apache.calcite.schema.SchemaPlus;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Factory that creates an {@link ElasticsearchSchema}.
 *
 * <p>Allows a custom schema to be included in a model.json file.
 */
@SuppressWarnings("UnusedDeclaration")
public class ElasticsearchSchemaFactory implements SchemaFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaFactory.class);

  public ElasticsearchSchemaFactory() {
  }

  @Override public Schema create(SchemaPlus parentSchema, String name,
      Map<String, Object> operand) {

    final Map map = (Map) operand;

    final ObjectMapper mapper = new ObjectMapper();
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    try {

      List<HttpHost> hosts;

      if (map.containsKey("hosts")) {
        final List<String> configHosts = mapper.readValue((String) map.get("hosts"),
                new TypeReference<List<String>>() { });

        hosts = configHosts
                .stream()
                .map(host -> HttpHost.create(host))
                .collect(Collectors.toList());
      } else if (map.containsKey("coordinates")) {
        final Map<String, Integer> coordinates = mapper.readValue((String) map.get("coordinates"),
                new TypeReference<Map<String, Integer>>() { });

        hosts =  coordinates
                .entrySet()
                .stream()
                .map(entry -> new HttpHost(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        LOGGER.warn("Prefer using hosts, coordinates is deprecated.");
      } else {
        throw new IllegalArgumentException
        ("Both 'coordinates' and 'hosts' is missing in configuration. Provide one of them.");
      }
      final String pathPrefix = (String) map.get("pathPrefix");
      // create client
      String username = (String) map.get("username");
      String password = (String) map.get("password");
      final RestClient client = connect(hosts, pathPrefix, username, password);
      final String index = (String) map.get("index");

      return new ElasticsearchSchema(client, new ObjectMapper(), index);
    } catch (IOException e) {
      throw new RuntimeException("Cannot parse values from json", e);
    }
  }

  /**
   * Builds elastic rest client from user configuration
   * @param hosts list of ES HTTP Hosts to connect to
   * @param username the username of ES
   * @param password the password of ES
   * @return newly initialized low-level rest http client for ES
   */
  private static RestClient connect(List<HttpHost> hosts, String pathPrefix,
                                    String username, String password) {

    Objects.requireNonNull(hosts, "hosts or coordinates");
    Preconditions.checkArgument(!hosts.isEmpty(), "no ES hosts specified");

    RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[hosts.size()]));

    if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY,
          new UsernamePasswordCredentials(username, password));
      builder.setHttpClientConfigCallback(httpClientBuilder ->
          httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
    }

    if (pathPrefix != null && !pathPrefix.isEmpty()) {
      builder.setPathPrefix(pathPrefix);
    }
    return builder.build();
  }

}

// End ElasticsearchSchemaFactory.java
