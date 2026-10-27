# Jck_GS_Pasportization

**Extracts, parses, and stores data about gas station attributes from a web API into an MS SQL staging environment for SAP BW/4HANA.**

## 📋 Description

The **Jck_GS_Pasportization** application automates the extraction, parsing, and storage of gas station complex attributes - including coordinates, addresses, fuel brands, and active service equipment statuses. The application retrieves raw JSON data from a remote web service, transforms the payloads defensively, and writes the records to a target MS SQL database. This environment serves as a high-performance intermediate staging area (PSA) for subsequent ETL loading into **SAP BW/4HANA**.

## 🚀 Features

* **Dependency Injection Architecture:** Powered by **Google Guice 7.0**. Promotes loose coupling by replacing error-prone static singletons and implicit initializations with clean, declarative constructor injection.
* **Resilient Network Architecture:** Utilizes **Apache HttpClient 5** backed by a connection pool (`PoolingHttpClientConnectionManager`), providing a high-performance foundation for concurrent web service interactions.
* **Automated Fault-Tolerance:** Replaces imperative retry loops with a declarative `DefaultHttpRequestRetryStrategy` executing an exponential backoff algorithm (up to 5 attempts, starting at 30 seconds) to survive transient network failures.
* **On-The-Fly SSL Certificate Management:** Features an advanced custom `KeyStoreManager` that programmatically intercepts remote certificate chains during SSL handshakes, updates the local JVM `cacerts` file, and hot-swaps the memory-resident `SSLContext` dynamically without requiring application restarts.
* **Robust JSON Deserialization:** Configured via Jackson's `ObjectMapper` to gracefully ignore undeclared or unknown API properties (`FAIL_ON_UNKNOWN_PROPERTIES`), preventing runtime crashes during upstream schema updates.
* **Defensive Parsing & Stream Transformation:** Employs a custom `GSP_DataDeserializer` registered via a `SimpleModule` for type-safe mapping. Uses Java Streams to parse raw payload arrays into persistent Hibernate entities.
* **High-Performance Staging Persistence:** Executes optimized, transaction-aware batch operations (`merge`, `flush`, `clear`) directly through the DAO layer to minimize round-trips to the database and maximize throughput.
* **Pluggable Multi-Schema Authentication:** Provides out-of-the-box support for `basic`, `token`, or `none` API authorization schemes, dynamically driven by external configuration files.

## 🏗️ Shared Dependency Architecture

To eliminate library redundancy and optimize disk space usage across multiple integration applications, this project adopts a decoupled shared-dependency deployment model:

* **Centralized Dependencies:** All external runtime JAR libraries are compiled and isolated into a dedicated `dependencies` directory.
* **Directory Layout:** The `dependencies` folder is located **one level above** the application root directory (`Jck_GS_Pasportization`), serving as a common binary store for neighboring integration modules:

  ```text
  ├── shared-environment-root/
  │   ├── dependencies/             <-- Shared runtime external libraries (.jar)
  │   └── Jck_GS_Pasportization/    <-- This application root directory
  │       ├── src/
  │       ├── target/
  │       └── pom.xml
  ```
* **Classpath Decoupling:** The compiled application artifact excludes third-party binaries from its own internal structure, dynamically linking against the shared directory at runtime via classpath definitions.
  
## 📂 Project Structure & Configuration

The project follows a clean, layered architecture designed for deterministic batch processing:

### ⚙️ Configuration & Security Management
> ⚠️ **Security Note:** Production configuration files containing plain-text credentials (`properties.yaml` and `hibernate.cfg.xml`) are strictly tracked by `.gitignore` and excluded from the repository. Instead, public safe templates (`.dist`) are provided.

* `config/properties.yaml.dist` — A safe template for API authorization modes, targets, and environment settings. Must be copied and renamed to `properties.yaml` inside the root `config/` directory.
* `config/hibernate.cfg.xml.dist` — A safe template for MS SQL connection strings and Hibernate parameters. Must be copied and renamed to `hibernate.cfg.xml` inside the root `config/` directory.
* `gsp.config.GSPApiConfig` — A DTO class for deserializing API connection parameters from the YAML configuration file. Features `@JsonRootName` mapping to transparently support nested enterprise configuration profiles.
* `gsp.util.YAMLConfigLoader` — A robust utility that parses external YAML configuration data using Jackson's `YAMLFactory`. It operates with strict **Fail-Fast** semantics, throwing fatal runtime exceptions to abort execution if configuration nodes are missing or malformed.
* `gsp.util.LogInitializer` — A utility class responsible for dynamic runtime initialization of the `Log4j2/SLF4J` logging subsystem and `P6Spy` SQL profiler. It automatically evaluates the execution path (IDE context vs. standalone JAR) to enforce deterministic cross-platform log routing using the `${sys:log.dir}` property.

### 🌐 Dependency Injection & Network Transport Layers

* **`gsp.GSPAppModule`** — The foundational IoC core module extending Guice's `AbstractModule`. It isolates complex technical setups via declarative `@Provides` recipes, managing singletons for Jackson's `ObjectMapper`, the connection-pooled `CloseableHttpClient`, and Hibernate's `SessionFactory`.
* **`gsp.network.GSP_HttpClient`** — An encapsulated network transport service. It relies on the injected `CloseableHttpClient` pool and dynamically builds custom authorization headers (`Basic`, `Bearer`, or `none`) directly on the HTTP request structure right before execution, hiding network concerns from business workflows.

### 💾 Domain Model & Persistence Layer

* **`gsp.model.GSP_Entity`** — A Hibernate entity class representing the target `GS_Passportization` staging database table, explicitly structured using standard JPA `@Table` and `@Column` mapping metadata.
* **`gsp.model.GSP_Data`** — A DTO class representing a gas station complex object mapped directly from the raw top-level JSON response arrays.
* **`gsp.util.GSP_DataDeserializer`** — A custom Jackson deserializer designed for defensive extraction of API fields, safely mapping missing or empty JSON nodes into appropriate Java fields and handling numerical overflows.
* **`gsp.hibernate.GSP_DAO`** — A thread-safe data access object managing transaction-aware, high-performance database batch processing (`merge`, `flush`, `clear`). It flushes state in blocks of 50 records to prevent Hibernate's first-level cache from causing JVM memory leaks.

### 🔄 Orchestration & Lifecycle Bootstrapping

* **`gsp.GSP_App`** — The primary high-level application orchestrator. Coordinates the data pipeline linearly (`Download payload` -> `Parse DTO list` -> `Clean duplicate keys via Streams` -> `Database batch flash`) while keeping components decoupled.
* **`gsp.MainLauncher`** — The definitive main application bootstrapper. It establishes the sequential application startup timeline: sets up diagnostic logging contexts first, boots up the Guice container second, runs the orchestration pipeline third, and communicates final state to the OS/scheduler by explicitly calling `System.exit()` codes.

### 📊 Generated Artifacts

* **`logs/`** — Created automatically in the application root directory during runtime. Contains standalone execution logs (`commons.log`) and database interaction telemetry (`p6spy.log`).

---

## 🛠️ Prerequisites

* **Java:** JDK 17 or higher
* **Database:** MS SQL Server
* **Build System:** Apache Maven 3.8+

## 💻 Deployment & Running

### 1. Prepare Environment Configurations
Since production credentials are safe from tracking via `.gitignore`, initialize your active operational profiles from the provided templates at the project root:
* Copy `config/properties.yaml.dist` to a new file named `properties.yaml` inside the root `config/` directory, then update the target API endpoints and active authorization credentials.
* Copy `config/hibernate.cfg.xml.dist` to a new file named `hibernate.cfg.xml` inside the root `config/` directory, then populate it with your actual MS SQL database endpoint parameters.

### 2. Build the Application
Execute the Maven package phase. This compiles the source code, builds the decoupled core application JAR, and automatically triggers the `maven-dependency-plugin` to isolate external dependencies into the centralized external directory:

```bash
mvn clean package
```

### 3. Execute the Application
Copy the compiled JAR file from the `target/` directory to the project root folder. Running a production JAR directly inside `target/` is an anti-pattern as it risks data loss during build cleanups. 

Run the application natively using the Java CLI. Thanks to the automated manifest configuration in `pom.xml`, the classpath linking to the shared `dependencies/` folder is resolved out of the box, while the internal logging engine dynamically routes output paths relative to your active execution scope:

```bash
# Copy the artifact to the project root and execute it
cp target/Jck_GS_Passportization-0.0.1-SNAPSHOT.jar .
java -jar Jck_GS_Passportization-0.0.1-SNAPSHOT.jar
```
