package com.documind;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class DocumentExtractor {
    private final int maxPages;
    private final int maxCharacters;
    DocumentExtractor(@Value("${documind.max-pages}") int maxPages,
                      @Value("${documind.max-characters}") int maxCharacters) {
        this.maxPages = maxPages; this.maxCharacters = maxCharacters;
    }
    String extract(MultipartFile file) {
        if (file.isEmpty()) throw bad("Choose a nonempty PDF or TXT file.");
        if (file.getSize() > 10L * 1024 * 1024) throw bad("The upload limit is 10 MB.");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            String text;
            if (name.endsWith(".pdf")) {
                try (var buffer = new RandomAccessReadBuffer(file.getInputStream()); var pdf = Loader.loadPDF(buffer)) {
                    if (pdf.isEncrypted()) throw bad("Encrypted PDFs are not supported. Upload an unlocked copy.");
                    if (pdf.getNumberOfPages() > maxPages) throw bad("This release accepts up to " + maxPages + " PDF pages. Split the document into chapters.");
                    StringBuilder out = new StringBuilder();
                    PDFTextStripper stripper = new PDFTextStripper();
                    Writer bounded = new Writer() {
                        public void write(char[] c, int start, int length) {
                            if (out.length() + length > maxCharacters) throw bad("The document exceeds " + maxCharacters + " extracted characters. Split it into chapters.");
                            out.append(c, start, length);
                        }
                        public void flush() {} public void close() {}
                    };
                    stripper.writeText(pdf, bounded);
                    text = out.toString();
                }
            } else if (name.endsWith(".txt")) {
                text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes())).toString();
            } else throw bad("Only text PDFs and UTF-8 TXT files are supported in this release.");
            text = text.replace('\u0000', ' ').strip();
            if (text.length() > maxCharacters) throw bad("The document exceeds " + maxCharacters + " extracted characters. Split it into chapters.");
            if (text.length() < 300) throw bad("At least 300 readable characters are needed. Scanned PDFs require OCR and are not supported yet.");
            return text;
        } catch (ApiException e) { throw e; }
        catch (IOException e) { throw bad("The document cannot be read. Use a valid, unlocked text PDF or UTF-8 TXT file."); }
    }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
