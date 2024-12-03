package io.conduktor.demos;

import java.io.Serializable;

public class Order implements Serializable {
    public String orderId;
    public String customerUniqueId;
    public String customerName;
    public String customerPhone;
    public String customerCountry;
    public int productId;
    public String productName;
    public double oldPrice;
    public double newPrice;
    public int productQuantity;
    public double totalPrice;
    public double shippingPrice;
    public double totalPriceWithShipping;

    public Order(String orderId, String customerUniqueId, String customerName, String customerPhone, String customerCountry,
                 int productId, String productName, double oldPrice, double newPrice, int productQuantity, double totalPrice,
                 double shippingPrice, double totalPriceWithShipping) {
        this.orderId = orderId;
        this.customerUniqueId = customerUniqueId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerCountry = customerCountry;
        this.productId = productId;
        this.productName = productName;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.productQuantity = productQuantity;
        this.totalPrice = totalPrice;
        this.shippingPrice = shippingPrice;
        this.totalPriceWithShipping = totalPriceWithShipping;
    }

    // Empty constructor for Jackson or serialization/deserialization
    public Order() {
    }
}
