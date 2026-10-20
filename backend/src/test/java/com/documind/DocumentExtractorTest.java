package com.documind;

import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class DocumentExtractorTest {
    DocumentExtractor extractor = new DocumentExtractor(100,60000);
    @Test void extractsPDFAndRejectsImageOnlyPDF() throws Exception {
        byte[] readable;
        try(var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); document.addPage(page);
            try(var stream = new PDPageContentStream(document,page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12); stream.newLineAtOffset(30,700);
                for(int i=0;i<10;i++) { stream.showText("A database primary key uniquely identifies each row in a table."); stream.newLineAtOffset(0,-20); }
                stream.endText();
            }
            document.save(output); readable = output.toByteArray();
        }
        assertThat(extractor.extract(new MockMultipartFile("file","notes.pdf","application/pdf",readable))).contains("primary key");
        try(var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output);
            assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file","scan.pdf","application/pdf",output.toByteArray())))
                .isInstanceOf(ApiException.class).hasMessageContaining("readable characters");
        }
    }
    @Test void rejectsTooManyPagesAndCharacters() throws Exception {
        try(var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for(int i=0;i<101;i++) document.addPage(new PDPage()); document.save(output);
            assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file","large.pdf","application/pdf",output.toByteArray())))
                .hasMessageContaining("100 PDF pages");
        }
        assertThatThrownBy(() -> extractor.extract(new MockMultipartFile("file","large.txt","text/plain","a".repeat(60001).getBytes())))
            .hasMessageContaining("60000 extracted characters");
    }
    @Test void rejectsFabricatedEvidenceAndDuplicateOptions() {
        String excerpt = "Primary keys uniquely identify rows.";
        var qs = new ArrayList<Question>();
        for(int i=0;i<10;i++) qs.add(new Question("Question "+i, List.of("One","Two","Three","Four"),0,"Explanation",excerpt));
        GeminiQuizGenerator.validate(qs,excerpt);
        assertThatThrownBy(() -> GeminiQuizGenerator.validate(qs,"Unrelated content" )).isInstanceOf(IllegalStateException.class);
        qs.set(0,new Question("Question",List.of("Same","same","Three","Four"),0,"Explanation",excerpt));
        assertThatThrownBy(() -> GeminiQuizGenerator.validate(qs,excerpt)).isInstanceOf(IllegalStateException.class);
    }
    @Test void acceptsEquivalentPdfUnicodeSpacingAndLigatures() {
        String source = "An o\ufb03ce supports\u00a0civil\u202fadministration and public service.";
        String excerpt = "An office supports civil administration and public service.";
        var questions = new ArrayList<Question>();
        for (int i = 0; i < 10; i++) questions.add(new Question("Question " + i,
            List.of("One", "Two", "Three", "Four"), 0, "Explanation", excerpt));
        assertThatCode(() -> GeminiQuizGenerator.validate(questions, source)).doesNotThrowAnyException();
        questions.set(0, new Question("Question zero", List.of("One", "Two", "Three", "Four"),
            0, "Explanation", "An office opposes civil administration and public service."));
        assertThatThrownBy(() -> GeminiQuizGenerator.validate(questions, source))
            .hasMessageContaining("Question 1 failed validation")
            .hasMessageContaining("supporting excerpt could not be matched");
    }
    @Test void rejectsOptionsThatOnlyDifferByUnicodeSpacing() {
        String source = "Primary keys uniquely identify rows.";
        var questions = new ArrayList<Question>();
        for (int i = 0; i < 10; i++) questions.add(new Question("Question " + i,
            List.of("First choice", "First\u00a0choice", "Third", "Fourth"), 0, "Explanation", source));
        assertThatThrownBy(() -> GeminiQuizGenerator.validate(questions, source))
            .hasMessageContaining("answer options are duplicates");
    }
    @Test void rejectsInvisibleExcerptsAfterNormalization() {
        String source = "Primary keys uniquely identify rows.";
        var questions = new ArrayList<Question>();
        for (int i = 0; i < 10; i++) questions.add(new Question("Question " + i,
            List.of("One", "Two", "Three", "Four"), 0, "Explanation", "\u00ad".repeat(20)));
        assertThatThrownBy(() -> GeminiQuizGenerator.validate(questions, source))
            .hasMessageContaining("supporting excerpt could not be matched");
    }
}
