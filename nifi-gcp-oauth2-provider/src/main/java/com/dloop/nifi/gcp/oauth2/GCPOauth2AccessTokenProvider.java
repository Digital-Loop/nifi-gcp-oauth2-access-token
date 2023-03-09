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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

import com.google.auth.oauth2.GoogleCredentials;

@Tags({ "gcp", "oauth2", "provider", "authorization", "access token", "http" })
@CapabilityDescription("Provides OAuth 2.0 access tokens for Google APIs.")
@SeeAlso({OAuth2AccessTokenProvider.class, GCPCredentialsService.class})
public class GCPOauth2AccessTokenProvider extends AbstractControllerService implements OAuth2AccessTokenProvider {
    public static final PropertyDescriptor SCOPE = new PropertyDescriptor.Builder()
            .name("scope")
            .displayName("Scope")
            .description(
                    "Space-delimited, case-sensitive list of scopes of the access request (as per the OAuth 2.0 specification)")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor DELEGATE = new PropertyDescriptor.Builder()
            .name("delegate")
            .displayName("Delegate")
            .description(
                    "If the credentials support domain-wide delegation, creates a copy of the identity so that it * impersonates the specified user; otherwise, returns the same instance.")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    private static final List<PropertyDescriptor> properties;

    static {
        final List<PropertyDescriptor> props = new ArrayList<>();
        props.add(GoogleUtils.GCP_CREDENTIALS_PROVIDER_SERVICE);
        props.add(SCOPE);
        props.add(DELEGATE);
        properties = Collections.unmodifiableList(props);
    }

    private volatile GoogleCredentials googleCredentials;
    private volatile String[] scopes;
    private volatile String delegate;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws InitializationException {
        // Get GCP credentials service
        GCPCredentialsService gcpCredentialsService = context.getProperty(GoogleUtils.GCP_CREDENTIALS_PROVIDER_SERVICE)
                .asControllerService(GCPCredentialsService.class);

        // Get GCP credentials
        googleCredentials = gcpCredentialsService.getGoogleCredentials();

        // Apply required scope(s)
        String scope = context.getProperty(SCOPE).getValue();
        if (scope != null) {
            scopes = scope.split("\\s+");
            if (scopes.length > 0) {
                getLogger().debug("dloop-scope: " + String.join(" ", scopes));
                googleCredentials = googleCredentials.createScoped(scopes);
            }
        }

        // Impersonate another account if specified
        delegate = context.getProperty(DELEGATE).getValue();
        if (delegate != null && !delegate.isBlank()) {
            getLogger().debug("dloop-delegate: " + delegate);
            googleCredentials = googleCredentials.createDelegated(delegate);
        }
    }

    @Override
    public AccessToken getAccessDetails() {
        // Refresh access token if expired
        try {
            googleCredentials.refreshIfExpired();
        } catch (IOException e) {
            getLogger().error(e.getMessage());
            return null;
        }

        String accessToken = googleCredentials.getAccessToken().getTokenValue();
        String tokenType = googleCredentials.getAuthenticationType();
        Long expiresIn = googleCredentials.getAccessToken().getExpirationTime().getTime() - System.currentTimeMillis();

        // Return access token
        return new AccessToken(
                accessToken,
                null,
                tokenType,
                expiresIn,
                String.join(" ", scopes));
    }
}
