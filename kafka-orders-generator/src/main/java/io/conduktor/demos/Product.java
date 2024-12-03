package io.conduktor.demos;

import java.io.Serializable;

public class Product implements Serializable {
    private int productId;
    private String productName;
    private double oldPrice;
    private double newPrice;

    public Product(int productId, String productName, double oldPrice, double newPrice) {
        this.productId = productId;
        this.productName = productName;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
    }

    public int getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public double getOldPrice() {
        return oldPrice;
    }

    public double getNewPrice() {
        return newPrice;
    }
}
