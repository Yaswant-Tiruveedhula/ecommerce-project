CREATE DATABASE ecommerce
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'ecommerce_app'@'localhost'
  IDENTIFIED BY 'replace-with-your-own-password';

GRANT ALL PRIVILEGES ON ecommerce.* TO 'ecommerce_app'@'localhost';# Database

The authoritative, versioned schema is maintained by Flyway in
`backend/src/main/resources/db/migration/`. Do not edit an already-applied
migration; add a new numbered migration instead.

The initial baseline is `V1__create_initial_schema.sql`. Flyway runs this
migration when the Spring Boot application starts. Hibernate is configured to
validate, not recreate, the schema. Add later changes in new numbered
migrations; never edit a migration already applied to a shared database.

Use MySQL 8.0+ with `utf8mb4`. Never commit local database credentials. The
migration seeds the CUSTOMER and ADMIN role records; an ADMIN account is only
created by the environment-configured bootstrap process. Public registration
cannot self-assign the ADMIN role. For local setup and MySQL Workbench access,
see the root README.
