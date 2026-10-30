# TasteTrail – Online Food Ordering System

Online food ordering web application with a customer site, an admin panel and sales analytics.

- **Backend:** Java 17+ (built-in `com.sun.net.httpserver` HTTP server, JDBC), Apache Derby embedded SQL database, Gson for JSON
- **Frontend:** HTML5, CSS3 and plain JavaScript (fetch API), Chart.js for the two charts
- No framework, no build tool and no database installation needed – just a JDK.

## Run it

Requires **JDK 17 or later** (`java -version` to check).

| | Windows | macOS / Linux |
|---|---|---|
| Start the app | double-click `run.bat` | `./run.sh` |
| Reset demo data | `seed.bat` | `./seed.sh` |
| Unit tests | `test.bat` | `./test.sh` |

Then open **http://localhost:8080**. The first start creates the database (`data/`) and fills it with demo data.

Demo logins:

| Role     | Email                 | Password |
|----------|-----------------------|----------|
| Customer | demo@tastetrail.in    | demo123  |
| Admin    | admin@tastetrail.in   | admin123 |

## Features

**Customer:** register/sign in · search restaurants by name, cuisine or dish · pure-veg and cuisine filters, sorting · menu with veg marks,
bestsellers and reviews · single-restaurant cart · server-calculated bill (coupon, delivery fee, packaging, 5% GST) and delivery-time
estimate · "Frequently ordered together" suggestions · UPI / card (simulated) or cash on delivery · live order tracking · cancel before
preparation · ratings · order history.

**Only on TasteTrail:** Budget Feast Planner (dynamic programming: best meal for N people within a total budget) · Mood Match quiz
and spin-the-wheel · Taste Passport (cuisine stamps, XP levels, bronze/silver/gold badges) · Fair Bill Split by dish · ₹0 platform fee
with a "where your money goes" bill. 14 restaurant brands, 95 dishes with real photos (credits page).

**Admin:** live order board · price, stock and new-dish management · open/close restaurant · sales analytics (KPIs, revenue trend with
7-day moving average and linear forecast, orders by hour, top dishes, revenue by restaurant/category, payments, ratings, association rules).

## Where everything is

| Part | Folder | What is inside |
|---|---|---|
| **Frontend** | `web/` | 9 HTML pages, `css/style.css`, `js/*.js` (one script per page + shared `app.js`) |
| **Backend** | `src/main/java/com/tastetrail/` | the Java server, split into packages below |
| Database | `data/` (created on first run) | Apache Derby files – nothing to install |
| Tests | `src/test/java/com/tastetrail/CoreTest.java` | 22 unit tests |

```
com.tastetrail
├── Main.java                 starts the server, creates and seeds the database
├── model/                    User (abstract) → Customer, Admin · Restaurant · MenuItem · Cart · CartItem · Bill · OrderStatus (enum)
├── coupon/                   Coupon (abstract) → PercentCoupon, FlatCoupon
├── payment/                  PaymentMethod (interface) → OnlinePayment (abstract) → UpiPayment, CardPayment · CashOnDelivery
├── service/                  BillCalculator · Recommender (interface) → AssociationRuleRecommender, PopularityRecommender, HybridRecommender · Analytics
├── dao/                      BaseDao<T> (generic, abstract) → UserDao, RestaurantDao, MenuItemDao, CouponDao · OrderDao
├── security/                 PasswordHasher (PBKDF2) · TokenService (JWT)
├── web/                      Http (router, static files) · Controller (interface) → AuthController, RestaurantController,
│                             OrderController, AdminController · AppContext · ApiException (+ subclasses)
└── db/                       Db (JDBC helper, schema, transactions) · Seeder (demo data)
```

## Put it online

See **DEPLOY.md** – free hosting on Render with the included `Dockerfile` and `render.yaml`. No Redis or separate database is needed.

## Configuration

Optional environment variables: `PORT` (default 8080), `JWT_SECRET` (set a strong value outside local demos), `DB_PATH` (default `data/tastetrail-db`).

Payments are simulated and the order history is synthetic demo data.
