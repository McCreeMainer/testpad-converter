package spbpu.md.converter;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

/**
 * Converter for formatting exported CSV-files into TXT-files.
 */
public class Converter {

    private final Path _originalPath;
    private final Path _resultPath;
    private final boolean _flatten;
    private final boolean _pathLine;
    private final boolean _indexFile;
    private final PrefixConstructor prefixCtor;
    private final ErrorReporter errorReporter = new ErrorReporter();

    /**
     * Constructor for converting input object (folder or <i>csv</i> file).
     *
     * @param originalPath path to the object to be converted
     * @param resultPath   path to result folder (./result by default)
     * @param flatten      ignoring indexing files in result folder in one layer
     * @param pathLine     ignoring generation of index file
     * @param indexFile    ignoring adding relative path in first line
     * @throws IOException if it was not possible to create a folder for the result
     */
    public Converter(String originalPath, String resultPath, boolean flatten, boolean pathLine, boolean indexFile) throws IOException {
        _originalPath = Paths.get(originalPath).toAbsolutePath();
        _resultPath = Paths.get(resultPath).toAbsolutePath();
        if (!Files.exists(_resultPath) || !Files.isDirectory(_resultPath)) Files.createDirectory(_resultPath);
        _flatten = flatten;
        _pathLine = pathLine;
        _indexFile = indexFile;
        prefixCtor = new PrefixConstructor(_originalPath);
        convert();
    }

    /**
     * Convert given file or file from {@code originalPath} to {@code resultPath}
     */
    private void convert() {

        if (!Files.exists(_originalPath)) {
            System.err.println("Wrong path");
            return;
        }

        if (Files.isDirectory(_originalPath)) {
            convertDirWithIndex(_originalPath);
            return;
        }

        try {
            if (isCsv(_originalPath)) {
                convertCsv(_originalPath);
                return;
            }
        } catch (ConvertException | IOException e) {
            errorReporter.addError(e);
            return;
        }

        System.out.println("Incorrect file extension");
    }

    /**
     * Convert all <i>csv</i> files in given folder and its subfolders and generating index file.
     *
     * @param directory {@code Path} to original directory
     */
    private void convertDirWithIndex(Path directory) {
        convertDir(directory);
        if (_flatten && _indexFile) createIndexFile();
        createErrorFile();
    }

    /**
     * Convert all <i>csv</i> files in given folder and its subfolders.
     *
     * @param directory {@code Path} to original directory
     */
    private void convertDir(Path directory) {

        try (Stream<Path> walk = Files.walk(directory)) {

            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isCsv)
                    .sorted()
                    .sorted((o1, o2) -> {
                        if (o1.getNameCount() > o2.getNameCount()) return 1;
                        else if (o1.getNameCount() < o2.getNameCount()) return -1;
                        return o1.toString().compareTo(o2.toString());
                    })
                    .collect(Collectors.toList());

            for (Path path : files) {
                try {
                    convertCsv(path);
                } catch (ConvertException e) {
                    errorReporter.addError(e);
                }
            }
        } catch (IOException e) {
            errorReporter.addError(e);
        }
    }

    /**
     * Convert given <i>csv</i> file to <i>txt</i> file.
     *
     * @param original {@code Path} to original file
     * @throws ConvertException if the line being converted has syntax errors
     */
    private void convertCsv(Path original) throws ConvertException, IOException {

        int lineIndex = 1;
        String line = "";

        // Init file reader
        try (BufferedReader csvReader = Files.newBufferedReader(original, Charset.forName("Cp866"))) {

            // Path to result file
            Path result;
            Path relative = _originalPath.relativize(original.getParent());
            if (_flatten) {
                result = _resultPath.resolve(Paths.get(prefixCtor.getPrefix(original) + ".txt"));
            } else {
                Path folder = _resultPath.resolve(_originalPath.relativize(original.getParent()));
                Files.createDirectories(folder);
                result = _resultPath
                        .resolve(_originalPath.relativize(original.getParent()))
                        .resolve(Paths.get(FilenameUtils.getBaseName(original.getFileName().toString()) + ".txt"));
            }

            // Result file writer
            try (BufferedWriter txtWriter = Files.newBufferedWriter(result, Charset.forName("Cp866"))) {

                boolean skipFlag = true;
                line = csvReader.readLine();
                Pattern dataStart = Pattern.compile("^(.*,)*indent,(.*,)*text,?(.*,?)*$");
                int indentIndex = -1;
                int textIndex = -1;
                int parsedLineSize = -1;

                // Write Path line
                if (_pathLine) txtWriter.write(relative.toString() + '\n');

                while (line != null) {

                    if (skipFlag) {

                        // Looking for start line of last section with required data
                        // Then indexing indent & text values
                        if (dataStart.matcher(line).matches()) {
                            skipFlag = false;
                            List<String> parsedLine = Arrays.asList(line.split(","));
                            indentIndex = parsedLine.indexOf("indent");
                            textIndex = parsedLine.indexOf("text");
                            parsedLineSize = parsedLine.size();
                        }
                        lineIndex++;
                    } else {
                        String tabs;
                        String text;
                        List<String> parsedLine = parseLine(line);
                        int lineCount = 1;

                        // Collect all lines with entry
                        while (parsedLine.size() != parsedLineSize) {
                            lineCount++;
                            line += '\n' + csvReader.readLine();
                            parsedLine = parseLine(line);
                        }

                        // Nesting level
                        tabs = tabs(Integer.parseInt(parsedLine.get(indentIndex)));

                        // Text value
                        text = parsedLine.get(textIndex);

                        // Write to result file
                        txtWriter.write(tabs + text + '\n');

                        lineIndex += lineCount;
                    }

                    // Next line
                    line = csvReader.readLine();
                }
            }
        } catch (NullPointerException | NumberFormatException | IndexOutOfBoundsException e) {
            throw new ConvertException(original, lineIndex, line);
        } catch (FileNotFoundException e) {
            System.out.println("Wrong path " + _originalPath);
        }
    }

    /**
     * Splits String with {@literal ,} delimiter with a quote (delimiter is ignored inside quotes).
     *
     * @param line string to process
     * @return separated line
     */
    private List<String> parseLine(String line) {

        ArrayList<String> result = new ArrayList<>();
        ArrayList<Integer> comas = new ArrayList<>();
        ArrayList<Integer> quotes = new ArrayList<>();

        // Indexing delimiters and quotes
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ',') comas.add(i);
            else if (line.charAt(i) == '"') quotes.add(i);
        }

        if (comas.size() == 0) {
            result.add(line);
            return result;
        }

        if (quotes.size() % 2 == 1) {
            return Collections.emptyList();
        }

        // Removing delimiters inside quote pairs
        int temp = 0;

        for (int i = 0; i < quotes.size() - 1; i += 2) {
            int quote1 = quotes.get(i);
            int quote2 = quotes.get(i + 1);

            for (int j = temp; j < comas.size(); j++) {
                temp = j;
                if (comas.get(j) > quote1) break;
            }

            int comaIndex = comas.get(temp);

            while (comaIndex > quote1 && comaIndex < quote2) {
                comas.remove(temp);
                if (temp >= comas.size()) break;
                comaIndex = comas.get(temp);
            }
        }

        // Splitting with remaining delimiters
        int j = 0;
        for (int i : comas) {
            result.add(line.substring(j, i));
            j = i + 1;
        }
        result.add(line.substring(j));

        return result;
    }

    /**
     * Generating {@code String} filled with tab symbols.
     * @param n length of result string
     * @return {@code String} of tabs
     */
    private String tabs(int n) {
        if (n <= 0) return "";
        return "\t".repeat(n - 1);
    }

    /**
     * Checks if the file format is <i>csv</i>.
     *
     * @param path {@code Path} to the file to check
     * @return has file <i>csv</i> extension
     */
    private boolean isCsv(Path path) {
        return FilenameUtils.getExtension(path.getFileName().toString()).equals("csv");
    }

    /**
     * Generating index file with next format:
     * <pre>
     *     ...
     *     001.002  relative/path/in/original/location
     *     ...
     * </pre>
     */
    private void createIndexFile() {
        try (BufferedWriter writer = Files.newBufferedWriter(
                _resultPath.resolve("index.txt"),
                Charset.forName("Cp866"))
        ) {
            writer.write(prefixCtor.getIndexPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generating file with error messages.
     */
    private void createErrorFile() {
        try (BufferedWriter writer = Files.newBufferedWriter(
                _resultPath.resolve("errors.txt"),
                Charset.forName("Cp866"))
        ) {
            writer.write(errorReporter.getReport());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
