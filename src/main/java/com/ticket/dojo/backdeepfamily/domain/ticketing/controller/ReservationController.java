package com.ticket.dojo.backdeepfamily.domain.ticketing.controller;

import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetRankingResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    //점유된 좌석 내역 가져오기 + reservation 객체 생성
    @PostMapping
    public ResponseEntity<GetHoldingSeatsResponse> enterTicketing(@RequestParam Long userId) {
        GetHoldingSeatsResponse response = reservationService.enterTicketing(userId);
        return ResponseEntity.ok(response);
    }

    //결제 진입
    @PostMapping("/{reservationId}/payment-session")
    public ResponseEntity<Void> enterPaying(@PathVariable Long reservationId) {
        reservationService.enterPaying(reservationId);
        return ResponseEntity.ok().build();
    }

    //결제완료
    @PostMapping("/{reservationId}/payment")
    public ResponseEntity<Void> completePaying(@PathVariable Long reservationId, @RequestParam Long userId) {
        reservationService.completePaying(reservationId, userId);
        return ResponseEntity.ok().build();
    }

    //랭킹 가져오기 (사용자가 진행한 round 랭킹 -> reservationId로 찾기)
    @GetMapping("/{reservationId}/rank")
    public ResponseEntity<GetRankingResponse> getRanking(@PathVariable Long reservationId) {
        GetRankingResponse response = reservationService.getRanking(reservationId);
        return ResponseEntity.ok(response);
    }

    //예약 취소 (뒤로가기, 창끄기 등)
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<Void> cancelReservation(@PathVariable Long reservationId, @RequestParam Long userId) {
        reservationService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok().build();
    }
}
