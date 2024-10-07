# Connect Apache NiFi to Google Business Profile API

This example runs a [Method: accounts.list](https://developers.google.com/my-business/reference/accountmanagement/rest/v1/accounts/list) request from the Google Business Profile API.

## Requirements

1. Access to a Google (service) account through Application Default Credentials, Compute Engine Credentials or Service Account JSON.
2. The GCP project that corresponds to the account has to enable the Google Business Profile API (requires filling out a form and waiting for Google's acceptance).
3. Access to a Google Business Profile with that account (not necessary for this test call).
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
