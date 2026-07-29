# 🐳 Docker Compose Setup for mod-notes

Local development environment for mod-notes using Docker Compose.

## 📋 Prerequisites

- Docker and Docker Compose V2+
- Java 21+ (for local development mode)
- Maven 3.8+ (for building the module)

## 🏗️ Architecture

Two compose files provide flexible development workflows:

- **`infra-docker-compose.yml`**: Infrastructure services only (PostgreSQL, pgAdmin, WireMock)
- **`app-docker-compose.yml`**: Full stack including the module (uses `include` to incorporate infra services)

## ⚙️ Configuration

Configuration is managed via the `.env` file in this directory.

### Key Environment Variables

| Variable                  | Description                   | Default                |
|---------------------------|-------------------------------|------------------------|
| `MODULE_REPLICAS`         | Number of module instances    | `1`                    |
| `MODULE_PORT`             | Module host port              | `8081`                 |
| `DEBUG_PORT`              | Remote debugging port         | `5005`                 |
| `DB_HOST`                 | PostgreSQL hostname           | `postgres`             |
| `DB_PORT`                 | PostgreSQL port               | `5432`                 |
| `DB_DATABASE`             | Database name                 | `modules`        |
| `DB_USERNAME`             | Database user                 | `folio_admin`          |
| `DB_PASSWORD`             | Database password             | `folio_admin`          |
| `PGADMIN_PORT`            | PgAdmin port                  | `5050`                 |
| `WIREMOCK_PORT`           | WireMock (Okapi mock) port    | `9130`                 |
| `OKAPI_URL`               | Okapi URL for the module      | `http://wiremock:8080` |
| `ENV`                     | Kafka topic prefix            | `folio`                |
| `KAFKA_HOST`              | Broker host (in-cluster)      | `kafka`                |
| `KAFKA_PORT`              | Broker port (in-cluster)      | `9093`                 |
| `KAFKA_EXTERNAL_PORT`     | Broker port from host/IDE     | `9092`                 |
| `KAFKA_UI_PORT`           | Kafka UI web port             | `8090`                 |

## 🚀 Services

### PostgreSQL
- **Purpose**: Primary database for module data
- **Version**: PostgreSQL 16 Alpine
- **Access**: localhost:5432 (configurable via `DB_PORT`)
- **Credentials**: See `DB_USERNAME` and `DB_PASSWORD` in `.env`
- **Database**: See `DB_DATABASE` in `.env`

### pgAdmin
- **Purpose**: Database administration interface
- **Access**: http://localhost:5050 (configurable via `PGADMIN_PORT`)
- **Login**: Use `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` from `.env`

### WireMock
- **Purpose**: Mock Okapi and other FOLIO modules for local testing
- **Access**: http://localhost:9130 (configurable via `WIREMOCK_PORT`)
- **Mappings**: Located in `src/test/resources/mappings`

### Kafka
- **Purpose**: Message broker the module publishes Note domain events to
- **Image**: `apache/kafka-native` (KRaft mode, no Zookeeper)
- **Access (host/IDE)**: `localhost:9092` (configurable via `KAFKA_EXTERNAL_PORT`)
- **Access (in-cluster)**: `kafka:9093`
- **Topics**: `{ENV}.{tenant}.notes.note` — e.g. `folio.diku.notes.note`. The module creates the topic when a tenant is enabled (via the `/_/tenant` API); the broker also auto-creates it on first publish.

### Kafka UI
- **Purpose**: Web UI to browse topics and inspect published event messages
- **Access**: http://localhost:8090 (configurable via `KAFKA_UI_PORT`)

## 📖 Usage

> **Note**: All commands in this guide assume you are in the `docker/` directory. If you're at the project root, run `cd docker` first.

### Starting the Environment

```bash
# Build the module first
mvn -f ../pom.xml clean package -DskipTests

# Start all services (infrastructure + module)
docker compose -f app-docker-compose.yml up -d
```

```bash
# Start only infrastructure services (for local development)
docker compose -f infra-docker-compose.yml up -d
```

```bash
# Start with build (if module code changed)
docker compose -f app-docker-compose.yml up -d --build
```

```bash
# Start specific service
docker compose -f infra-docker-compose.yml up -d postgres
```

### Stopping the Environment

```bash
# Stop all services
docker compose -f app-docker-compose.yml down
```

```bash
# Stop infra services only
docker compose -f infra-docker-compose.yml down
```

```bash
# Stop and remove volumes (clean slate)
docker compose -f app-docker-compose.yml down -v
```

### Viewing Logs

```bash
# All services
docker compose -f app-docker-compose.yml logs
```

```bash
# Specific service
docker compose -f app-docker-compose.yml logs mod-notes
```

```bash
# Follow logs in real-time
docker compose -f app-docker-compose.yml logs -f mod-notes
```

```bash
# Last 100 lines
docker compose -f app-docker-compose.yml logs --tail=100 mod-notes
```

### Scaling the Module

The module is configured with resource limits and deployment policies for production-like scaling:

- **CPU Limits**: 1.0 CPU (max), 0.5 CPU (reserved)
- **Memory Limits**: 512M (max), 256M (reserved)
- **Restart Policy**: Automatic restart on failure
- **Update Strategy**: Rolling updates with 1 instance at a time, 10s delay

```bash
# Scale to 3 instances
docker compose -f app-docker-compose.yml up -d --scale mod-notes=3
```

```bash
# Or modify MODULE_REPLICAS in .env and restart
echo "MODULE_REPLICAS=3" >> .env
docker compose -f app-docker-compose.yml up -d
```

### Cleanup and Reset

```bash
# Complete cleanup (stops containers, removes volumes)
docker compose -f app-docker-compose.yml down -v
```

```bash
# Remove all Docker resources
docker compose -f app-docker-compose.yml down -v
docker volume prune -f
docker network prune -f
```

## 🔧 Development Workflows

### Workflow 1: Full Docker Stack
Run everything in Docker, including the module.

```bash
# Build the module
mvn -f ../pom.xml clean package -DskipTests

# Start all services
docker compose -f app-docker-compose.yml up -d

# View logs
docker compose -f app-docker-compose.yml logs -f mod-notes
```

**Use Case**: Testing the full deployment, simulating production environment, scaling tests.

### Workflow 2: Infrastructure Only + IDE
Run infrastructure in Docker, develop the module in your IDE.

```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d

# Run module from IDE or command line
mvn -f ../pom.xml spring-boot:run
```

**Use Case**: Active development with hot reload, debugging in IDE, faster iteration cycles.

### Workflow 3: Spring Boot Docker Compose Integration
Let Spring Boot manage Docker Compose automatically (recommended for rapid development).

```bash
# Run with dev profile (starts infrastructure automatically)
mvn -f ../pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile is configured to:
- Start services from `docker/infra-docker-compose.yml` automatically
- Connect to services via localhost ports (PostgreSQL: 5432, WireMock: 9130)
- Keep containers running after the application stops for faster subsequent startups

**Use Case**: Quickest way to start development — no manual Docker commands needed.

### Workflow 4: Spring Boot DevTools
For rapid development with automatic restart on code changes.

```bash
# Start infrastructure
docker compose -f infra-docker-compose.yml up -d

# Run with devtools (automatic restart on code changes)
mvn -f ../pom.xml spring-boot:run

# Make code changes — application will automatically restart
```

**Use Case**: Continuous development with automatic reload, live code updates, rapid feedback loop.

## 🛠️ Common Tasks

### Building the Module

```bash
# Clean build (skip tests)
mvn -f ../pom.xml clean package -DskipTests
```

```bash
# Build with tests
mvn -f ../pom.xml clean package
```

### Tenant Setup

After starting the module, register a tenant by calling the `/_/tenant` API:

```bash
curl -X POST http://localhost:8081/_/tenant \
  -H "Content-Type: application/json" \
  -H "X-Okapi-Tenant: diku" \
  -H "X-Okapi-Url: http://localhost:9130" \
  -d '{"module_to": "mod-notes-8.0.0-SNAPSHOT", "parameters": [{"key": "loadReference", "value": "true"}]}'
```

> **Note**: Adjust `module_to` version to match the currently running module version.

### Accessing Services

```bash
# PostgreSQL CLI
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d modules
```

```bash
# View database tables
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d modules -c "\dt"
```

```bash
# Check PostgreSQL health
docker compose -f infra-docker-compose.yml exec postgres pg_isready -U folio_admin
```

### Rebuilding the Module

```bash
# Rebuild and restart the module
mvn -f ../pom.xml clean package -DskipTests
docker compose -f app-docker-compose.yml up -d --build mod-notes
```

```bash
# Force rebuild without cache
docker compose -f app-docker-compose.yml build --no-cache mod-notes
docker compose -f app-docker-compose.yml up -d mod-notes
```

## 🐛 Troubleshooting

### Port Conflicts

If you encounter port conflicts, modify the ports in `.env`:

```bash
# Example: Change module port to 8082
MODULE_PORT=8082
```

Then restart the services:

```bash
docker compose -f app-docker-compose.yml up -d
```

### Container Health Issues

```bash
# Check container status
docker compose -f app-docker-compose.yml ps

# Check specific container logs
docker compose -f app-docker-compose.yml logs mod-notes

# Restart a specific service
docker compose -f app-docker-compose.yml restart mod-notes
```

### Database Connection Issues

```bash
# Verify PostgreSQL is running
docker compose -f infra-docker-compose.yml ps postgres

# Check PostgreSQL logs
docker compose -f infra-docker-compose.yml logs postgres

# Test database connection
docker compose -f infra-docker-compose.yml exec postgres psql -U folio_admin -d modules -c "SELECT 1"
```

**`FATAL: database "modules" does not exist`** — PostgreSQL only creates the database defined in `POSTGRES_DB` on the very first startup with an empty data directory. If the `postgres-data` volume already existed from a previous run (with different settings), the init is skipped. Fix by recreating the volume:

```bash
docker compose -f infra-docker-compose.yml stop postgres pgadmin
docker compose -f infra-docker-compose.yml rm -f postgres pgadmin
docker volume rm folio-mod-notes_postgres-data
docker compose -f infra-docker-compose.yml up -d postgres pgadmin
```

### Clean Start

If you need to completely reset the environment:

```bash
# Stop and remove everything
docker compose -f app-docker-compose.yml down -v

# Remove any orphaned containers
docker container prune -f

# Remove unused networks
docker network prune -f

# Start fresh
mvn -f ../pom.xml clean package -DskipTests
docker compose -f app-docker-compose.yml up -d --build
```

## 📚 Additional Resources

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot Docker Compose Support](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.docker-compose)
- [mod-notes Documentation](../README.md)

## 💡 Tips

- Use **Workflow 3** (Spring Boot Docker Compose) for the fastest development experience
- Keep infrastructure running between development sessions to save startup time
- Use **Workflow 1** (Full Docker Stack) when testing deployment or scaling scenarios
- Use `docker compose -f infra-docker-compose.yml logs -f` to monitor all infrastructure services
- pgAdmin provides a helpful web interface for inspecting the database at http://localhost:5050

