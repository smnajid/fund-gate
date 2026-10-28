package com.fundgate.answering.web;

import com.fundgate.answering.AnswerRequests;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The dealer screen's paste step: one Requester's email in, its Verdicts and reply out (screens.md, Dealer screen). */
@RestController
@Profile("!pipeline")
@RequestMapping("/api/answers")
public class AnswerController {

    private final AnswerRequests answerRequests;

    public AnswerController(AnswerRequests answerRequests) {
        this.answerRequests = answerRequests;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public AnswerResponse answer(@RequestBody EmailToAnswer email) {
        return AnswerResponse.of(answerRequests.answer(email.email()));
    }
}
