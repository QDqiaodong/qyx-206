package com.example.app.controller;

import com.example.app.dto.LoanCheckoutDTO;
import com.example.app.dto.LoanReturnDTO;
import com.example.app.dto.LoanVO;
import com.example.app.entity.EquipmentLoan;
import com.example.app.service.LoanService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/loan")
@Slf4j
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * 班长登记器材外借离场。
     * 缺项 / 越权 / 在途未还 / 被未结束课目占用时返回 400。
     */
    @PostMapping("/checkout")
    public ResponseEntity<EquipmentLoan> checkout(@Valid @RequestBody LoanCheckoutDTO dto) {
        return ResponseEntity.ok(loanService.checkout(dto));
    }

    /**
     * 器材回场归还，闭环离场单。
     */
    @PutMapping("/{id}/return")
    public ResponseEntity<EquipmentLoan> returnLoan(@PathVariable Long id,
                                                    @RequestBody(required = false) LoanReturnDTO dto) {
        return ResponseEntity.ok(loanService.returnLoan(id, dto));
    }

    /**
     * 离场列表。
     * tab: OPEN 在外（默认，含超期）/ OVERDUE 仅超期 / RETURNED 已归还 / ALL 全部
     */
    @GetMapping
    public ResponseEntity<Page<LoanVO>> list(
            @RequestParam(defaultValue = "OPEN") String tab,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(loanService.list(tab, teamId, equipmentId, pageable));
    }

    /**
     * 对账统计：在外 / 超期 / 已归还件数。
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(loanService.summary());
    }

    /**
     * 手动触发一次超期扫描（正常由定时任务每分钟执行）。
     */
    @PostMapping("/sweep-overdue")
    public ResponseEntity<Map<String, Object>> sweepOverdue() {
        int marked = loanService.markOverdueLoans();
        return ResponseEntity.ok(Map.of("markedOverdue", marked));
    }
}
