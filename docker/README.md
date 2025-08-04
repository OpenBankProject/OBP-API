## OBP API – Docker & Docker Compose Setup

This project uses Docker and Docker Compose to run the **OBP API** service with Maven and Jetty.

- Java 17 with reflection workaround  
- Connects to your local Postgres using `host.docker.internal`  
- Supports separate dev & prod setups

---

## How to use

> **Make sure you have Docker and Docker Compose installed.**

### Set up the database connection

Edit your `default.properties` (or similar config file):

```properties
db.url=jdbc:postgresql://host.docker.internal:5432/YOUR_DB_NAME?user=YOUR_DB_USER&password=YOUR_DB_PASSWORD
````

> Use `host.docker.internal` so the container can reach your local database.

---

### Build & run (production mode)

Build the Docker image and run the container:

```bash
docker-compose up --build
```

The service will be available at [http://localhost:8080](http://localhost:8080).

---

## Development tips

For live code updates without rebuilding:

* Use the provided `docker-compose.override.yml` which mounts only:

  ```yaml
  volumes:
    - ../obp-api:/app/obp-api
    - ../obp-commons:/app/obp-commons
  ```
* This keeps other built files (like `entrypoint.sh`) intact.
* Avoid mounting the full `../:/app` because it overwrites the built image.

---

## Useful commands

Rebuild the image and restart:

```bash
docker-compose up --build
```

Stop the container:

```bash
docker-compose down
```

---

## Before first run

Make sure your entrypoint script is executable:

```bash
chmod +x docker/entrypoint.sh
```

---

## Notes

* The container uses `MAVEN_OPTS` to pass JVM `--add-opens` flags needed by Lift.
* In production, avoid volume mounts for better performance and consistency.

---

That’s it — now you can run:

```bash
docker-compose up --build
```

and start coding!

```