# HMPPS Document Management API
[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=flat&logo=github&label=MoJ%20Compliant&query=%24.result&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fapi%2Fv1%2Fcompliant_public_repositories%2Fhmpps-document-management-api)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/public-github-repositories.html#hmpps-document-management-api "Link to report")
[![CircleCI](https://circleci.com/gh/ministryofjustice/hmpps-document-management-api/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/hmpps-document-management-api)
[![codecov](https://codecov.io/github/ministryofjustice/hmpps-document-management-api/branch/main/graph/badge.svg)](https://codecov.io/github/ministryofjustice/hmpps-document-management-api)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/hmpps-document-management-api/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/hmpps-document-management-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://document-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

A shared electronic document management API.

# Running the service locally using run-local.sh
This will run the service locally. It starts the database and localstack containers then start the service via a bash script.
It connects to the dev version of hmpps-auth.

Run the following commands from the root directory of the project:

1. docker compose -f docker-compose-local.yml pull
2. docker compose -f docker-compose-local.yml up --no-start
3. docker compose -f docker-compose-local.yml start db localstack
4. ./run-local.sh

# Running tests locally
The tests use test containers to spin up dependent services (clamAV, postgres, localstack) so make sure Docker is running.
There is also an integration test for uploading a "file infected with a virus" The file can be found src/test/resources/test_data/eicar.txt. 
Windows defender will delete this file so it must be excluded from the scan. Open Windows defender and add the following exclusions:

1. file: src/test/resources/test_data/eicar.txt
2. file: build/resources/test_data/eicar.txt
3. folder: build/tmp

This will ensure the file is not deleted on the file system and during the test too.

To run the test execute the following command:

```shell
./gradlew check
```

# Load testing

**IMPORTANT:** Inform the cloud platform team before running load tests against any environment. 

1. Install JMeter using brew: `brew install jmeter`
2. Open the JMeter GUI: `jmeter`
3. Use the plugin manager (Options -> Plugins Manager or the icon on the top right) to install the [Custom JMeter Functions](https://jmeter-plugins.org/wiki/Functions/) plugin
4. Close JMeter and run the following command to open the test plan and specify required properties:

```
JVM_ARGS="-Xms1024m -Xmx1024m" jmeter -t load-test.jmx -Jprotocol='http' -Jbase_server_name='localhost' -Jbase_port='8080' -Jauth_server_name='<auth_server_name>' -Jclient_id='<client_id>' -Jclient_secret='<client_secret>'
```

## Running the load tests from the command line

```
rm -rf load-test-* \
&& JVM_ARGS="-Xms1024m -Xmx1024m" hmpps-document-management-api % jmeter -n -t load-test.jmx -l load-test-results.jtl -e -o load-test-results -Jprotocol='http' -Jbase_server_name='localhost' -Jbase_port='8080' -Jauth_server_name='<auth_server_name>' -Jclient_id='<client_id>' -Jclient_secret='<client_secret>' \
&& open load-test-results/index.html
```