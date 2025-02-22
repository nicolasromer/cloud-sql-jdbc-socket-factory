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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.net.ssl.SSLContext;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Test;

public class SqlAdminApiFetcherTest {

  public static final String SAMPLE_PUBLIC_IP = "34.1.2.3";
  public static final String SAMPLE_PRIVATE_IP = "10.0.0.1";
  public static final String INSTANCE_CONNECTION_NAME = "p:r:i";
  public static final String DATABASE_VERSION = "POSTGRES14";

  @Test
  public void testFetchInstanceData_returnsIpAddresses()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {
    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ListenableFuture<InstanceData> instanceDataFuture =
        fetcher.getInstanceData(
            new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
            null,
            AuthType.PASSWORD,
            newTestExecutor(),
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    InstanceData instanceData = instanceDataFuture.get();
    assertThat(instanceData.getSslContext()).isInstanceOf(SSLContext.class);

    Map<String, String> ipAddrs = instanceData.getIpAddrs();
    assertThat(ipAddrs.get("PRIMARY")).isEqualTo(SAMPLE_PUBLIC_IP);
    assertThat(ipAddrs.get("PRIVATE")).isEqualTo(SAMPLE_PRIVATE_IP);
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    //noinspection UnstableApiUsage
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  @Test
  public void testFetchInstanceData_throwsException_whenIamAuthnIsNotSupported()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi =
        buildMockAdminApi(INSTANCE_CONNECTION_NAME, "SQLSERVER_2019_STANDARD");
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ListenableFuture<InstanceData> instanceData =
        fetcher.getInstanceData(
            new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
            OAuth2CredentialsWithRefresh.newBuilder()
                .setRefreshHandler(
                    mockAdminApi.getRefreshHandler(
                        "refresh-token", Date.from(Instant.now().plus(1, ChronoUnit.HOURS))))
                .setAccessToken(new AccessToken("my-token", Date.from(Instant.now())))
                .build(),
            AuthType.IAM,
            newTestExecutor(),
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    ExecutionException ex = assertThrows(ExecutionException.class, instanceData::get);
    assertThat(ex)
        .hasMessageThat()
        .contains("[p:r:i] IAM Authentication is not supported for SQL Server instances");
  }

  @Test
  public void testFetchInstanceData_throwsException_whenTokenIsEmpty()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ListenableFuture<InstanceData> instanceData =
        fetcher.getInstanceData(
            new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
            OAuth2CredentialsWithRefresh.newBuilder()
                .setRefreshHandler(
                    mockAdminApi.getRefreshHandler(
                        "", Date.from(Instant.now().plus(1, ChronoUnit.HOURS)) /* empty */))
                .setAccessToken(new AccessToken("" /* ignored */, Date.from(Instant.now())))
                .build(),
            AuthType.IAM,
            newTestExecutor(),
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    ExecutionException ex = assertThrows(ExecutionException.class, instanceData::get);

    assertThat(ex).hasMessageThat().contains("Access Token has length of zero");
  }

  @Test
  public void testFetchInstanceData_throwsException_whenTokenIsExpired()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION);
    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create());

    ListenableFuture<InstanceData> instanceData =
        fetcher.getInstanceData(
            new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
            OAuth2CredentialsWithRefresh.newBuilder()
                .setRefreshHandler(
                    mockAdminApi.getRefreshHandler(
                        "refresh-token",
                        Date.from(Instant.now().minus(1, ChronoUnit.HOURS)) /* 1 hour ago */))
                .setAccessToken(new AccessToken("original-token", Date.from(Instant.now())))
                .build(),
            AuthType.IAM,
            newTestExecutor(),
            Futures.immediateFuture(mockAdminApi.getClientKeyPair()));

    ExecutionException ex = assertThrows(ExecutionException.class, instanceData::get);

    assertThat(ex).hasMessageThat().contains("Access Token expiration time is in the past");
  }

  @SuppressWarnings("SameParameterValue")
  private MockAdminApi buildMockAdminApi(String instanceConnectionName, String databaseVersion)
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        instanceConnectionName, SAMPLE_PUBLIC_IP, SAMPLE_PRIVATE_IP, databaseVersion);
    mockAdminApi.addGenerateEphemeralCertResponse(instanceConnectionName, Duration.ofHours(1));
    return mockAdminApi;
  }
}
