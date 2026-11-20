# BTH5 - Report Management Service (SOA)

Bai TH5 mon Kien truc huong dich vu: thong ke **ton kho, hang ban, doanh thu, chi phi, loi nhuan** theo **san pham va don hang**.

## 1. Kien truc: ba service doc lap

```text
                 Postman / API Client
                         |
            Report Service (8083)
             |        |        |
             |        |        +--> BTH5_REPORT MySQL (chi phi & snapshot bao cao)
             |        +-----------> GET /orders   Order Service (8082) -> BTH4_ORDER
             +--------------------> GET /products Product Service (8081) -> BTH3_PRODUCT
```

Report Service **KHONG** query database cua BTH3/BTH4. Ket noi qua HTTP REST, co database **BTH5_REPORT** rieng.

## 2. Yeu cau chay

- Java/JDK 21; IntelliJ IDEA, Eclipse/STS; Maven (IDE co Maven tich hop).
- MySQL Server 8.x, MySQL Workbench.
- BTH3 Product Service port `8081` va BTH4 Order Service port `8082` da chay.
- Ma nguon duoc doi chieu voi hai project BTH3, BTH4 da lam truoc do (DTO JSON, trang thai `PENDING / CONFIRMED / COMPLETED / CANCELLED`).

**Moi truong:**

1. Mo MySQL Workbench, chay **toan bo** `database.sql` cua BTH5 de tao database `BTH5_REPORT` va 5 bang.
2. Mo **dung thu muc chua pom.xml** trong IntelliJ; dat **Project SDK = JDK 21**, Maven Reload. Neu gap loi JVM target 1.5, sua `Settings > Compiler > Java Compiler > per-module bytecode = 21`.
3. Run Configuration > Environment variables:
   - `DB_USER=root`
   - `DB_PASSWORD=<YOUR_MYSQL_PASSWORD>` (khong commit mat khau)
   - `PRODUCT_SERVICE_URL=http://localhost:8081` (mac dinh)
   - `ORDER_SERVICE_URL=http://localhost:8082` (mac dinh)
4. Chay `Bth5ReportApplication` trong cua so IntelliJ rieng, khong tat BTH3/BTH4.
5. Vao `http://localhost:8083/reports` de thu GET: luc dau `[]`.

Chay bang PowerShell (neu Maven co san):

```powershell
$env:DB_USER = 'root'
$env:DB_PASSWORD = '<YOUR_MYSQL_PASSWORD>'
mvn spring-boot:run
```

Neu su dung CMD thay vi PowerShell:

```cmd
set "DB_USER=root"
set "DB_PASSWORD=<YOUR_MYSQL_PASSWORD>"
mvn spring-boot:run
```

`spring.jpa.hibernate.ddl-auto=update` phuc vu moi truong hoc tap. Ban van nen chay `database.sql` de chu dong tao schema. Trong production, dung migrations (Flyway/Liquibase) va `validate`.

## 3. Dinh nghia chi tieu (khong suy doan chi phi)

| Chi tieu | Cach tinh |
|---|---|
| So luong ton | Tong `quantity` hien tai do BTH3 tra ve |
| So luong da ban | Tong `quantity` cua cac `order.items` co `status = COMPLETED` |
| Doanh thu | Tong `subtotal` cua cac `order.items` co `status = COMPLETED` |
| Chi phi | Tong `unitCost * quantity` cua nhung item da ban; unitCost do BTH5 luu |
| Loi nhuan | Doanh thu - Chi phi |

**QUAN TRONG:** BTH4 CHUA tru kho khi tao/hoan thanh don. `stockQuantity` hien thi la gia tri BTH3 hien bao, **KHONG** lay gia tri BTH3 tru tiep `soldQuantity` (se tinh 2 lan neu sau nay BTH3 tru kho).

- `CONFIRMED`, `PENDING`, `CANCELLED`: van xuat hien tren bao cao **theo don hang**, nhung `countedAsSale=false`, `revenue=0`, `cost=0`, `soldQuantity=0`. `orderValue` la gia tri don du kien, khong phai doanh thu thuc te.
- Tinh loi nhuan theo **gia von hang ban** (COGS) tu BTH5; **chua gom** thue, van chuyen, luong, phi quan ly. Day la **loi nhuan gop**, khong phai loi nhuan rong.
- Gia ban trong bao cao lay tu **unitPrice va subtotal da duoc BTH4 luu snapshot**, khong lay gia ban hien tai cua san pham trong BTH3.
- Gia von duoc cau hinh tai thoi diem tao bao cao (khong phai lich su gia von tu ngay ban, do BTH4 chua luu `unitCost` khi dat hang). Khi gia von thay doi, tao bao cao moi co the tinh lai COGS cho don cu; bao cao da luu thi giu nguyen.
- Neu da ban san pham nhung **chua khai bao gia von**, `POST /reports/generate` tra HTTP **422**, khong gan chi phi bang 0 de tao loi nhuan gia.
- Neu san pham tung ban nhung da bi xoa khoi BTH3, bao cao van ghi nhan don cu, `stockQuantity=null` va `inCatalog=false` (khong tu coi la 0 ton).
- Snapshot duoc tao lan luot tu 2 service, KHONG phai giao dich phan tan, nen so lieu co the sai lech neu co sua don hoac thay kho dung giua 2 lan lay du lieu. Su dung du lieu on dinh khi demo.

## 4. REST endpoints

| Method | Endpoint | Muc dich | HTTP thanh cong |
|---|---|---|---|
| `PUT` | `/costs/{productId}` | Nhap/cap nhat gia von | 200 |
| `GET` | `/costs` | Xem gia von cac san pham | 200 |
| `POST` | `/reports/generate` | Goi BTH3, BTH4 va luu snapshot vao BTH5_REPORT | 201 |
| `GET` | `/reports` | Xem lich su bao cao (tom tat) | 200 |
| `GET` | `/reports/latest` | Xem snapshot moi nhat (tom tat + 2 loai chi tiet) | 200 |
| `GET` | `/reports/{id}` | Xem lai snapshot da luu | 200 |
| `GET` | `/reports/products` | Thong ke **theo san pham** cua bao cao moi nhat | 200 |
| `GET` | `/reports/orders` | Thong ke **theo don hang** cua bao cao moi nhat | 200 |
| `GET` | `/reports/{id}/products` | Bao cao tung san pham theo snapshot ID | 200 |
| `GET` | `/reports/{id}/orders` | Bao cao tung don hang theo snapshot ID | 200 |

Tat ca API co alias `/api/*`. Ma loi: 400 sai input, 404 chua co bao cao/khong thay ID, 422 thieu gia von cho don COMPLETED, 503 khong ket noi BTH3/BTH4, 502 du lieu upstream bi sai.

## 5. Huong dan kiem thu tren may

### Buoc A. San pham va don hang

Mo `http://localhost:8081/products` de xem ID san pham. Neu khong co san pham, tao 1 san pham bang Postman: `POST http://localhost:8081/products`, body:

```json
{"name":"Laptop Dell Inspiron","description":"BTH5 SOA","price":18500000,"quantity":10}
```

Thay ID thuc te vao Postman, tao don qua `POST http://localhost:8082/orders`:

```json
{"customerName":"Nguyen Van A","items":[{"productId":3,"quantity":2}]}
```

**Thay `3` bang ID thuc te.** Order moi `PENDING` **chua duoc tinh la hang ban**.

Chuyen `PENDING -> CONFIRMED -> COMPLETED` bang `PATCH /orders/{orderId}/status`:

```json
{"status":"CONFIRMED"}
```

```json
{"status":"COMPLETED"}
```

Doi `orderId` theo don vua tao. BTH4 khong cho chuyen truc tiep tu PENDING sang COMPLETED.

### Buoc B. Nhap gia von

`PUT http://localhost:8083/costs/3` (thay ID thuc te)

```json
{"unitCost":14000000}
```

### Buoc C. Tao bao cao

`POST http://localhost:8083/reports/generate` (khong can body).

Neu 1 don COMPLETED gom 2 san pham gia ban `18,500,000`, gia von `14,000,000`/san pham, ket qua **vi du**:

- `stockQuantity`: 10 (hien BTH3 bao 10)
- `soldQuantity`: 2
- `revenue`: 37,000,000
- `cost`: 28,000,000
- `profit`: 9,000,000

**Day la so minh hoa, khong phai du lieu thuc te tu may cua ban.**

Truy cap `GET /reports/latest`, `GET /reports/products`, `GET /reports/orders` de xem chi tiet va kiem tra chenh lech cua cac trang thai. Response tao bao cao co ca `summary` va cac truong tong hop tren top-level (`totalStockQuantity`, `totalSoldQuantity`, `totalRevenue`, `totalCost`, `totalProfit`) de de viet Postman tests.

### Buoc D. MySQL Workbench

```sql
USE BTH5_REPORT;
SELECT * FROM product_costs;
SELECT * FROM report_runs ORDER BY id DESC;
SELECT * FROM report_product_rows;
SELECT * FROM report_order_rows;
SELECT * FROM report_order_item_rows;
```

Cac bang tren nam trong **BTH5_REPORT**, khong dat chung database BTH3 hoac BTH4.

## 6. Automated tests

```
mvn test
```

`ReportMathTest` kiem tra doanh thu, loi nhuan, don CONFIRMED bi loai khoi doanh thu, ton kho cho san pham da xoa, thieu gia von. `ReportApiIntegrationTest` su dung H2 + Mock ProductClient/OrderClient; khong can MySQL, BTH3 hay BTH4 khi chay unit/integration test.

## 7. Dua len GitHub va nop

1. Tao repository `BTH5-Report-Service` tren GitHub Desktop, kiem tra `src`, `pom.xml`, `README.md`, `database.sql` nam ngay thu muc goc (khong long thu muc).
2. `.gitignore` loai `/target/`, `.idea/`, `.env`, `*.iml`. Kiem tra khong co mat khau trong file va lich su commit.
3. Commit `Complete BTH5 - SOA Report Service` va `Publish repository`.
4. Neu can giao vien xem, Public sau khi kiem tra secret, hoac moi giao vien vao Collaborators neu can Private.

## 8. Gioi han pham vi

Ban nay la bai thuc hanh local: chua co auth, role, bao mat khi public endpoint; khong expose cong 8083 len Internet, khong su dung trong production. De dat so lieu tai chinh chinh xac cho he thong thuc te, can luu gia von theo giao dich, xu ly hoan/doi, thue, giam gia va luu giao dich ton kho trong BTH3.
