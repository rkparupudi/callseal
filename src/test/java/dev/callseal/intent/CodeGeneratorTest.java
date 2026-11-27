package dev.callseal.intent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator(List.of("TIGER", "MANGO", "RIVER", "LOTUS"), "pepper");

    @Test
    void codeIsTwoDifferentWords() {
        for (int n = 0; n < 200; n++) {
            String code = generator.generate();
            assertThat(code).matches("[A-Z]+-[A-Z]+");
            String[] parts = code.split("-");
            assertThat(parts[0]).isNotEqualTo(parts[1]);
        }
    }

    @Test
    void hashIsStableCaseInsensitiveAndPeppered() {
        assertThat(generator.hash("tiger-mango")).isEqualTo(generator.hash("TIGER-MANGO"));
        assertThat(generator.hash("TIGER-MANGO")).isNotEqualTo(generator.hash("TIGER-RIVER"));
        CodeGenerator otherPepper = new CodeGenerator(List.of("A", "B"), "different");
        assertThat(otherPepper.hash("TIGER-MANGO")).isNotEqualTo(generator.hash("TIGER-MANGO"));
    }

    @Test
    void rejectsTinyWordList() {
        assertThatThrownBy(() -> new CodeGenerator(List.of("ONLY"), "p")).isInstanceOf(IllegalStateException.class);
    }
}
