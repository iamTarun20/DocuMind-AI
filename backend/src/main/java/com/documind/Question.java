package com.documind;

import java.util.List;

// Internal representation. Correct answers are never sent with the quiz GET response.
record Question(String prompt, List<String> options, Integer correctIndex, String explanation, String sourceExcerpt) {}
