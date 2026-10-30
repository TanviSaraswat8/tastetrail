package com.tastetrail.explore;

import com.tastetrail.model.Bill;
import com.tastetrail.model.CartItem;
import com.tastetrail.service.BillCalculator;

import java.util.*;

/**
 * FAIR BILL SPLIT for group orders.
 * Splitting equally is unfair when one friend orders a ₹379 biryani and another a ₹49 chai.
 * Here every dish is assigned to the people who share it; each person pays for their dishes, and the
 * shared charges (GST, packaging, delivery, minus any coupon) are divided in proportion to food share.
 * Amounts are rounded to paise and the rounding difference goes to the last person, so the parts always
 * add up exactly to the bill.
 */
public class BillSplitter {

    public record Share(String person, double food, double extras, double pay, List<String> items) {}

    /** OVERLOADED: equal split. */
    public List<Share> split(Bill bill, List<String> people) {
        int n = people.size();
        if (n < 1) throw new IllegalArgumentException("Add at least one person");
        double each = BillCalculator.round2(bill.getTotal() / n);
        List<Share> out = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double pay = i == n - 1 ? BillCalculator.round2(bill.getTotal() - sum) : each;
            sum += pay;
            out.add(new Share(people.get(i), BillCalculator.round2(bill.getSubtotal() / n), BillCalculator.round2(pay - bill.getSubtotal() / n), pay, List.of("Equal share")));
        }
        return out;
    }

    /**
     * OVERLOADED: split by who ate what.
     * @param assignment menu item id -> indexes of the people sharing it (missing = shared by everyone)
     */
    public List<Share> split(Bill bill, List<CartItem> lines, List<String> people, Map<Integer, List<Integer>> assignment) {
        int n = people.size();
        if (n < 1) throw new IllegalArgumentException("Add at least one person");
        double[] food = new double[n];
        List<List<String>> items = new ArrayList<>();
        for (int i = 0; i < n; i++) items.add(new ArrayList<>());
        for (CartItem l : lines) {
            List<Integer> who = assignment.getOrDefault(l.getMenuItemId(), List.of()).stream().filter(p -> p >= 0 && p < n).distinct().toList();
            if (who.isEmpty()) { who = new ArrayList<>(); for (int i = 0; i < n; i++) who.add(i); }
            double part = l.getLineTotal() / who.size();
            for (int p : who) {
                food[p] += part;
                items.get(p).add(who.size() == 1 ? l.getQuantity() + " × " + l.getName() : l.getName() + " (shared ÷" + who.size() + ")");
            }
        }
        double subtotal = bill.getSubtotal();
        double extrasTotal = bill.getTotal() - subtotal;  // GST + packaging + delivery − discount
        List<Share> out = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double ratio = subtotal == 0 ? 1.0 / n : food[i] / subtotal;
            double pay = i == n - 1 ? BillCalculator.round2(bill.getTotal() - sum) : BillCalculator.round2(food[i] + extrasTotal * ratio);
            sum += pay;
            out.add(new Share(people.get(i), BillCalculator.round2(food[i]), BillCalculator.round2(pay - food[i]), pay, items.get(i)));
        }
        return out;
    }
}
