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