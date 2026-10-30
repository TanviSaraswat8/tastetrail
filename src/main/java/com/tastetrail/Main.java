package com.tastetrail;

import com.sun.net.httpserver.HttpServer;
import com.tastetrail.db.Db;
import com.tastetrail.db.Seeder;
import com.tastetrail.security.TokenService;
import com.tastetrail.web.*;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;

/**
 * TasteTrail – Online Food Ordering System.
 *   java -cp "out:lib/*" com.tastetrail.Main          start the server on http://localhost:8080
 *   java -cp "out:lib/*" com.tastetrail.Main --seed   reset the database with demo data and exit
 */
public final class Main {
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC")); // timestamps are stored in UTC and shown in IST
        java.nio.file.Files.createDirectories(Path.of("data"));
        System.setProperty("derby.stream.error.file", "data/derby.log");
        String dbPath = System.getenv().getOrDefault("DB_PATH", "data/tastetrail-db");
        Db.init(dbPath);

        boolean seedOnly = args.length > 0 && args[0].equals("--seed");
        boolean empty = AppContext.i(Db.one("SELECT COUNT(*) AS n FROM restaurants").get("n")) == 0;
        if (seedOnly || empty) {
            long t = System.currentTimeMillis();
            Seeder.seed();
            System.out.printf("Seeded %s restaurants, %s menu items, %s users, %s orders, %s reviews in %d ms%n",
                count("restaurants"), count("menu_items"), count("users"), count("orders"), count("reviews"), System.currentTimeMillis() - t);
            System.out.println("Logins -> customer: demo@tastetrail.in / demo123   admin: admin@tastetrail.in / admin123");
            if (seedOnly) { Db.shutdown(); return; }
        }

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        TokenService tokens = new TokenService(System.getenv().getOrDefault("JWT_SECRET", "change-this-secret-for-real-deployments"), 7 * 24 * 3600);
        AppContext ctx = new AppContext(tokens);
        Http.Router router = new Http.Router(tokens);
        List<Controller> controllers = List.of(new AuthController(ctx), new RestaurantController(ctx), new OrderController(ctx), new AdminController(ctx), new ExploreController(ctx));
        for (Controller c : controllers) c.register(router); // polymorphism: each controller adds its own routes
        server.createContext("/api/", router);
        server.createContext("/", new Http.StaticFiles(Path.of("web")));
        server.setExecutor(Executors.newFixedThreadPool(8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(0); Db.shutdown(); }));
        server.start();
        System.out.println("TasteTrail is running on http://localhost:" + port);
    }

    private static Object count(String table) { return Db.one("SELECT COUNT(*) AS n FROM " + table).get("n"); }
}
