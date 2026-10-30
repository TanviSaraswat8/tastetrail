---
title: "TasteTrail – Viva and Demo Guide"
subtitle: "Online Food Ordering System · Object Oriented Programming Using Java Language"
---

# 1. Where everything is

| What | Where | Say this in the viva |
|---|---|---|
| **Frontend** | `web/` folder | "9 HTML pages, one CSS file, and one JavaScript file per page. JavaScript calls our Java API with `fetch()`." |
| **Backend** | `src/main/java/com/tastetrail/` | "42 Java files in 9 packages, built on Java's own HTTP server – no framework." |
| **Database** | `data/` (created automatically) | "Apache Derby, an embedded SQL database. Seven tables with primary and foreign keys." |
| **Tests** | `src/test/java/com/tastetrail/CoreTest.java` | "22 unit tests, all passing." |
| **Start scripts** | `run.bat` (Windows), `run.sh` (Mac) | "One click compiles and starts the server." |
| **Online hosting** | `Dockerfile`, `render.yaml`, `DEPLOY.md` | "It can be deployed to the cloud as a Docker container." |

Backend packages (open these in the viva if asked "show me OOP"):

| Package | Open this file to show… |
|---|---|
| `model` | `User.java` (abstract) → `Customer.java`, `Admin.java` – **inheritance, abstraction, polymorphism** |
| `model` | `OrderStatus.java` – **enum with methods** (`canMoveTo`) |
| `model` | `Cart.java` – **encapsulation** (private list, unmodifiable view) |
| `coupon` | `Coupon.java` → `PercentCoupon.java`, `FlatCoupon.java` – **abstract class, template method, overriding** |
| `payment` | `PaymentMethod.java` (interface) → `OnlinePayment` → `UpiPayment`, `CardPayment`; `CashOnDelivery` – **interface, default and static methods** |
| `service` | `BillCalculator.java` – **method overloading** (`calculate(cart)` / `calculate(cart, coupon)`) |
| `service` | `Recommender.java` (interface) + 3 implementations; `HybridRecommender` – **composition** |
| `dao` | `BaseDao.java` – **generics** (`BaseDao<T>`) |
| `web` | `ApiException.java` – **custom exception hierarchy**; `Controller.java` – interface used in a loop in `Main.java` |

# 2. Starting the project

**On your laptop (do this first – it always works):**

1. Check Java: open Terminal / Command Prompt, type `java -version`. You need 17 or higher.
   If missing, install **Temurin 21** from https://adoptium.net (2 minutes).
2. Unzip the project. Windows: double-click `run.bat`. Mac: open Terminal in the folder, run `chmod +x *.sh` once, then `./run.sh`.
3. Wait for **"TasteTrail is running on http://localhost:8080"**, then open that address in Chrome.

**Before the viva:** run `seed.bat` / `./seed.sh` (server stopped) to reset to clean demo data, then start again.

**Online (optional, gives a public link):** follow `DEPLOY.md` (GitHub + Render, about 10 minutes, free).
Open the link 5 minutes before your turn – the free server sleeps when unused.

| Login | Email | Password |
|---|---|---|
| Customer | demo@tastetrail.in | demo123 |
| Admin | admin@tastetrail.in | admin123 |

Tip: open the **customer in a normal Chrome window** and the **admin in an Incognito window** side by side. Then you can show live tracking.

# 3. Five-minute demo script

1. **Sign-in page** – "Customers and admins sign in here. Passwords are stored as salted PBKDF2 hashes." Click **Customer demo**.
2. **Home page** – type **paneer** in the search box: "Search looks inside menus too – neither restaurant has 'paneer' in its name." Click **Pure veg**, then clear it.
3. **Punjabi Tadka** – add **2 × Butter Chicken** and **1 × Dal Makhani**. "Bestseller tags come from the order history."
4. **Cart** – point at **Frequently ordered together**: "74% of Butter Chicken orders also have Butter Naan – this is association-rule mining (support, confidence, lift)." Add Butter Naan.
5. Apply **FLAT75** – show the discount, free delivery, 5% GST and the delivery time. "The bill is calculated on the server, so nobody can change prices from the browser."
6. Pay with **UPI** → **Place order**. The tracking page opens.
7. In the **admin window**: **Live orders** → **Accept** → **Start preparing** → **Hand to rider**. Switch to the customer window – the progress bar has moved by itself.
8. Admin → **Mark delivered**, customer → give **5 stars**.
9. Admin → **Menu** – switch a dish **out of stock**; show it now says "Unavailable" for customers. Switch it back.
10. Admin → **Analytics** – revenue, average order value, peak hour (8–9 pm), top dishes, and the association-rules table.
11. If they ask about OOP, open `Coupon.java` and `PercentCoupon.java` (see section 1).

# 4. Likely viva questions and answers

**Q1. Which OOP concepts did you use?**
All four pillars. Encapsulation: private fields with getters, e.g. `Cart` hides its list. Abstraction: abstract classes `User`, `Coupon`, `OnlinePayment`, `BaseDao` and interfaces `PaymentMethod`, `Recommender`, `Controller`. Inheritance: `Customer` and `Admin` extend `User`; `PercentCoupon` and `FlatCoupon` extend `Coupon`. Polymorphism: `coupon.apply()` runs the subclass's `calculateDiscount()`; `user.canManageRestaurants()` answers differently for Customer and Admin.

**Q2. Difference between abstract class and interface? Where did you use each?**
An abstract class can have fields, constructors and implemented methods and a class can extend only one; an interface describes behaviour and a class can implement many. `Coupon` is abstract because all coupons share code, minimum order and the checks. `PaymentMethod` is an interface because UPI, card and cash share no state, only behaviour. `OnlinePayment` is an abstract class that implements the interface to share UPI/card behaviour.

**Q3. Show runtime polymorphism.**
`Coupon c = Coupon.create(...)` may return a `PercentCoupon` or a `FlatCoupon`. `BillCalculator` only calls `c.apply(subtotal)`; Java decides at run time which `calculateDiscount()` to run (method overriding, dynamic dispatch).

**Q4. Compile-time polymorphism?**
Method overloading: `BillCalculator.calculate(Cart)` and `calculate(Cart, Coupon)`; two constructors in `Restaurant`.

**Q5. What design patterns?**
Template Method (`Coupon.apply` is final and calls the abstract step), Factory Method (`User.create`, `Coupon.create`, `PaymentMethod.of`), Strategy (payment methods, recommenders), DAO (`BaseDao<T>` and subclasses), dependency injection through constructors (controllers receive `AppContext`).

**Q6. Why an enum for order status?**
The status can only be one of six fixed values, and each value knows where it may go next (`canMoveTo`). That makes impossible changes, such as PLACED → DELIVERED, rejected in one place.

**Q7. Where are generics used?**
`BaseDao<T>` – one class with `findOne` and `findAll` for any entity; `UserDao extends BaseDao<User>`. Also `Optional<T>`, `List<CartItem>`, `Map<Integer, List<Rule>>`.

**Q8. How do you handle exceptions?**
A custom hierarchy: `ApiException` with `BadRequest` (400), `Unauthorized` (401), `Forbidden` (403), `NotFound` (404), `Conflict` (409). Code throws the right one; the router catches `ApiException` in one place and sends a JSON error. Database code uses try-with-resources, and transactions roll back on any exception.

**Q9. How does the frontend talk to the backend?**
JavaScript `fetch()` sends HTTP requests with JSON to `/api/...`. The Java router matches the URL to a controller method, which returns a Java object; Gson converts it to JSON.

**Q10. Why no Spring Boot?**
To show the core Java concepts directly – HTTP handling, routing, JDBC and transactions – without a framework hiding them. It also runs with only a JDK, with nothing to download. Moving to Spring Boot is in Future Scope.

**Q11. What database? Show the tables.**
Apache Derby, embedded – it runs inside our Java program. Seven tables: users, restaurants, menu_items, coupons, orders, order_items, reviews. One user has many orders, one order has many order_items, and an order can have at most one review. (ER diagram: Figure 5 in the report.)

**Q12. How are passwords stored?**
Never in plain text. PBKDF2 with SHA-256, 120,000 iterations and a random 16-byte salt per user. When signing in we hash the typed password with the same salt and compare.

**Q13. What is the token? Can someone fake an admin token?**
A JSON Web Token signed with HMAC-SHA256 using a secret only the server knows. If anyone changes "customer" to "admin" inside it, the signature no longer matches and the server rejects it. We tested this (unit test 17, test case TC-21).

**Q14. How do you stop SQL injection?**
Every query uses `PreparedStatement` with `?` parameters, so user input is never treated as SQL. We tested a search for `' OR 1=1 --` – it returns nothing.

**Q15. Can a user change the price in the browser?**
No. The browser sends only dish IDs and quantities. The server reads prices from the database and calculates the bill (`BillCalculator`). We tested sending price 1 – the bill still used ₹329.

**Q16. How is the bill calculated?**
Item total → minus coupon → that is the food value. GST is 5% of the food value. Delivery is free at ₹499 or more; otherwise ₹30 for the first 3 km plus ₹8 per extra km. Plus ₹15 packaging. Example from the report: 1,034 − 75 = 959; GST 47.95; total ₹1,021.95.

**Q17. How do the recommendations work?**
Market basket analysis. Each past order is a basket. For dishes A and B: support = orders with both ÷ all orders; confidence = orders with both ÷ orders with A; lift = confidence ÷ support of B. We suggest dishes with lift above 1.2, ranked by confidence × lift. If there are fewer than 3, `HybridRecommender` fills the rest with popular dishes.

**Q18. How is the delivery time estimated?**
Preparation time + 2 minutes per item above three + travel time at 20 km/h + 5 minutes for hand-over.

**Q19. How does live tracking work?**
The tracking page asks the server for the order every 5 seconds. When the admin changes the status, the next request shows it. (WebSockets would push updates instantly – that's future scope.)

**Q20. Where is multithreading?**
The HTTP server uses a thread pool of 8 threads, so 8 requests can be handled at the same time. The recommendation model is built in a `synchronized` method and stored in a `volatile` field so every thread sees the latest one.

**Q21. What is a transaction? Where do you use one?**
A group of SQL statements that succeed or fail together. Placing an order inserts one row in `orders` and several in `order_items` inside one transaction (`OrderDao.create`), so we never get an order without its items.

**Q22. Is the data real?**
No. The 60 days of order history are generated by `Seeder.java` with a fixed random seed, so the analytics can be demonstrated. Payments are simulated. We say this clearly in the report.

**Q23. What would you improve?**
A real payment gateway (e.g. Razorpay), a delivery-partner app with maps, WebSocket push updates, MySQL/PostgreSQL with Spring Boot, and a forecast that understands weekends.

**Q24. Do you need Redis or a separate server to deploy it?**
No. It is one Java program with an embedded database. The Dockerfile builds it into a container, which any cloud host such as Render can run.

**Q25. What did each team member do?**
Agree on this as a team before the viva, so everyone can explain their own part (for example: frontend pages, backend and OOP classes, database and analytics, testing and report).

# 5. If something goes wrong

| Problem | Fix |
|---|---|
| `java` is not recognised | Install Temurin 21 from adoptium.net, then open a new terminal window. |
| "Address already in use" | The server is already running in another window – use that one, or close it and start again. |
| Mac says "permission denied" for run.sh | Run `chmod +x *.sh` once in the project folder. |
| Data looks messy after practice | Stop the server, run `seed.bat` / `./seed.sh`, start again. |
| Page looks broken or old | Press Ctrl + Shift + R (Cmd + Shift + R on Mac) to reload. |
| Online link is slow | The free server was asleep – wait about a minute; keep the laptop version ready as a backup. |
