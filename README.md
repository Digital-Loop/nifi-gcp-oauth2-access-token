# GCP Oauth2 Access Token Provider

Fortunately Apache NiFi already ships with a couple of processors which provide connections to Google APIs. But since there are dozens of Google APIs and they are also subject to changes from time to time, it's to expect that NiFi cannot offer a specialized processor for all of them out of the box. In our case we were missing a processor for the APIs of [Search Console](https://developers.google.com/webmaster-tools/v1/api_reference_index) and [Business Profile](https://developers.google.com/my-business/ref_overview).

This is why we created this custom controller service which adds an alternative oauth2 access token provider besides the [StandardOauth2AccessTokenProvider](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-oauth2-provider-nar/1.20.0/org.apache.nifi.oauth2.StandardOauth2AccessTokenProvider/index.html). It provides the access token generated by the [GCPCredentialsControllerService](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-gcp-nar/1.19.1/org.apache.nifi.processors.gcp.credentials.service.GCPCredentialsControllerService/index.html) service for the [InvokeHTTP](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.17.0/org.apache.nifi.processors.standard.InvokeHTTP/index.html) processor. This way it can be used to generate requests towards any Google API with https support.

## Installation
1. Download the latest .nar file from the [release page](https://github.com/Digital-Loop/nifi-gcp-oauth2-access-token/releases).
2. Copy it to the `lib` directory of your Apache NiFi deployment.

## Manual Build
```
mvn clean install
cp nifi-gcp-oauth2-provider-nar/target/nifi-gcp-oauth2-provider-nar-1.0.0.nar $NIFI_HOME/lib
```

## Usage
1. Create and configure a [GCPCredentialsControllerService](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-gcp-nar/1.19.1/org.apache.nifi.processors.gcp.credentials.service.GCPCredentialsControllerService/index.html) controller service.
2. Create a GCPOauth2AccessTokenProvider controller service (provided by this module).
3. You can add one or multiple [scopes](https://developers.google.com/identity/protocols/oauth2/scopes) if required (the required scopes are mentioned in the corresponding Google API documentation).
4. You can delegate to another account if you want to [impersonate](https://cloud.google.com/iam/docs/service-account-overview#impersonation) it.
5. Create an [InvokeHTTP](https://nifi.apache.org/docs/nifi-docs/components/org.apache.nifi/nifi-standard-nar/1.17.0/org.apache.nifi.processors.standard.InvokeHTTP/index.html) processor and select your GCPOauth2AccessTokenProvider for the field "Request OAuth2 Access Token Provider".
6. Configure http method, http url and headers as explained by the corresponding API documentation (the flow file content is used as the request body).

## Examples
1. [Search Console API](./examples/google-search-console/README.md)
2. [Business Profile API](./examples/google-business-profile/README.md)
