#
# This script is used to run the Document Management API locally, to interact with
# existing PostgreSQL, localstack and hmpps-auth containers.
#
# It runs with a combination of properties from the default spring profile (in application.yaml) and supplemented
# with the -local profile (from application-local.yml). The latter overrides some of the defaults.
#
# The environment variables here will also override values supplied in spring profile properties, specifically
# around removing the SSL connection to the database and setting the DB properties, SERVER_PORT and client credentials
# to match those used in the docker-compose files.
#

# Provide the DB connection details to local container-hosted Postgresql DB
# Match with the credentials set in docker-compose.yml
export DB_SERVER=localhost:5433
export DB_NAME=document-management
export DB_USER=document-management
export DB_PASS=document-management
export DB_SSL_MODE=prefer

# Provide URLs to other dependent services. Dev services used here (can be local if you set uo the dependent services locally)
export HMPPS_AUTH_URL=https://sign-in-dev.hmpps.service.justice.gov.uk/auth

# Run the application with stdout and local profiles active
SPRING_PROFILES_ACTIVE=stdout,local ./gradlew bootRun

# End
