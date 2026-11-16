package shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
public class ShopApplication {

    private final JdbcTemplate jdbc;

    public ShopApplication(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }

    @GetMapping("/orders/{customer}")
    public List<Map<String, Object>> orders(@PathVariable String customer) {
        return jdbc.queryForList("SELECT id, item, price FROM orders WHERE customer = ? ORDER BY id", customer);
    }

    @GetMapping("/report")
    public Map<String, Object> report() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE price > ?", Integer.class, 0);
        Integer total = jdbc.queryForObject("SELECT SUM(price) FROM orders WHERE price > ?", Integer.class, 0);
        jdbc.update("UPDATE stats SET reports = reports + ?", 1);
        return Map.of("orders", count, "revenue", total);
    }
}
