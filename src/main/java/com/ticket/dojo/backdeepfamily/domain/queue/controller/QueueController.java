package com.ticket.dojo.backdeepfamily.domain.queue.controller;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    // jwt 도입 전 endPoint
    @PostMapping("/plain/enter")
    public ResponseEntity<QueueEnterResponse> plainQueueEnter(@RequestParam Long userId){
        log.info("대기열 진입 API 호출 - user : {}", userId);
        QueueEnterResponse response = queueService.enterQueue(userId);
        return ResponseEntity.ok(response);
    }

    // jwt 도입 후 endPoint
//    @PostMapping("/jwt/enter")
//    public ResponseEntity<QueueEnterResponse> jwtQueueEnter(@AuthenticationPrincipal CustomUserDetails userDetail){
//        Long userId = userDetail.getUserId();
//        log.info("대기열 진입 API 호출 - USER {}", userId);
//        QueueEnterResponse response = queueService.enterQueue(userId);
//        return ResponseEntity.ok(response);
//    }


}
