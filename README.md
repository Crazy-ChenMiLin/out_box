# Outbox + Local Canal

This project is wired for your local containers:

- `canal-server` exposes `11111:11111` and runs in `tcp` mode.
- `kafka-kraft` exposes `9092:9092`.
- Spring Boot connects to Canal, receives `demo1.outbox` binlog INSERT events,
  and publishes the outbox `payload` to Kafka topic `inventory-events`.

## Configure MySQL

MySQL must enable ROW binlog. Run the SQL in
`mysql/init/01-schema.sql` once, or apply the same schema and user manually.

The Canal container connects to host MySQL through:

```properties
canal.instance.master.address=host.docker.internal:3306
```

If MySQL is also inside Docker, change that value to the MySQL container name or
IP before applying the Canal config.

## Apply Canal Config

The config files are in:

- `canal/conf/canal.properties`
- `canal/conf/example/instance.properties`

Apply them to your running `canal-server` container:

```powershell
.\scripts\apply-canal-config.ps1
```

The script backs up the current container config under `canal/backup/` and then
restarts `canal-server`.

## Canal Admin UI

Start the Canal Admin web console:

```powershell
.\scripts\start-canal-admin.ps1
```

Then open:

```text
http://localhost:8089/
```

Default UI login:

```text
admin / 123456
```

The script initializes the `canal_manager` database in local MySQL if it does
not already exist, then starts `canal/canal-admin:v1.1.8` on port `8089`.

If the UI opens but the Canal server list is empty, the existing `canal-server`
container was not started in Admin registration mode. Start or recreate
`canal-server` with these additional settings when you want it to register into
the panel:

```powershell
-e canal.admin.manager=host.docker.internal:8089 `
-e canal.admin.port=11110 `
-e canal.admin.user=admin `
-e canal.admin.passwd=4ACFE3202A5FF5CF467898FC58AAB1D615029441 `
-e canal.admin.register.auto=true `
-e canal.admin.register.name=local-canal-server
```

## Run the App

If Chinese logs look garbled in PowerShell, switch the terminal to UTF-8 first:

```powershell
chcp 65001
```

```powershell
.\mvnw.cmd spring-boot:run
```

## Produce an Event

```powershell
curl -X POST "http://localhost:8080/api/inventory/sku-1?qty=10"
curl -X POST "http://localhost:8080/api/inventory/sku-1/decrement"
```

## Read Kafka Messages

```powershell
docker exec -it kafka-kraft kafka-console-consumer `
  --bootstrap-server localhost:9092 `
  --topic inventory-events `
  --from-beginning
```

The Kafka message value is the business JSON stored in `outbox.payload`.

## How to Read Logs

The app logs are intentionally short and mostly Chinese, so the main flow is
easy to follow:

- `[请求]`: HTTP request reached the controller.
- `[业务]`: business logic is running inside the database transaction.
- `[Canal]`: Spring has pulled binlog entries from canal-server.
- `[Kafka]`: an outbox event is being sent to Kafka.
- `[Outbox]`: the database row has been marked `SENT`.
- `[备用发布器]`: optional fallback poller, only used when `outbox.publisher.enabled=true`.

Normal Canal flow:

```text
[请求] 收到扣减库存请求
[业务] 库存已扣减
[业务] outbox 事件已写入
[Canal] 监听到 outbox 新事件
[Kafka] 发送成功
[Outbox] 状态已改为 SENT
```

## Optional Fallback Without Canal

The old Spring polling publisher is disabled:

```properties
outbox.publisher.enabled=false
```

Only set it to `true` when Canal is disabled, otherwise the same outbox row can
be published twice.
