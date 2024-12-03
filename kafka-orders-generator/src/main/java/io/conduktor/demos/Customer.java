package io.conduktor.demos;

import java.io.Serializable;

public class Customer implements Serializable {
    private String customerUniqueId;
    private String customerName;
    private String customerPhone;
    private String customerCountry;

    public Customer(String customerUniqueId, String customerName, String customerPhone, String customerCountry) {
        this.customerUniqueId = customerUniqueId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerCountry = customerCountry;
    }

    public String getCustomerUniqueId() {
        return customerUniqueId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCustomerCountry() {
        return customerCountry;
    }
}
