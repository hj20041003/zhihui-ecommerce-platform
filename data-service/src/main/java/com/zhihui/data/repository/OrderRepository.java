package com.zhihui.data.repository;

import com.zhihui.data.entity.SalesOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<SalesOrder, Long> {

    List<SalesOrder> findTop30ByOrderByCreatedAtDesc();
}
