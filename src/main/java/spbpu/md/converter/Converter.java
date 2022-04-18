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
    private final PrefixConstructor _prefixCtor;
    private final boolean _flatten;
    private final boolean _pathLine;
    private final boolean _indexFile;

    /**
     * Constructor for converting input object (folder or <i>csv</i> file).
     * @param originalPath path to the object to be converted
     * @param resultPath path to result folder (./result by default)
     * @param flatten ignoring indexing files in result folder in one layer
     * @param pathLine ignoring generation of index file
     * @param indexFile ignoring adding relative path in first line
     * @throws IOException if it was not possible to create a folder for the result
     */
    public Converter(String originalPath, String resultPath, boolean flatten, boolean pathLine, boolean indexFile) throws IOException {
        _originalPath = Paths.get(originalPath).toAbsolutePath();
        _resultPath = Paths.get(resultPath).toAbsolutePath();
        if (!Files.exists(_resultPath) || !Files.isDirectory(_resultPath)) Files.createDirectory(_resultPath);
        _flatten = flatten;
        _pathLine = pathLine;
        _indexFile = indexFile;
        _prefixCtor = new PrefixConstructor(_originalPath);
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

        try {
            if (Files.isDirectory(_originalPath)) {
                convertDirWithIndex(_originalPath);
                return;
            }

            if (isCsv(_originalPath)) {
                convertCsv(_originalPath);
                return;
            }
        } catch (ConvertException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("Incorrect file extension");
    }

    /**
     * Convert all <i>csv</i> files in given folder and its subfolders and generating index file.
     * @param directory {@code Path} to original directory
     * @throws ConvertException if any file being converted has syntax error
     */
    private void convertDirWithIndex(Path directory) throws ConvertException {
        convertDir(directory);
        if (_flatten && _indexFile) createIndexFile();
    }

    /**
     * Convert all <i>csv</i> files in given folder and its subfolders.
     * @param directory {@code Path} to original directory
     * @throws ConvertException if any file being converted has syntax error
     */
    private void convertDir(Path directory) throws ConvertException {

        try (Stream<Path> walk = Files.walk(directory)) {

            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isCsv)
                    .sorted()
                    .collect(Collectors.toList());

            for (Path path : files) {
                convertCsv(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert given <i>csv</i> file to <i>txt</i> file.
     * @param original {@code Path} to original file
     * @throws ConvertException if the line being converted has syntax errors
     */
    private void convertCsv(Path original) throws ConvertException {

        int lineIndex = 0;
        String line = "";

        // Init file reader
        try (BufferedReader csvReader = Files.newBufferedReader(original, Charset.forName("Cp866"))) {

            // Path to result file
            Path result;
            Path relative = _originalPath.relativize(original.getParent());
            if (_flatten) {
                result = _resultPath.resolve(Paths.get(_prefixCtor.getPrefix(original) + ".txt"));
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

                // Write Path line
                if (_pathLine) txtWriter.write(relative.toString() + '\n');

                while (line != null) {
                    lineIndex++;

                    if (skipFlag) {

                        // Looking for start line of last section with required data
                        // Then indexing indent & text values
                        if (dataStart.matcher(line).matches()) {
                            skipFlag = false;
                            List<String> parsedLine = Arrays.asList(line.split(","));
                            indentIndex = parsedLine.indexOf("indent");
                            textIndex = parsedLine.indexOf("text");
                        }
                    } else {
                        String tabs;
                        String text;
                        List<String> parsedLine = parseLine(line);

                        // Nesting level
                        tabs = tabs(Integer.parseInt(parsedLine.get(indentIndex)));

                        // Text value
                        text = parsedLine.get(textIndex);

                        // Write to result file
                        txtWriter.write(tabs + text + '\n');
                    }

                    // Next line
                    line = csvReader.readLine();
                }
            }
        } catch (NullPointerException | NumberFormatException | IndexOutOfBoundsException e) {
            throw new ConvertException(original, lineIndex, line);
        } catch (FileNotFoundException e) {
            System.out.println("Wrong path " + _originalPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Splits String with {@literal ,} delimiter with a quote (delimiter is ignored inside quotes).
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

        // Removing delimiters inside quote pairs
        int j = 0;
        for (int i = 0; i < quotes.size() - 1; i += 2) {
            int quote1 = quotes.get(i);
            int quote2 = quotes.get(i + 1);

            int comaIndex = comas.get(j);
            while (comaIndex < quote1) comaIndex = comas.get(++j);
            while (comaIndex < quote2) {
                comas.remove(j);
                comaIndex = comas.get(j);
            }
        }

        // Splitting with remaining delimiters
        j = 0;
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
            writer.write(_prefixCtor.getIndexPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
