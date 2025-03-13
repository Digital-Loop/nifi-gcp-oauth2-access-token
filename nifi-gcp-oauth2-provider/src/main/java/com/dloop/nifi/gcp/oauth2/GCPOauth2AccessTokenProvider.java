/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dloop.nifi.gcp.oauth2;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.iam.credentials.v1.IamCredentialsClient;
import com.google.cloud.iam.credentials.v1.IamCredentialsSettings;
import com.google.cloud.iam.credentials.v1.SignJwtRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.gcp.credentials.service.GCPCredentialsService;
import org.apache.nifi.oauth2.AccessToken;
import org.apache.nifi.oauth2.OAuth2AccessTokenProvider;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.gcp.util.GoogleUtils;
import org.apache.nifi.reporting.InitializationException;

@Tags({ "gcp", "oauth2", "provider", "authorization", "access token", "http" })
@CapabilityDescription("Provides OAuth 2.0 access tokens for Google REST APIs.")
@SeeAlso({ OAuth2AccessTokenProvider.class, GCPCredentialsService.class })
public class GCPOauth2AccessTokenProvider
    extends AbstractControllerService
    implements OAuth2AccessTokenProvider {

    private static final String DEFAULT_SCOPE =
        "https://www.googleapis.com/auth/cloud-platform";

    public static final PropertyDescriptor SCOPE =
        new PropertyDescriptor.Builder()
            .name("scope")
            .displayName("Scope")
            .description(
                "Whitespace-delimited, case-sensitive list of scopes of the access request (as per the OAuth 2.0 specification). More information: https://developers.google.com/identity/protocols/oauth2/scopes"
            )
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .defaultValue(DEFAULT_SCOPE)
            .build();

    public static final PropertyDescriptor SERVICE_ACCOUNT =
        new PropertyDescriptor.Builder()
            .name("impersonate-service-account")
            .displayName("Impersonate Service Account")
            .description(
                "Allow credentials issued to a user or service account to impersonate another service account. The source project must enable the \"IAMCredentials\" API.\nAlso, the target service account must grant the originating principal the \"Service Account Token Creator\" IAM role. More information: https://cloud.google.com/iam/docs/service-account-impersonation"
            )
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor DELEGATE =
        new PropertyDescriptor.Builder()
            .name("delegate")
            .displayName("Domain-wide delegation")
            .description(
                "If the credentials support domain-wide delegation, creates a copy of the identity so that it impersonates the specified user; otherwise, returns the same instance. To enable domain-wide delegation it is necessary to use Google Workspace in your organization. More information: https://developers.google.com/cloud-search/docs/guides/delegation"
            )
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor PROJECT_ID =
        new PropertyDescriptor.Builder()
            .name("project-id")
            .displayName("Project ID")
            .description(
                "Sets a custom quota/billing project for the JWT sign request from the IAM Credentials API. Important: The calling user or service account must have the serviceusage.services.use IAM permission for a project to be able to designate it as your quota project. This setting does not have an affect on the quota project of the REST API requests that will use the access token of this service. To set a custom quota project for the REST API requests set the custom header 'x-goog-user-project' as a dynamic property on the InvokeHTTP processor. More information: https://cloud.google.com/docs/quotas/set-quota-project#rest_request"
            )
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .dependsOn(SERVICE_ACCOUNT)
            .dependsOn(DELEGATE)
            .build();

    private static final List<PropertyDescriptor> properties;

    static {
        final List<PropertyDescriptor> props = new ArrayList<>();
        props.add(GoogleUtils.GCP_CREDENTIALS_PROVIDER_SERVICE);
        props.add(SCOPE);
        props.add(SERVICE_ACCOUNT);
        props.add(DELEGATE);
        props.add(PROJECT_ID);
        properties = Collections.unmodifiableList(props);
    }

    private volatile GoogleCredentials googleCredentials;
    private volatile IamCredentialsClient iamCredentialsClient;
    private volatile AccessToken accessToken;
    private volatile String serviceAccount;
    private volatile String scope;
    private volatile String delegate;
    private volatile String projectId;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context)
        throws InitializationException, IOException {
        // Reset processor
        googleCredentials = null;
        iamCredentialsClient = null;
        accessToken = null;

        // Get GCP credentials service
        GCPCredentialsService gcpCredentialsService = context
            .getProperty(GoogleUtils.GCP_CREDENTIALS_PROVIDER_SERVICE)
            .asControllerService(GCPCredentialsService.class);

        // Initialize credentials
        googleCredentials = gcpCredentialsService
            .getGoogleCredentials()
            .createScoped(DEFAULT_SCOPE);

        scope = context.getProperty(SCOPE).getValue().replaceAll("\\s+", " ");
        serviceAccount = context.getProperty(SERVICE_ACCOUNT).getValue();
        delegate = context.getProperty(DELEGATE).getValue();
        projectId = context.getProperty(PROJECT_ID).getValue();

        if (serviceAccount != null) {
            // Impersonate service account with user delegation
            if (delegate != null) {
                IamCredentialsSettings.Builder builder =
                    IamCredentialsSettings.newBuilder()
                        .setCredentialsProvider(
                            FixedCredentialsProvider.create(googleCredentials)
                        );

                if (projectId != null) {
                    builder.setQuotaProjectId(projectId);
                }

                iamCredentialsClient = IamCredentialsClient.create(
                    builder.build()
                );

                return;
            }

            // Impersonate service account without user delegation
            googleCredentials = ImpersonatedCredentials.newBuilder()
                .setSourceCredentials(googleCredentials)
                .setTargetPrincipal(serviceAccount)
                .setScopes(Arrays.asList(scope.split(" ")))
                .build();

            return;
        }

        googleCredentials = googleCredentials.createScoped(scope.split(" "));

        if (delegate != null) {
            googleCredentials = googleCredentials.createDelegated(delegate);
        }
    }

    @Override
    public AccessToken getAccessDetails() {
        if (accessToken != null && !accessToken.isExpired()) {
            return accessToken;
        }

        com.google.auth.oauth2.AccessToken gcpAccessToken;
        if (serviceAccount != null && delegate != null) {
            gcpAccessToken = getJwtCredentials();
        } else {
            gcpAccessToken = getDefaultCredentials();
        }

        Long expiresIn = Duration.between(
            Instant.now(),
            gcpAccessToken.getExpirationTime().toInstant()
        ).getSeconds();

        accessToken = new AccessToken(
            gcpAccessToken.getTokenValue(),
            null,
            "OAuth2",
            expiresIn,
            scope
        );

        return accessToken;
    }

    private com.google.auth.oauth2.AccessToken getDefaultCredentials() {
        try {
            googleCredentials.refreshIfExpired();

            return googleCredentials.getAccessToken();
        } catch (IOException e) {
            getLogger().error(e.getMessage(), e);

            return null;
        }
    }

    private com.google.auth.oauth2.AccessToken getJwtCredentials() {
        try {
            long iat = Instant.now().getEpochSecond();
            long exp = iat + 3600;
            String payload = new Gson()
                .toJson(
                    Map.of(
                        "aud",
                        "https://oauth2.googleapis.com/token",
                        "iat",
                        iat,
                        "exp",
                        exp,
                        "iss",
                        serviceAccount,
                        "scope",
                        scope,
                        "sub",
                        delegate
                    )
                );

            SignJwtRequest signJwtRequest = SignJwtRequest.newBuilder()
                .setName("projects/-/serviceAccounts/" + serviceAccount)
                .setPayload(payload)
                .build();

            String assertion = iamCredentialsClient
                .signJwt(signJwtRequest)
                .getSignedJwt();

            String body = URLEncodedUtils.format(
                List.of(
                    new BasicNameValuePair("assertion", assertion),
                    new BasicNameValuePair(
                        "grant_type",
                        "urn:ietf:params:oauth:grant-type:jwt-bearer"
                    )
                ),
                "utf-8"
            );

            HttpRequest httpRequest = new NetHttpTransport()
                .createRequestFactory()
                .buildPostRequest(
                    new GenericUrl("https://oauth2.googleapis.com/token"),
                    ByteArrayContent.fromString(
                        "application/x-www-form-urlencoded",
                        body
                    )
                );

            JsonObject json = JsonParser.parseString(
                httpRequest.execute().parseAsString()
            ).getAsJsonObject();

            return new com.google.auth.oauth2.AccessToken(
                json.get("access_token").getAsString(),
                Date.from(
                    Instant.now()
                        .plusSeconds(json.get("expires_in").getAsLong())
                )
            );
        } catch (IOException e) {
            getLogger().error(e.getMessage(), e);

            return null;
        }
    }
}
