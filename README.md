# storage-sharded-service — Plan to Production

> Project ini sekarang **monolith 1 proses + 2 H2 in-memory**. README ini adalah **rencana** untuk mengubahnya jadi sistem terdistribusi yang bisa dipakai orang lain (`docker compose up` -> API jalan). Ikuti milestone berurutan — tiap phase punya verifikasi yang jelas.

## 0. Ringkasan

Single database tidak cukup: data kebesaran & throughput kurang. Solusi: **horizontal sharding**. Client cuma kenal **router**; router menentukan shard pemilik key via **consistent hashing + virtual nodes** dan meneruskan request ke **shard node** yang tepat. Tiap shard node punya DB sendiri. **Shard registry** menyimpan shard map + health via heartbeat. **Replica node** mengejar ketertinggalan secara async lewat **RabbitMQ**.

```
client -> [router:8080] --(poll map)--> [registry:8081]
               |  --forward--> [shard-node-0:8082] -> DB shard0 ┐
               |  --forward--> [shard-node-1:8083] -> DB shard1 ├─ Postgres (1 container, 3 DB)
               |  --forward--> [shard-node-2:8084] -> DB shard2 ┘
               |                          | publish write/delete
               |                          v
               |                    [RabbitMQ:5672] -> [replica:8085] -> DB replica
```

Solid: request path sync. Dashed: heartbeat + map polling. Queue: replikasi async.

---

## 1. Status Sekarang vs Target

| Aspek | Sekarang | Target (DoD) |
|-------|----------|--------------|
| Proses | 1 Spring Boot, semua di `ShardRouter` | 5 service: `registry`, `router`, `shard-node` (N instance), `replica` |
| DB | 2x `jdbc:h2:mem` hilang tiap restart | 1 Postgres container, 3 database `shard0/1/2` + 1 `replica`, volume-backed |
| Routing | `HashRing` ada, tapi rebalance cuma handle node hilang, repo hard-coded `shard0/1` | Consistent hashing bener, repo dinamis dari registry, N shard tanpa ubah kode |
| Registry | In-memory `ConcurrentHashMap` tapi di-heartbeat oleh router sendiri -> never unhealthy | Registry standalone, heartbeat dari shard node, timeout 10s, `GET /shards/map` |
| Replikasi | Tidak ada | RabbitMQ `shard.events` topic, best-effort async |
| API | `200` untuk PUT, map di `/registry/nodes` | `201 {shardId, nodeId}` untuk PUT, `GET /shards/map` sesuai spek |
| Jalanin | `./mvnw spring-boot:run` | `docker compose up` + local dev tanpa docker tetap bisa |
| Test | `contextLoads` doang | 2 test DoD: remap fraction + replica convergence |

> Bug kritis yang ditemukan di review dan akan diperbaiki di Phase 0: (1) add shard tidak memindahkan key lama -> GET 404 palsu (`ShardRouter.java:70-77`), (2) repo hard-coded -> NPE kalau node baru daftar, (3) router heartbeat palsu -> health check mati, (4) `ring` non-volatile -> data race, (5) `Rebalancer.move` tidak atomik.

---

## 2. Keputusan Teknologi (dikunci)

| Keputusan | Pilihan | Alasan |
|-----------|---------|--------|
| Struktur build | **Multi-module Maven** | Batas antar service keras lewat `common` saja. Folder: `common`, `registry`, `router`, `shard-node`, `replica`. Alternatif (single module, beda main class) ditolak karena mudah bocor import. |
| Queue replikasi | **RabbitMQ** (bukan Kafka) | 1 container, startup cepat, `spring-amqp` simpel. Kafka ditolak: butuh KRaft, lebih berat untuk demo lokal. Redis Streams ditolak: campur aduk registry + queue. |
| Persistensi per shard | **1 Postgres, 3 database** (`shard0`, `shard1`, `shard2`) + `replica` | Sesuai spek "one schema/DB per shard", tapi hemat RAM vs 3 container Postgres. H2 file ditolak: tidak nunjukin setup DB beneran. |
| Registry state | **In-memory** + heartbeat TTL (tanpa Postgres/Redis) | Simpel, cukup untuk training project. Documented trade-off: hilang kalau registry restart (acceptable untuk v1). |
| Map distribution | **Polling** (router poll registry tiap 5s) | Simpel, sudah ada `@Scheduled`. Push (WebSocket/SSE) lebih kompleks tanpa benefit di skala ini. |
| Shard map key | **Consistent hashing + 150 virtual nodes per physical shard**, hash = MD5 first 8 bytes | Sesuai spek. 150 = sweet spot (real system pakai 100-200): distribusi ~±3% untuk N kecil, tanpa overhead ring besar. Modulo ditolak: `N` berubah -> hampir semua key pindah. |
| Inter-service call | `RestTemplate` / `WebClient` (HTTP) | Sesuai spek, tanpa gRPC di v1. |

---

## 3. Sharding Strategy — Detail + Worked Example

**Cara key -> shard:**
1. Hash `key` dengan MD5, ambil 8 byte pertama sebagai `long` (signed, urutan `TreeMap`).
2. Hash tiap virtual node `nodeId#i` (i=0..149) dengan fungsi yang sama, masukkan ke `TreeMap<Long, nodeId>`.
3. `getNode(key)` = `ceilingEntry(hash(key))`, kalau null wrap ke `firstEntry()`.

**Kenapa virtual nodes?** Tanpa vnode, tiap physical node cuma 1 titik di ring -> distribusi timpang kalau node sedikit. Dengan 150 vnode, tiap node tersebar 150 titik -> key tersebar merata.

**Worked example `3 shard -> 4 shard`:**
- Misal 10.000 key terdistribusi merata di `shard0/1/2` (~3333 tiap shard) dengan 150 vnode.
- Tambah `shard3` (150 titik baru). Di consistent hashing, hanya key yang hash-nya jatuh di interval yang sekarang dimiliki `shard3` yang pindah.
- Expected yang pindah ~ `1/(N+1)` = `1/4` = 25% total key (~2500), diambil proporsional dari 3 shard lama (~833 per shard). Sisa 75% tetap.
- Bandingkan dengan **modulo** `hash%N`: `hash%3` -> `hash%4` memindahkan ~ `1 - 1/N` dekat 75-90% key -> reshuffle besar, tidak acceptable untuk produksi.

**Apa yang terjadi ke key yang sudah tersimpan?** Saat membership berubah, shard node sumber yang masih menyimpan key yang sekarang dimiliki node baru harus **memindahkan** key tersebut (Phase 4). Tanpa migrasi, GET akan 404 walau data masih ada di node lama — ini bug yang ada sekarang.

---

## 4. Struktur Repo Target

```
shardService/
├── pom.xml                     # parent (multi-module)
├── docker-compose.yml
├── architecture.png
├── common/                     # tidak ada logic bisnis, cuma shared contract
│   └── src/main/java/.../common
│       ├── hash/HashRing.java  # pindahan dari sekarang, API dirapikan
│       ├── dto/RecordDto.java
│       ├── dto/ShardMapEntry.java  # {shardId, nodeId, address, status, rangeHint}
│       └── dto/ReplicationEvent.java
├── registry/
│   └── src/main/java/.../registry
│       ├── ShardRegistry.java  # pindahan, jadi @RestController standalone
│       ├── ShardNode.java
│       └── RegistryController.java
├── shard-node/
│   └── src/main/java/.../shardnode
│       ├── ShardNodeApp.java   # register + heartbeat loop on startup
│       ├── InternalRecordController.java  # PUT/GET/DELETE /internal/records/{key}
│       ├── ReplicationPublisher.java      # afterCommit -> RabbitMQ
│       └── Rebalancer.java     # migrasi key saat ring berubah
├── router/
│   └── src/main/java/.../router
│       ├── RouterApp.java
│       ├── ShardRouter.java    # poll registry, forward via HTTP, retry-once
│       └── RecordController.java  # PUT/GET/DELETE /records/{key}, GET /shards/map
└── replica/
    └── src/main/java/.../replica
        ├── ReplicaApp.java
        └── ReplicationConsumer.java  # @RabbitListener
```

**Dependency rule:** `router`, `shard-node`, `replica`, `registry` boleh depend ke `common`. **Tidak boleh** depend silang (router tidak import shard-node). Enforce via `dependency:analyze` atau `archunit` di CI.

---

## 5. Kontrak API

### 5.1 Router — public API (yang client pakai)

| Method | Path | Request | Response | Catatan |
|--------|------|---------|----------|---------|
| PUT | `/records/{key}` | body = raw JSON string atau `{value: string}` | `201 {shardId, nodeId}` | Hitung owner via ring, forward ke shard node. Retry sekali dengan map fresh kalau shard unreachable. |
| GET | `/records/{key}` | - | `200 {key, value}` / `404 {error}` | Sama, forward. |
| DELETE | `/records/{key}` | - | `204` / `404` | Forward. |
| GET | `/shards/map` | - | `200 [{shardId, nodeId, address, status: UP/DOWN, vnodes: 150}]` | Proxy dari registry `GET /registry/map` + status. |

Error konsisten: `{status, error, message, path}` via `@ControllerAdvice`. Jangan bocorkan stacktrace (`server.error.include-stacktrace=never`).

### 5.2 Shard node — internal API (hanya dipanggil router & untuk migrasi)

| Method | Path | Deskripsi |
|--------|------|-----------|
| PUT | `/internal/records/{key}` | Upsert lokal, lalu publish event. Return `200`. |
| GET | `/internal/records/{key}` | Return `200` / `404` |
| DELETE | `/internal/records/{key}` | `204` / `404` |
| GET | `/internal/records/keys` | `200 [key...]` — dipakai rebalancer untuk list key lokal |
| POST | `/internal/migrate` | Body `{key, value}` — dipakai node lain untuk push key yang sekarang jadi miliknya |

### 5.3 Registry

| Method | Path | Deskripsi |
|--------|------|-----------|
| POST | `/registry/register` | Body `{nodeId, address}` — shard node daftar |
| POST | `/registry/heartbeat/{nodeId}` | Heartbeat |
| POST | `/registry/deregister/{nodeId}` | Optional, saat graceful shutdown |
| GET | `/registry/nodes` | `["shard0", ...]` active |
| GET | `/registry/status` | `{"shard0": true, ...}` |
| GET | `/registry/map` | `[{nodeId, address, status, lastHeartbeat}]` — sumber kebenaran untuk router |

### 5.4 Replication Event (RabbitMQ)

- Exchange: `shard.events` (topic), routing key `shard.<nodeId>.record.<op>`
- Queue: `replica.queue` (durable)
- Payload:
```json
{
  "key": "user:123",
  "value": "{\"name\":\"...\"}",
  "op": "PUT|DELETE",
  "sourceNodeId": "shard1",
  "timestamp": "2026-09-27T00:00:00Z",
  "seq": 42
}
```
- Consumer idempotent: `PUT` = upsert by `key` (last-write-wins by `timestamp`), `DELETE` = delete if exists.
- Best-effort async: shard node `publish` after commit, tidak `await` ack replica.

---

## 6. Data & Konfigurasi

**Postgres (1 container, 4 DB):**
- Container `postgres:16`, volume `pgdata`, init script `CREATE DATABASE shard0/1/2/replica`.
- Tiap service pakai `spring.datasource.url=jdbc:postgresql://postgres:5432/<db>`.

**Env var per service (12-factor):**

| Var | Contoh | Dipakai |
|-----|--------|---------|
| `SERVER_PORT` | `8080` router, `8081` registry, `8082` shard0 | Semua |
| `REGISTRY_URL` | `http://registry:8081` | router, shard-node |
| `SHARD_ID` | `shard0` | shard-node |
| `SHARD_ADDRESS` | `http://shard-node-0:8082` | shard-node (saat register) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://...` | shard-node, replica |
| `RABBITMQ_URL` | `amqp://guest:guest@rabbitmq:5672` | shard-node, replica |
| `SHARD_VNODES` | `150` | router, shard-node (rebalance) |
| `REGISTRY_TIMEOUT_SECONDS` | `10` | registry |
| `ROUTER_POLL_MS` | `5000` | router |
| `HEARTBEAT_INTERVAL_MS` | `3000` | shard-node |

**Failure handling (didokumentasikan, bukan cuma code):**
- Shard node kirim heartbeat tiap 3s. Registry tandai `DOWN` kalau `now - lastHeartbeat > 10s`.
- Router poll registry tiap 5s, rebuild `HashRing` kalau `activeNodes` berubah. Baca `ring` via `volatile` + copy-on-write (fix data race sekarang).
- Saat forward gagal (timeout / connection refused), router **refresh map sekali** dari registry lalu retry ke owner baru. Kalau masih gagal atau owner `DOWN`, return `503 {error: "shard unavailable", shardId}` — jangan return stale data.
- Replikasi: kalau RabbitMQ down, shard node log & lanjut (write tetap sukses). Replica catch-up saat queue kembali (durable queue).

---

## 7. Cara Jalanin

### Prereq
- Java 21, Maven 3.9+, Docker & Docker Compose

### Dengan Docker (recommended, sesuai DoD)
```bash
docker compose up --build
# tunggu semua healthy: router 8080, registry 8081, shard-node 8082-8084, rabbitmq 5672/15672, postgres 5432, replica 8085
curl -X PUT localhost:8080/records/user:1 -H 'Content-Type: application/json' -d '"hello"'
curl localhost:8080/records/user:1
curl localhost:8080/shards/map
curl localhost:8081/registry/map
```

### Tanpa Docker (local dev)
```bash
# terminal 1: postgres & rabbitmq saja
docker compose up postgres rabbitmq -d
# terminal 2-6: tiap service (profile local pakai H2 file kalau mau tanpa postgres)
./mvnw -pl registry -am spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
SHARD_ID=shard0 SERVER_PORT=8082 REGISTRY_URL=http://localhost:8081 ./mvnw -pl shard-node -am spring-boot:run
# dst untuk shard1/2, router, replica
```

---

## 8. Rencana Eksekusi — Ikuti Urutan Ini

> Aturan: jangan loncat phase. Tiap phase selesai = code + test + commit. Build harus hijau sebelum lanjut.

### Phase 0 — Perbaiki fondasi (0.5 hari) — *wajib biar phase lain tidak di atas bug*
- [ ] `HashRing.java` pindah ke `common`, rapikan API: `addNode(node, vnodes)` / `removeNode(node, vnodes)` konsisten, tambah `size()`, `isEmpty()`. Hapus `addNode(node)` 1-arg yang tidak konsisten.
- [ ] `ShardRouter` fix: `ring` + `lastKnownNodes` jadi `volatile` + copy-on-write, hapus heartbeat palsu (`registry.heartbeat("shard0")`), hilangkan hard-code `shard0/1` (baca dari registry), perbaiki rebalance untuk **add** (pindahkan key dari survivor ke new node, bukan cuma dari removed node).
- [ ] `Rebalancer.move` bikin atomik per key (atau document trade-off: at-most-once, retry idempotent).
- [ ] `RecordController` PUT return `201 {shardId, nodeId}` sesuai spek, error handling `@ControllerAdvice`.
- [ ] Verifikasi: `./mvnw -o -q compile` hijau, `ShardRouter` tidak lagi NPE kalau node baru muncul.

### Phase 1 — Registry standalone (1 hari)
- [ ] Buat module `common` + `registry`. Pindah `ShardRegistry`, `ShardNode`, `RegistryController` ke `registry` (port 8081).
- [ ] Registry `POST /registry/register {nodeId, address}`, `POST /heartbeat/{nodeId}`, `GET /map` (buat router poll), `GET /status`.
- [ ] Timeout logic + unit test: node tidak heartbeat 10s -> `DOWN`, lalu `UP` lagi saat heartbeat kembali.
- [ ] Verifikasi: `curl -X POST localhost:8081/registry/register -H 'Content-Type: application/json' -d '{"nodeId":"shard0","address":"http://localhost:8082"}'` -> `GET /registry/map` muncul.

### Phase 2 — Shard node standalone (1.5 hari)
- [ ] Module `shard-node`. `InternalRecordController` (`/internal/records/*`), JPA repo single datasource (ambil `SHARD_ID` + `SPRING_DATASOURCE_URL` dari env).
- [ ] On startup: `register` ke registry, schedule heartbeat 3s, graceful `deregister` on shutdown.
- [ ] Dockerfile untuk shard-node, compose dengan Postgres (init 3 DB).
- [ ] Verifikasi: jalankan 2 shard-node (shard0:8082, shard1:8083) -> registry `GET /map` lihat 2 UP -> `curl -X PUT localhost:8082/internal/records/k1 -d '"v1"'` -> `GET` balik.

### Phase 3 — Router standalone (1 hari)
- [ ] Module `router` (port 8080). `ShardRouter` poll `GET /registry/map` tiap 5s, rebuild `HashRing` (150 vnodes) kalau `activeNodes` berubah.
- [ ] `RecordController` public: `PUT /records/{key}` forward via `RestTemplate` ke `http://<owner>/internal/records/{key}`, handle `404/204/201`, retry-once dengan map fresh kalau `IOException`.
- [ ] `GET /shards/map` proxy + status.
- [ ] Test: `PUT /records/a` -> cek `GET /shards/map` tunjuk shard benar (deterministik: `curl localhost:8080/debug/route/a` kalau masih ada, atau hitung hash lokal).
- [ ] Verifikasi: `docker compose up registry + 2 shard-node + router` -> `curl -X PUT localhost:8080/records/x -d '"1"'` -> `curl localhost:8080/records/x` = `1`. Kill satu shard-node -> `GET /shards/map` jadi DOWN dalam ~10s -> `PUT` ke key yang tadinya milik node mati -> `503` (bukan 404 palsu).

### Phase 4 — Consistent hashing + rebalancing beneran (1 hari)
- [ ] Implement migrasi: saat router detect `activeNodes` berubah, shard node yang kehilangan key push key tersebut ke owner baru via `POST /internal/migrate`, lalu delete lokal (idempotent, retry).
- [ ] Alternatif simpel untuk v1: router trigger rebalance endpoint di tiap shard node (`POST /internal/rebalance {newNodes}`), tiap node scan `findAllKeys()` dan pindahkan yang bukan miliknya.
- [ ] **Test DoD #1**: tulis 1000 key, catat `routeNode(key)` untuk N=2, lalu N=3 (tambah shard2). Assert yang pindah ~25% ±5% dan semua key masih GET-able lewat router.
- [ ] Verifikasi: `./mvnw -pl common,router -am test -Dtest=HashRingTest,RebalanceTest` hijau.

### Phase 5 — Replikasi async via RabbitMQ (1 hari)
- [ ] Compose tambah `rabbitmq:3` (port 5672, management 15672).
- [ ] `shard-node`: `ReplicationPublisher` — setelah `save/delete` commit, publish `ReplicationEvent` ke `shard.events`.
- [ ] Module `replica` (port 8085): `@RabbitListener(queues="replica.queue")`, upsert/delete ke DB `replica` (idempotent by `key` + `timestamp`).
- [ ] **Test DoD #2**: burst 100 PUT via router -> tunggu 2s -> `SELECT count(*) FROM records` di DB replica == 100 dan `GET` via replica `GET /internal/records/{key}` (atau query DB) cocok.
- [ ] Verifikasi: kill replica, tulis 10 key, hidupkan replica -> eventually converge.

### Phase 6 — Packaging, CI, docs final (0.5 hari)
- [ ] Parent `pom.xml` multi-module, tiap module punya `Dockerfile` multi-stage.
- [ ] `docker-compose.yml` final: `postgres`, `rabbitmq`, `registry`, `router`, `shard-node-0/1/2`, `replica` + healthcheck + volume + network.
- [ ] GitHub Actions: `mvn verify` + `docker compose config` lint.
- [ ] Bersihkan `.classpath/.project/.settings` dari git, tambah `.gitignore` yang bener, tambah `LICENSE` (MIT), isi `pom.xml` `<name>/<description>`.
- [ ] Tambah `actuator` `/actuator/health` di tiap service, `logback` proper, matikan `show_sql` di prod profile.
- [ ] Verifikasi akhir: `docker compose up --build` dari clone fresh -> semua curl di §7 jalan -> `./mvnw verify` hijau.

---

## 9. Definition of Done (checklist sebelum dianggap selesai)

- [ ] `docker compose up` dari clone fresh jalan tanpa manual step
- [ ] README ini sudah diisi hasil konkret (bukan rencana): sharding scheme + worked example 3->4, format shard map, cara kerja replikasi & failure handling — tiap satu punya contoh konkret
- [ ] Test `addingShardRemapsOnlyFraction` hijau
- [ ] Test `writeEventuallyVisibleOnReplica` hijau
- [ ] Commit incremental (bukan 1 dump), tiap phase 1 commit minimal
- [ ] `actuator/health` UP di semua service, log tanpa `System.out`

---

## 10. Batasan & Trade-off yang Disadari

- Registry in-memory: kalau registry mati, map hilang — acceptable untuk v1, next step bisa persist ke Postgres/Redis.
- Replikasi best-effort: tidak ada exactly-once, hanya at-least-once idempotent. Write ke primary tidak menunggu replica.
- Rebalancing push-model: ada window kecil key duplikat (sudah dipindah tapi belum dihapus di sumber) — diatasi dengan idempotent upsert + delete retry.
- 1 Postgres 3 DB: bukan "independent disk" beneran, tapi cukup untuk nunjukin isolasi schema tanpa makan RAM 3 container.
