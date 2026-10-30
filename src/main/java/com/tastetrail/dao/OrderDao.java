package com.tastetrail.dao;

import com.tastetrail.db.Db;
import com.tastetrail.model.Bill;
import com.tastetrail.model.Cart;
import com.tastetrail.model.CartItem;
import com.tastetrail.model.OrderStatus;
import com.tastetrail.payment.PaymentMethod;

import java.util.Map;

/** Writes orders and changes their status. Every multi-row change runs in one transaction. */
public class OrderDao {

    /** Saves the order and all its items together: either everything is stored or nothing is. */
    public int create(int userId, Cart cart, Bill bill, PaymentMethod payment, String address) {
        return Db.tx(() -> {
            int orderId = Db.insert("""
                INSERT INTO orders (user_id, restaurant_id, subtotal, discount, delivery_fee, packaging_fee, tax, total, coupon_code,
                                    payment_method, payment_status, status, address, eta_minutes)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                userId, cart.getRestaurant().getId(), bill.getSubtotal(), bill.getDiscount(), bill.getDeliveryFee(), bill.getPackaging(),
                bill.getTax(), bill.getTotal(), bill.getCouponApplied(), payment.getCode(), payment.statusWhenPlaced(),
                OrderStatus.PLACED.name(), address, bill.getEtaMinutes());
            for (CartItem item : cart.getItems())
                Db.insert("INSERT INTO order_items (order_id, menu_item_id, name, price, quantity) VALUES (?,?,?,?,?)",
                    orderId, item.getMenuItemId(), item.getName(), item.getPrice(), item.getQuantity());
            return orderId;
        });
    }

    /** The raw order row (used to check owner, status and payment method). */
    public Map<String, Object> findRow(int orderId) { return Db.one("SELECT * FROM orders WHERE id = ?", orderId); }

    /** Moves an order to a new status and updates the payment status the payment method asks for. */
    public void changeStatus(int orderId, OrderStatus next, PaymentMethod payment) {
        switch (next) {
            case DELIVERED -> Db.update("UPDATE orders SET status = ?, payment_status = ?, delivered_at = CURRENT_TIMESTAMP WHERE id = ?",
                next.name(), payment.statusWhenDelivered(), orderId);
            case CANCELLED -> Db.update("UPDATE orders SET status = ?, payment_status = ? WHERE id = ?",
                next.name(), payment.statusWhenCancelled(), orderId);
            default -> Db.update("UPDATE orders SET status = ? WHERE id = ?", next.name(), orderId);
        }
    }

    public boolean hasReview(int orderId) { return Db.one("SELECT id FROM reviews WHERE order_id = ?", orderId) != null; }

    public void addReview(int orderId, int userId, int restaurantId, int rating, String comment) {
        Db.insert("INSERT INTO reviews (order_id, user_id, restaurant_id, rating, comment) VALUES (?,?,?,?,?)", orderId, userId, restaurantId, rating, comment);
    }
}
