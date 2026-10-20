package com.documind;

import java.util.List;

interface QuizGenerator {
    List<Question> generate(String text);
}
