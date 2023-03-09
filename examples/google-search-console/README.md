# Connect Apache NiFi to Google Search Console API

This example runs a [Sites: list](https://developers.google.com/webmaster-tools/v1/sites/list) request from the Google Search Console API.

## Requirements
1. Access to a Google (service) account through Application Default Credentials, Compute Engine Credentials or Service Account JSON.
2. The GCP project that corresponds to the account has to enable the Google Search Console API.
3. Access to a Search Console property (domain or url) with that account (otherwise the response will be empty).
4. This module must be [built](../../README.md#manual-build) and NiFi restarted.

## Use Template
1. [Upload example template](https://nifi.apache.org/docs/nifi-docs/html/user-guide.html#Import_Template).
2. Add example template ("Template" action in top navigation).

## Configure controller services
1. Click anywhere to focus the process group.
2. Open process group configuration.
3. Open "Controller Services" tab.
4. Configure "GCPCredentialsControllerService".
5. Open "Properties" tab.
6. Select authentication (Application Default Credentials, Compute Engine Credentials or Service Account JSON).
7. Enable "GCPCredentialsControllerService".
8. Enable "GCPOauth2AccessTokenProvider" (no configuration required).
9. Close process group configuration window.

## Test InvokeHTTP processor
1. Right click "InvokeHTTP" and select "Run Once" from menu.
2. When the processor has finished and the view has been refreshed it should say "Queued 1 (xxx bytes)" on the Connection.
3. Right click Connection and select "List Queue" from menu.
4. Download or view content (icons in last column).
