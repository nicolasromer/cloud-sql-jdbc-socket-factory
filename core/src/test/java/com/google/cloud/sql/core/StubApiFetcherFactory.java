/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdmin.Builder;
import com.google.cloud.sql.ApiFetcherFactory;

public class StubApiFetcherFactory implements ApiFetcherFactory {

  HttpTransport httpTransport;

  StubApiFetcherFactory(HttpTransport transport) {
    this.httpTransport = transport;
  }

  @Override
  public SqlAdminApiFetcher create(HttpRequestInitializer credentials) {
    SQLAdmin sqlAdmin =
        new Builder(
                httpTransport != null ? httpTransport : new MockHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credentials)
            .setApplicationName("Mock SQL Admin")
            .build();
    return new SqlAdminApiFetcher(sqlAdmin);
  }
}
