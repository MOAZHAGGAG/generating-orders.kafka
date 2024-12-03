package io.conduktor.demos;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

public class OrProducer {

    private static final String KAFKA_BROKER = "localhost:9092";
    private static final String TOPIC = "orders";
    private static final Logger logger = LoggerFactory.getLogger(OrProducer.class);

    private static Connection getDbConnection() throws Exception {
        String url = "jdbc:postgresql://ah-data-lab.duckdns.org:35432/moaz_db";
        String user = "moaz";
        String password = "moaz";
        return DriverManager.getConnection(url, user, password);
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BROKER);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("acks", "all");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        try (Connection conn = getDbConnection(); Statement stmt = conn.createStatement()) {
            List<Customer> customers = new ArrayList<>();
            List<Product> products = new ArrayList<>();

            // Fetch customers
            try (ResultSet customersResultSet = stmt.executeQuery("SELECT * FROM customers")) {
                while (customersResultSet.next()) {
                    String id = customersResultSet.getString("customer_unique_id");
                    String name = customersResultSet.getString("customer_name");
                    String phone = customersResultSet.getString("customer_phone");
                    String country = customersResultSet.getString("customer_country");

                    if (id == null || name == null || phone == null || country == null) {
                        logger.error("Null values detected in customers table. Skipping this record.");
                        continue;
                    }

                    customers.add(new Customer(id, name, phone, country));
                }
            }
            if (customers.isEmpty()) {
                throw new IllegalStateException("No customers found in the database.");
            }

// Fetch products
            try (ResultSet productsResultSet = stmt.executeQuery("SELECT * FROM productstess")) {
                while (productsResultSet.next()) {
                    String idString = productsResultSet.getString("id");
                    String name = productsResultSet.getString("name");
                    String oldPriceString = productsResultSet.getString("old_price");
                    String newPriceString = productsResultSet.getString("new_price");

                    if (idString == null || name == null || oldPriceString == null || newPriceString == null) {
                        logger.error("Null values detected in products table. Skipping this record.");
                        continue;
                    }

                    int id = Integer.parseInt(idString);
                    double oldPrice = Double.parseDouble(oldPriceString.replace(",", ""));
                    double newPrice = Double.parseDouble(newPriceString.replace(",", ""));

                    products.add(new Product(id, name, oldPrice, newPrice));
                }
            }
            if (products.isEmpty()) {
                throw new IllegalStateException("No products found in the database.");
            }


            Random random = new Random();
            ObjectMapper objectMapper = new ObjectMapper();

            // Produce 2 orders every 10 seconds
            while (true) {
                for (int i = 0; i < 2; i++) {
                    Customer customer = customers.get(random.nextInt(customers.size()));
                    Product product = products.get(random.nextInt(products.size()));

                    int productQuantity = random.nextInt(10) + 1;
                    double totalPrice = product.getNewPrice() * productQuantity;
                    double shippingPrice = 20;
                    double totalPriceWithShipping = totalPrice + shippingPrice;

                    String orderId = UUID.randomUUID().toString();

                    Order order = new Order(orderId, customer.getCustomerUniqueId(), customer.getCustomerName(),
                            customer.getCustomerPhone(), customer.getCustomerCountry(), product.getProductId(),
                            product.getProductName(), product.getOldPrice(), product.getNewPrice(), productQuantity,
                            totalPrice, shippingPrice, totalPriceWithShipping);

                    // Send to Kafka
                    String orderMessage = objectMapper.writeValueAsString(order);
                    producer.send(new ProducerRecord<>(TOPIC, orderId, orderMessage), (metadata, exception) -> {
                        if (exception != null) {
                            logger.error("Error sending message to Kafka", exception);
                        } else {
                            logger.info("Message sent successfully: " + orderMessage);
                        }
                    });

                    System.out.println("Order Produced: " + orderMessage);
                }
                Thread.sleep(10_000);
            }
        } catch (Exception e) {
            logger.error("Error in producer", e);
        } finally {
            producer.close();
        }
    }
}
