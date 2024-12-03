package io.conduktor.demos;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

public class OrConsumer {

    private static final String KAFKA_BROKER = "localhost:9092";
    private static final String TOPIC = "orders";
    private static final Logger logger = LoggerFactory.getLogger(OrConsumer.class);

    private static Connection getDbConnection() throws SQLException {
        String url = "jdbc:postgresql://ah-data-lab.duckdns.org:35432/moaz_db";
        String user = "moaz";
        String password = "moaz";
        return DriverManager.getConnection(url, user, password);
    }

    private static void insertOrder(Connection conn, Order order) throws SQLException {
        String sql = "INSERT INTO orders (order_id, customer_unique_id, customer_name, customer_phone, customer_country, product_id, " +
                "product_name, old_price, new_price, product_quantity, total_price, shipping_price, total_price_with_shipping) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(order.orderId));
            stmt.setString(2, order.customerUniqueId);
            stmt.setString(3, order.customerName);
            stmt.setString(4, order.customerPhone);
            stmt.setString(5, order.customerCountry);
            stmt.setInt(6, order.productId);
            stmt.setString(7, order.productName);
            stmt.setDouble(8, order.oldPrice);
            stmt.setDouble(9, order.newPrice);
            stmt.setInt(10, order.productQuantity);
            stmt.setDouble(11, order.totalPrice);
            stmt.setDouble(12, order.shippingPrice);
            stmt.setDouble(13, order.totalPriceWithShipping);
            stmt.executeUpdate();
        }
    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_BROKER);
        props.put("group.id", "order-consumer-group");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "latest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));

        ObjectMapper objectMapper = new ObjectMapper();

        try (Connection conn = getDbConnection()) {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    String message = record.value();
                    try {
                        Order order = objectMapper.readValue(message, Order.class);
                        insertOrder(conn, order);
                        logger.info("Order saved to database: " + order.orderId);
                    } catch (Exception e) {
                        logger.error("Failed to process record: " + message, e);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database connection error", e);
        } finally {
            consumer.close();
        }
    }
}
