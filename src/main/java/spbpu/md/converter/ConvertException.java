package spbpu.md.converter;

import java.nio.file.Path;

public class ConvertException extends Exception {
    public ConvertException(Path path, int lineIndex, String line) {
        super("Syntax error at line " + lineIndex + " in file " + path.toString() + "\n" + line);
    }
}
