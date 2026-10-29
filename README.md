# Jck_GS_Pasportization

**Extracts, parses, and stores data about gas station attributes from a web API into an MS SQL staging environment for SAP BW/4HANA.**

## 📋 Description

The **Jck_GS_Pasportization** application automates the extraction, parsing, and storage of gas station complex attributes — including coordinates, addresses, fuel brands, and active service equipment statuses. The application retrieves raw JSON data from a remote web service, transforms the payloads defensively, and writes the records to a target MS SQL database. This environment serves as a high-performance intermediate staging area (PSA) for subsequent ETL loading into **SAP BW/4HANA**.

## 🚀 Features

* **Dependency Injection Architecture:** Powered by **Google Guice**. Promotes loose coupling across the network, serialization, and persistence layers by replacing anti-pattern static singletons and implicit state lifecycle bottlenecks with declarative, type-safe constructor and provider-based injection.
* **Resilient Network Infrastructure:** Utilizes a highly optimized **Apache HttpClient** infrastructure backed by a managed connection pool (`PoolingHttpClientConnectionManager`), establishing a scalable, high-throughput foundation engineered for high-concurrency enterprise web service interactions.
* **Automated Fault-Tolerance & Retry Policy:** Embeds an enterprise-grade `DefaultHttpRequestRetryStrategy` that intercepts transient network failures and execution glitches. It automatically triggers up to 5 granular retries with fixed-interval backoffs (30 seconds), preventing transient environment disruptions from bubbling up to business workflows.
* **State-Aware Lazy Initialization:** Defers the final instantiation of the HTTP client until the exact moment of the first remote invocation. This architectural constraint guarantees that the underlying `SSLContext` is established only *after* the local keystore has been fully synchronized and updated with the latest remote certificate chains, preventing the client from utilizing stale or invalid TLS security parameters.
* **Dynamic TLS Certificate Lifecycle Management:** Integrates an advanced custom `KeyStoreManager` and certificate updater that programmatically intercepts remote certificate chains during the TLS handshake, transparently updates the local JVM `cacerts` file, and hot-swaps the memory-resident `SSLContext` dynamically without requiring application restarts. **This subsystem routes detailed debugging metadata (`DEBUG` level traces) directly into the primary application log matrix, ensuring explicit observability and deterministic validation of runtime certificate updates.**
* **Fault-Tolerant JSON Deserialization:** Features a production-grade `GSP_DataDeserializer` that implements robust data normalization for upstream API payloads. The component guarantees high application availability and runtime infrastructure stability by automatically normalizing missing or null JSON elements into standard default types, resolving malformed alphanumeric identifiers via pre-compiled regular expressions, and parsing heterogeneous coordinate data formats without triggering validation exceptions.
* **Stream-Based Data Transformation:** Leverages Java Streams to orchestrate clean decoupling between layers, mapping transient API Data Transfer Objects (DTOs) into persistent Hibernate domain entities on the fly. This pipeline integrates stateful filtering to seamlessly eliminate duplicate business keys from the incoming payload array before they hit the database layer.
* **High-Performance Staging Persistence:** Executes optimized, transaction-aware batch operations (`merge`, `flush`, `clear`) directly through the DAO layer to minimize round-trips to the database and maximize throughput.
* **Pluggable Multi-Schema Authentication:** Provides a unified, metadata-driven security layer supporting `Basic`, `Bearer Token`, or anonymous (`none`) API authorization schemes, dynamically resolved at runtime directly from external configuration profiles.

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
* **Classpath Decoupling:** The compiled application artifact excludes third-party binaries from its own internal structure, dynamically linking against the shared directory at runtime via classpath definitions inside the manifest.
  
## 📂 Project Structure & Configuration

The project follows a clean, layered architecture designed for deterministic batch processing:

### ⚙️ Configuration & Security Management
> ⚠️ **Security Note:** Production configuration files containing plain-text credentials (`properties.yaml` and `hibernate.cfg.xml`) are strictly tracked by `.gitignore` and excluded from the repository. Instead, public safe templates (`.dist`) are provided.

* `config/properties.yaml.dist` — A safe template for API authorization modes, targets, and environment settings. Must be copied and renamed to `properties.yaml` inside the root `config/` directory.
* `config/hibernate.cfg.xml.dist` — A safe template for MS SQL connection strings and Hibernate parameters. Must be copied and renamed to `hibernate.cfg.xml` inside the root `config/` directory.
* **`gsp.config.ConfigPathResolver`** — A centralized path resolution utility. It dynamically detects the execution context (such as running inside an IDE environment versus a standalone packaged JAR) to establish correct absolute paths. It houses the core orchestration logic for validating the structural integrity of configuration resources and emitting descriptive lifecycle failures if environment constraints are violated.
* `gsp.config.GSPApiConfig` — A DTO class for deserializing API connection parameters from the YAML configuration file. Features `@JsonRootName` mapping to transparently support nested enterprise configuration profiles.
* **`gsp.config.YAMLConfigLoader`** — A robust utility that parses external YAML configuration data using Jackson's `YAMLFactory`. It leverages the centralized path resolver to operate with strict **Fail-Fast** semantics, throwing fatal runtime exceptions to abort execution if configuration nodes are missing or malformed.
* `gsp.util.LogInitializer` — A utility class responsible for dynamic runtime initialization of the `Log4j2/SLF4J` logging subsystem and `P6Spy` SQL profiler. It automatically evaluates the execution path to enforce deterministic cross-platform log routing using the `${sys:log.dir}` property.
* `src/main/resources/Log4j2.xml` — The central configuration matrix for the Log4j2 subsystem. It establishes isolated internal file routing for persistent infrastructure components (Hibernate metrics, P6Spy SQL profiling traces) and configures explicit debug-level logging boundaries for the external TLS certificate engine to enforce complete runtime handshake transparency.

### 🌐 Dependency Injection & Network Transport Layers

* `gsp.MainLauncher` — The definitive bootstrapper and application entry point. It orchestrates early infrastructure initialization by bootstrapping the logging subsystem, builds the global Google Guice dependency injection container, invokes the main orchestration pipeline, and explicitly manages process exit codes (`0` or `1`) for seamless integration with external OS schedulers and enterprise cron triggers.
* `gsp.GSPAppModule` — The core Dependency Injection container powered by Google Guice. It centrally configures and exposes high-level infrastructure singletons including application configuration profiles, an optimized Hibernate `SessionFactory` pool managed with safe programmatic teardown mechanics, a customized Jackson `ObjectMapper`, and a pre-configured `HttpClientBuilder` tailored with resilient transport defaults.
* `gsp.GSP_App` — Main application orchestrator coordinating the end-to-end data pipeline. It orchestrates the flow of data by triggering the network fetch, managing target deserialization types, executing deterministic in-memory business-key deduplication via functional pipelines, and delegating clean persistence units to the database layer.
* `gsp.network.GSP_HttpClient` — A high-performance network transport service encapsulating a managed `CloseableHttpClient` connection pool. It abstracts low-level network operations from business logic, leveraging a thread-safe lazy-initialization pattern to defer client instantiation until upstream TLS certificates are verified, and dynamically generates request-scoped authorization headers (`Basic`, `Bearer`, or `none`).

### 💾 Domain Model & Persistence Layer

* `gsp.hibernate.GSP_DAO` — Data Access Object (DAO) utility managed as a Guice singleton. It implements transactional multi-record batch processing using periodic cache synchronization primitives (`merge`, `flush`, `clear`) to maximize throughput into MS SQL while enforcing flat deterministic memory limits.
* `gsp.model.GSP_Entity` — A Hibernate entity class representing the target `GS_Passportization` staging database table, explicitly structured using standard JPA `@Table` and `@Column` mapping metadata.
* `gsp.model.GSP_Data` — A DTO class representing a gas station complex object mapped directly from the raw top-level JSON response arrays.
* `gsp.jackson_json.GSP_DataDeserializer` — A fault-tolerant Jackson deserializer implementing safe data-extraction strategies for upstream API payloads. It ensures strict mapping of missing, null, or malformed JSON nodes to predictable Java structures and standardizes inconsistent numeric data formats.

### 🔄 Orchestration & Lifecycle Bootstrapping

* `gsp.GSP_App` — The primary high-level application orchestrator. Coordinates the data pipeline linearly (`Download payload` -> `Parse DTO list` -> `Clean duplicate keys via Streams` -> `Database batch flush`) while keeping components decoupled.
* `gsp.MainLauncher` — The definitive main application bootstrapper. It establishes the sequential application startup timeline: sets up diagnostic logging contexts first, boots up the Guice container second, runs the orchestration pipeline third, and communicates final state to the OS/scheduler by explicitly calling `System.exit()` codes.

### 📊 Generated Artifacts

* `logs/` — Created automatically in the application root directory during runtime. Contains standalone execution logs (`commons.log`) and database interaction telemetry (`p6spy.log`).

---

## 🛠️ Prerequisites

* **Java:** JDK 17 or higher
* **Database:** MS SQL Server
* **Build System:** Apache Maven 3.8+

## 💻 Deployment & Running

### 1. Prepare Environment Configurations
Since production credentials are safe from tracking via `.gitignore`, initialize your active operational profiles from the provided templates at the project root:
* Copy `config/properties.yaml.dist` to a new file named `properties.yaml` inside the root `config/` directory and fill in your target API configurations.
* Copy `config/hibernate.cfg.xml.dist` to a new file named `hibernate.cfg.xml` inside the root `config/` directory and update the MS SQL connection credentials.

### 2. Build the Application
Run the standard Maven package phase to compile the application and collect shared dependencies:
```bash
mvn clean package
```
This compilation setup automatically executes the following:
1. Compiles the Java source files under JDK 17 specifications.
2. Triggers `maven-dependency-plugin` to isolate all required third-party binaries into the `../dependencies/` shared directory.
3. Generates the final application artifact inside the `target/` directory with a pre-configured manifest `Class-Path` referencing the parent directory.

### 3. Run the Application
Copy the compiled JAR file from the `target/` directory to the project root folder. Running a production JAR directly inside `target/` is an anti-pattern as it risks data loss during build cleanups. 

Run the application using the Java CLI. Thanks to the automated manifest configuration in `pom.xml`, the classpath linking to the shared `dependencies/` folder is resolved out of the box, while the logging engine dynamically routes output paths relative to your active execution scope.

By default, the application initializes and writes all runtime traces into a local `logs/` directory created in the root folder of execution. This path can be dynamically overridden at runtime by supplying the `-Dlog.dir` system property flag to the JVM configuration footprint:

```bash
# 1. Copy the production artifact to the project root path
cp target/Jck_GSPassportization-0.0.1-SNAPSHOT.jar .

# 2. Execute utilizing the default local logs directory (./logs/)
java -jar Jck_GSPassportization-0.0.1-SNAPSHOT.jar

# 3. Alternative: Execute with explicit log directory overriding via JVM System Properties
java -Dlog.dir=/var/log/gsp_applications -jar Jck_GSPassportization-0.0.1-SNAPSHOT.jar
```


