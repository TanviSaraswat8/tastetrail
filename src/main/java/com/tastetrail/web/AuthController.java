package com.tastetrail.web;

import com.tastetrail.dao.UserDao;
import com.tastetrail.model.User;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.tastetrail.web.AppContext.map;

/** Registration, sign-in and "who am I". */
public class AuthController implements Controller {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private final AppContext ctx;

    public AuthController(AppContext ctx) { this.ctx = ctx; }

    @Override public void register(Http.Router r) {
        r.post("/api/auth/register", this::registerUser);
        r.post("/api/auth/login", this::login);
        r.get("/api/auth/me", q -> ctx.users.findById(q.requireUser().getId()).map(User::toJson).orElseThrow(() -> new ApiException.NotFound("User not found")));
    }

    private Object registerUser(Http.Req q) {
        String name = q.str("name"), email = q.str("email"), phone = q.str("phone");
        String password = q.body.get("password") instanceof String s ? s : null;
        if (name == null || name.isEmpty() || email == null || !EMAIL.matcher(email).matches()) throw new ApiException.BadRequest("Enter your name and a valid email");
        if (password == null || password.length() < 6) throw new ApiException.BadRequest("Password must be at least 6 characters");
        if (phone != null && !phone.isEmpty() && !PHONE.matcher(phone).matches()) throw new ApiException.BadRequest("Enter a valid 10-digit mobile number");
        email = email.toLowerCase(Locale.ROOT);
        if (ctx.users.emailExists(email)) throw new ApiException.Conflict("An account with this email already exists");
        User user = ctx.users.createCustomer(name, email, phone == null || phone.isEmpty() ? null : phone, ctx.passwords.hash(password));
        q.status(201);
        return map("token", ctx.tokens.issue(user), "user", user.toJson());
    }

    private Object login(Http.Req q) {
        String email = Objects.requireNonNullElse(q.str("email"), "").toLowerCase(Locale.ROOT);
        String password = q.body.get("password") instanceof String s ? s : null;
        UserDao.Credentials c = ctx.users.findCredentials(email).orElse(null);
        if (c == null || !ctx.passwords.verify(password, c.passwordHash())) throw new ApiException.Unauthorized("Incorrect email or password");
        return map("token", ctx.tokens.issue(c.user()), "user", c.user().toJson());
    }
}
