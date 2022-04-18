package spbpu.md.converter;

import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Constructor for generation and accounting names of result files.
 * Names are generated based on their relative path in the source folder.
 * Each number is index inside its parent.
 * <pre>
 *     {@code <root>}
 *      |
 *      |___001
 *          |
 *          |___001
 *              002
 *              |
 *              |___file.csv
 * </pre>
 */
public class PrefixConstructor {

    private final Path _root;
    private final Folder rootFolder;
    private final static String delimiter = ".";
    private final static int padSize = 3;
    private final static char padChar = '0';

    /**
     * Ctor
     * @param root {@code Path} of root of processed folder
     */
    public PrefixConstructor(Path root) {
        _root = root;
        rootFolder = new Folder(root, 0);
    }

    /**
     * Generating name of file based on relative path.
     * @param current {@code Path} to processed file
     * @return indexed name for result file
     */
    public String getPrefix(Path current) {

        Path relativePath = _root.relativize(current.getParent());
        StringJoiner prefix = new StringJoiner(delimiter);
        Folder folder = rootFolder;

        for (Path path : relativePath) {

            if (path.toString().equals("")) break;

            Folder nextFolder = IterableUtils.find(
                    folder.folders,
                    object -> path.equals(object._path)
            );

            if (nextFolder == null) {
                nextFolder = new Folder(relativePath, folder.getInnerIndex());
                folder.addSubFolder(nextFolder);
            }

            folder = nextFolder;

            prefix.add(StringUtils.leftPad(folder.getIndex().toString(), padSize, padChar));
        }

        prefix.add(StringUtils.leftPad(folder.getInnerIndex().toString(), padSize, padChar));

        return prefix.toString();
    }

    /**
     * Generating info about indexed paths.
     * Each line contain relative path of folder and its code/index.
     * @return {@code String} with info about folders and theirs indices
     */
    public String getIndexPath() {
        Map<Path, List<Integer>> map = rootFolder.getIndexPath(Collections.emptyList());
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry ->
                        entry.getValue().stream()
                                .map(i -> StringUtils.leftPad(i.toString(), padSize, padChar))
                                .collect(Collectors.joining(delimiter)) +
                                '\t' +
                                entry.getKey().toString())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Container for indexing objects inside folder.
     */
    private class Folder {
        private final Path _path;
        private final Integer _index;
        private Integer _innerIndex = 0;
        private final List<Folder> folders = new LinkedList<>();

        /**
         * Ctor
         * @param path {@code Path} of folder
         * @param index index relative to folder parent
         */
        public Folder(Path path, int index) {
            _path = path;
            _index = index;
        }

        /**
         * Generating map of {@code Path} to index of subfolders.
         * @param prevIndices prefix with index of parent
         * @return Mapped info about paths of all subfolders and theirs indices
         */
        public Map<Path, List<Integer>> getIndexPath(List<Integer> prevIndices) {
            List<Integer> indices = new LinkedList<>(prevIndices);
            Map<Path, List<Integer>> indexedPaths = folders.stream()
                    .collect(Collectors.toMap(
                            Folder::getPath,
                            f -> ListUtils.union(indices, Collections.singletonList(f.getIndex()))
                    ));
            indices.add(_index);
            folders.stream()
                    .map(f -> f.getIndexPath(indices))
                    .forEach(indexedPaths::putAll);
            return indexedPaths;
        }

        public Path getPath() {
            return _path;
        }

        public Integer getIndex() {
            return _index;
        }

        public Integer getInnerIndex() {
            return _innerIndex++;
        }

        public void addSubFolder(Folder subfolder) {
            folders.add(subfolder);
        }
    }
}
