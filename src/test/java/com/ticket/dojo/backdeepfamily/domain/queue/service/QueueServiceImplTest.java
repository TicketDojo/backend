package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
class QueueServiceImplTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Test
    @DisplayName("1인 대기열 진입")
    @Transactional
    void enterQueue_One(){

        // given
        User user = User.builder()
                .email("test@naver.com")
                .name("테스트유저")
                .password("qwe123")
                .role(User.Role.USER)
                .build();

        User savedUser = userRepository.save(user);

        // when
        QueueEnterResponse response = queueService.enterQueue(savedUser.getUserId());

        /**
         * {
         *     "token": "cc40c76f-a617-41df-b628-1e1f32828f89",
         *     "position": 1,
         *     "status": "WAITING",
         *     "enteredAt": "2025-12-07T23:17:05.7168345"
         * }
         */
        // then
        Assertions.assertNotNull(response.getToken());
        Assertions.assertEquals(1, response.getPosition());
        Assertions.assertEquals(Queue.QueueStatus.WAITING, response.getStatus());
        Assertions.assertNotNull(response.getEnteredAt());
    }
}