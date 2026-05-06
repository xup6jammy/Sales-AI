# MCP server — JDBC drivers

This directory holds the JDBC driver jars the MCP server loads at runtime. **Driver jars are not committed to this repository.** Download the one you need and drop it in here.

## SQLite (recommended for the demo)

The default `--db jdbc:sqlite:mcp-server/demo.db` and the bundled smoke tests all assume SQLite. Use **3.42.0.1** specifically — later versions (3.43+) require SLF4J on the classpath, which would break the "drop one jar in `lib/` and go" experience.

```powershell
# PowerShell
Invoke-WebRequest `
  -Uri 'https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.1/sqlite-jdbc-3.42.0.1.jar' `
  -OutFile 'mcp-server/lib/sqlite-jdbc-3.42.0.1.jar'
```

```bash
# bash
curl -L -o mcp-server/lib/sqlite-jdbc-3.42.0.1.jar \
  https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.1/sqlite-jdbc-3.42.0.1.jar
```

## MySQL

```bash
curl -L -o mcp-server/lib/mysql-connector-j-8.4.0.jar \
  https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar
```

JDBC URL: `jdbc:mysql://host:3306/sales`

## PostgreSQL

```bash
curl -L -o mcp-server/lib/postgresql-42.7.3.jar \
  https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar
```

JDBC URL: `jdbc:postgresql://host:5432/sales`

## Why aren't jars committed?

- Binaries bloat the git history.
- License terms differ per driver — keeping them out of the repo lets each user accept the licence they actually use.
- The download is a one-line command and the URLs above point at Maven Central, the canonical source.

## Why a folder dedicated to drivers?

This keeps the engine's "zero dependencies" promise intact for the standalone CLI. `src/` still compiles and runs without anything in `lib/`. Only the MCP server (an opt-in subproject) needs the driver, and the classpath separation makes that explicit.
