package spbpu.md.converter;

import org.apache.commons.cli.*;

import java.io.IOException;

public class Main {

    /**
     * Entry point of converter.
     *
     * @param args available options:
     *             <ul>
     *                 <li>-p,--path [arg]      - Path to the object to be converted</li>
     *                 <li>-r,--result [arg]    - Path to result folder (./result by default)</li>
     *                 <li>-nf,--no-flatten     - Ignoring indexing files in result folder in one layer</li>
     *                 <li>-ni,--no-index-file  - Ignoring generation of index file</li>
     *                 <li>-np,--no-path-line   - Ignoring adding relative path in first line</li>
     *             </ul>
     */
    public static void main(String[] args) {

        Options helpOptions = new Options();
        helpOptions.addOption(
                Option.builder("h")
                        .required(false)
                        .longOpt("help")
                        .desc("Show available options")
                        .build()
        );

        Options converterOptions = new Options();
        converterOptions.addOption(
                Option.builder("p")
                        .required(true)
                        .hasArg()
                        .longOpt("path")
                        .desc("Path to the object to be converted")
                        .build()
        );
        converterOptions.addOption(
                Option.builder("r")
                        .required(false)
                        .hasArg()
                        .longOpt("result")
                        .desc("Path to result folder (./result by default)")
                        .build()
        );
        converterOptions.addOption(
                Option.builder("nf")
                        .required(false)
                        .longOpt("no-flatten")
                        .desc("Ignoring indexing files in result folder in one layer")
                        .build()
        );
        converterOptions.addOption(
                Option.builder("np")
                        .required(false)
                        .longOpt("no-path-line")
                        .desc("Ignoring adding relative path in first line")
                        .build()
        );
        converterOptions.addOption(
                Option.builder("ni")
                        .required(false)
                        .longOpt("no-index-file")
                        .desc("Ignoring generation of index file")
                        .build()
        );

        try {

            CommandLineParser parser = new DefaultParser();

            CommandLine helpLine = parser.parse(helpOptions, args, true);

            if (helpLine.hasOption("h")) {
                new HelpFormatter().printHelp("Testpad Converter", converterOptions);
                System.exit(0);
            }

            CommandLine converterLine = parser.parse(converterOptions, args);

            new Converter(
                    converterLine.getOptionValue("p"),
                    converterLine.hasOption("r") ? converterLine.getOptionValue("r") : "result",
                    !converterLine.hasOption("nf"),
                    !converterLine.hasOption("np"),
                    !converterLine.hasOption("ni")
            );
        } catch (ParseException | IOException exp) {
            System.err.println(exp.getMessage());
            System.exit(1);
        }
    }
}
