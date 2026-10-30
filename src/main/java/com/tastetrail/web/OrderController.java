package com.tastetrail.web;

import com.tastetrail.coupon.Coupon;
import com.tastetrail.db.Db;
import com.tastetrail.model.Bill;
import com.tastetrail.model.Cart;
import com.tastetrail.model.OrderStatus;
import com.tastetrail.model.User;
import com.tastetrail.payment.PaymentMethod;

import java.util.List;
import java.util.Map;

import static com.tastetrail.web.AppContext.i;

/** Customer side of orders: place, list, track, cancel and rate. */
public class OrderController implements Controller {
    private final AppContext ctx;

    public OrderController(AppContext ctx) { this.ctx = ctx; }

    @Override public void register(Http.Router r) {
        r.post("/api/orders", this::place);
        r.get("/api/orders", this::myOrders);
        r.get("/api/orders/:id", this::track);
        r.post("/api/orders/:id/cancel", this::cancel);
        r.post("/api/orders/:id/review", this::review);
    }

    private Object place(Http.Req q) {
        User user = q.requireUser();
        if (!user.canPlaceOrders()) throw new ApiException.Forbidden("Administrators cannot place orders");
        PaymentMethod payment = PaymentMethod.of(q.str("paymentMethod"));
        if (payment == null) throw new ApiException.BadRequest("Choose a payment method");
        String address = q.str("address");
        if (address == null || address.length() < 8) throw new ApiException.BadRequest("Enter a complete delivery address");
        Cart cart = ctx.loadCart(q);
        String code = q.str("coupon");
        Coupon coupon = ctx.findCoupon(code);
        Bill bill = ctx.billing.calculate(cart, coupon);
        if (code != null && !code.isEmpty() && (coupon == null || bill.getCouponError() != null))
            throw new ApiException.BadRequest(coupon == null ? "Invalid coupon code" : bill.getCouponError());
        int id = ctx.orders.create(user.getId(), cart, bill, payment, address);
        ctx.ordersChanged();
        q.status(201);
        return ctx.orderDetail(id);
    }

    private Object myOrders(Http.Req q) {
        List<Map<String, Object>> rows = Db.query("""
            SELECT o.id, o.total, o.status, o.created_at, o.payment_method, r.name AS restaurant, r.theme,
                   (SELECT rating FROM reviews WHERE order_id = o.id) AS rating
            FROM orders o JOIN restaurants r ON r.id = o.restaurant_id
            WHERE o.user_id = ? ORDER BY o.created_at DESC FETCH FIRST 50 ROWS ONLY""", q.requireUser().getId());
        ctx.addSummaries(rows);
        return rows;
    }

    private Object track(Http.Req q) {
        User u = q.requireUser();
        Map<String, Object> o = ctx.orderDetail(q.param("id"));
        // a customer may only see their own orders; an admin may see any
        if (o == null || (i(o.get("user_id")) != u.getId() && !u.canManageRestaurants())) throw new ApiException.NotFound("Order not found");
        return o;
    }

    /** Loads an order that belongs to the signed-in user, or fails with 404. */
    private Map<String, Object> ownOrder(Http.Req q) {
        Map<String, Object> o = ctx.orders.findRow(q.param("id"));
        if (o == null || i(o.get("user_id")) != q.requireUser().getId()) throw new ApiException.NotFound("Order not found");
        return o;
    }

    private Object cancel(Http.Req q) {
        Map<String, Object> o = ownOrder(q);
        OrderStatus status = OrderStatus.parse((String) o.get("status"));
        if (!status.isCancellableByCustomer()) throw new ApiException.BadRequest("This order is already being prepared and cannot be cancelled");
        ctx.orders.changeStatus(i(o.get("id")), OrderStatus.CANCELLED, PaymentMethod.of((String) o.get("payment_method")));
        ctx.ordersChanged();
        return ctx.orderDetail(i(o.get("id")));
    }

    private Object review(Http.Req q) {
        Map<String, Object> o = ownOrder(q);
        if (OrderStatus.parse((String) o.get("status")) != OrderStatus.DELIVERED) throw new ApiException.BadRequest("You can rate an order after it is delivered");
        Double rating = q.num("rating");
        if (rating == null || rating != Math.rint(rating) || rating < 1 || rating > 5) throw new ApiException.BadRequest("Rating must be 1 to 5 stars");
        if (ctx.orders.hasReview(i(o.get("id")))) throw new ApiException.Conflict("You have already rated this order");
        String comment = q.str("comment");
        ctx.orders.addReview(i(o.get("id")), q.user.getId(), i(o.get("restaurant_id")), rating.intValue(), comment == null || comment.isEmpty() ? null : comment);
        q.status(201);
        return ctx.orderDetail(i(o.get("id")));
    }
}
