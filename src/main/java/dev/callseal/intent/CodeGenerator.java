package dev.callseal.intent;

import dev.callseal.common.Hashing;
import dev.callseal.config.CallSealProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * Two-word spoken codes, e.g. TIGER-MANGO.
 *
 * The bundled wordlist.txt is a short starter list (64 words = 4,096 pairs). The design targets
 * 2,048 words (about 4.2 million pairs): drop in a larger list of short, easy-to-say words,
 * checking its licence first.
 */
@Component
public class CodeGenerator {

    private final SecureRandom random = new SecureRandom();
    private final List<String> words;
    private final String pepper;

    @Autowired // two constructors: tell Spring which one to use
    public CodeGenerator(CallSealProperties props) {
        this(loadWords(), props.codePepper());
    }

    CodeGenerator(List<String> words, String pepper) {
        if (words.size() < 2) {
            throw new IllegalStateException("Word list needs at least 2 words");
        }
        this.words = List.copyOf(words);
        this.pepper = pepper;
    }

    public String generate() {
        String first = words.get(random.nextInt(words.size()));
        String second;
        do {
            second = words.get(random.nextInt(words.size()));
        } while (second.equals(first));
        return first + "-" + second;
    }

    /** HMAC with a server pepper: a leaked Redis dump alone can't brute-force the small code space. */
    public String hash(String code) {
        return Hashing.hmacSha256Hex(pepper, code.toUpperCase(Locale.ROOT));
    }

    public int wordCount() {
        return words.size();
    }

    private static List<String> loadWords() {
        try (InputStream in = new ClassPathResource("wordlist.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(w -> !w.isEmpty() && !w.startsWith("#"))
                    .map(w -> w.toUpperCase(Locale.ROOT))
                    .distinct()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read wordlist.txt", e);
        }
    }
}
