// Copyright 2015-2016 Stanford University
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package glade.main;


import glade.grammar.fuzz.GrammarFuzzer.GrammarMutationSampler;
import glade.grammar.fuzz.GrammarFuzzer.SampleParameters;
import glade.grammar.GrammarUtils.Grammar;
import glade.grammar.synthesize.GrammarSynthesis;
import glade.util.CharacterUtils;
import glade.util.Log;
import glade.util.OracleUtils.DiscriminativeOracle;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import glade.util.Utils;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;


@Command(name = "glade", mixinStandardHelpOptions = true, version = "1.0",
    subcommands = {Learn.class, Fuzz.class, Print.class})
public class Main implements Callable<Integer> {

    @Option(names = {"--log"}, description = "logging level")
    private Log.Level level;

    @Option(names = {"-f", "--file"}, description = "file for logging")
    private File logFile;

    private static OutputStream out;

    @Override
    public Integer call() {
        initGlade();
        Log.debug("Starting command glade");
        System.err.println("Please invoke a subcommand");
        CommandLine cmd = new CommandLine(this);
        cmd.usage(System.err);
        return cmd.getCommandSpec().exitCodeOnInvalidInput();
    }

    public void initGlade() {
        if (logFile != null) {
            try {
                Log.setOutputStream(new FileOutputStream(logFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                System.exit(new CommandLine(this).getCommandSpec()
                    .exitCodeOnExecutionException());
            }
        }
        if (level != null) {
            Log.setLoggingLevel(level);
        }
    }

    public static void main(String ... args) {
        int returnValue = new CommandLine(new Main()).execute(args);
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(new CommandLine(new Main()).getCommandSpec()
                    .exitCodeOnExecutionException());
            }
        }
        System.exit(returnValue);
    }

    /**
     * @see <a href="https://github.com/remkop/picocli/issues/832">this related issue</a>
     */
    static int[] parseAllowedLength(String allowedLength) {
        if (allowedLength == null) {
            return new int[]{};
        }
        if (!allowedLength.contains("-")) {
            return new int[]{Integer.parseInt(allowedLength)};
        }
        if (allowedLength.indexOf("-") == allowedLength.lastIndexOf("-")) {
            String[] lengthRange = allowedLength.split("-");
            int[] parsedLength = new int[]{Integer.parseInt(lengthRange[0]), Integer.parseInt(lengthRange[1])};
            if (parsedLength[0] > parsedLength[1]) {
                throw new IllegalArgumentException("Invalid range for \"allowedLength\".");
            }
            return parsedLength;
        }
        throw new IllegalArgumentException("\"allowedLength\" contains more than one dash.");
    }
}

@Command(name = "learn", description = "Learn grammar")
class Learn implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters(description = {"Each {} in command will be substituted with query.",
        "Whenever {} is not in command, the query is sent to standard input."})
    private String command;

    @Option(names = {"-o", "--output"}, description = "output, grammar file")
    private String outputFile;

    @Option(names = {"-i", "--input"}, defaultValue = "inputs", description = "folder with seed inputs")
    private Path inputFolder;

    @Option(names = {"-l", "--length"}, description = "allowed length of an input")
    private String allowedLength;

    @Option(names = {"-a", "--alphabet"}, defaultValue = "ASCII", description = "input alphabet")
    private CharacterUtils.InputAlphabet inputAlphabet;

    @Override
    public Integer call() {
        parent.initGlade();
        Log.debug("Starting subcommand learn");
        CharacterUtils.init(inputAlphabet);
        int[] allowedLength = Main.parseAllowedLength(this.allowedLength);
        List<String> seedInputs = new ArrayList<>();
        Log.debug("Creating oracle");
        DiscriminativeOracle oracle = new Oracle(command, allowedLength);
        try (Stream<Path> walk = Files.walk(inputFolder)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Log.debug("Reading seed input from " + p);
                    String seed = new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
                    if (!oracle.query(seed)) {
                        throw new IllegalArgumentException("Seed input has been rejected by oracle: "
                            + CharacterUtils.queryToAnsiString(seed));
                    }
                    Log.info("Adding new seed input: " + CharacterUtils.queryToAnsiString(seed));
                    seedInputs.add(seed);
                } catch (IOException e) {
                    throw new IllegalStateException("Error when reading seed input from file: " + p, e);
                }
            });
        } catch (IOException e) {
            System.err.println("Error when reading from input folder: " + inputFolder);
            return new CommandLine(this).getCommandSpec().exitCodeOnInvalidInput();
        }
        if (seedInputs.isEmpty()) {
            System.err.println("Error: input folder is empty");
            return new CommandLine(this).getCommandSpec().exitCodeOnInvalidInput();
        }
        Log.info("Learning grammar");
        Grammar grammar = GrammarSynthesis.getGrammarMultiple(seedInputs, oracle);
        if(outputFile == null) {
            outputFile = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm").format(LocalDateTime.now()) + ".gram";
        }
        Log.info("Saving grammar to " + outputFile);
        GrammarDataUtils.saveGrammar(outputFile, grammar);
        return 0;
    }
}

@CommandLine.Command(name = "fuzz", description = "Fuzz using grammar")
class Fuzz implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters
    private String command;

    @Option(names = {"-i", "--input"}, required = true, description = "input grammar")
    private String input;

    @Option(names = {"-c", "--count"}, defaultValue = "15", description = "number of generated inputs")
    private int count;

    @Option(names = {"-l", "--length"}, description = "allowed length of an input")
    private String allowedLength;

    @Option(names = {"-s", "--seed"}, defaultValue = "0", description = "seed for random number generator")
    private int seed;

    @Option(names = {"-m", "--mutations"}, defaultValue = "40", description = "number of mutations to seed input")
    private int numMut;

    @Option(names = {"-d", "--distribution"}, split = "," ,defaultValue = "0.2,0.2,0.2,0.4",
        description = "multinomial distribution of repetitions")
    private double[] distribution;

    @Option(names = {"-r", "--recursion"}, defaultValue = "0.2", description = "probability of using recursive production")
    private double recursionProbability;

    @Override
    // TODO add support for combined fuzzer
    public Integer call() {
        parent.initGlade();
        Log.debug("Starting subcommand fuzz");
        int[] allowedLength = Main.parseAllowedLength(this.allowedLength);

        Log.debug("Creating oracle");
        DiscriminativeOracle oracle = new Oracle(command, allowedLength);

        Log.info("Loading grammar from " + input);
        Grammar grammar = GrammarDataUtils.loadGrammar(input);

        int maxLength;
        if (allowedLength.length == 2) {
            maxLength = allowedLength[1];
        } else {
            maxLength = Integer.MAX_VALUE;
        }
        Log.debug("Creating samples");
        Iterable<String> samples = new GrammarMutationSampler(grammar,
            new SampleParameters(distribution, recursionProbability, 1, 200),
            maxLength, numMut, new Random(seed));

        int pass = 0;
        int processed = 0;
        for(String sample : samples) {
            Utils.printlnAnsi("Input: " + CharacterUtils.queryToAnsiString(sample));
            if(oracle.query(sample)) {
                Utils.printlnAnsi("@|green pass|@");
                pass++;
            } else {
                Utils.printlnAnsi("@|red fail|@");
            }
            System.out.println();

            processed++;
            if(processed >= count) {
                break;
            }
        }
        System.out.println("Pass rate: " + (float) pass / count);
        return 0;
    }
}

@Command(name = "print", description = "Print grammar")
class Print implements Callable<Integer> {

    @ParentCommand
    private Main parent;

    @Parameters
    private String grammar;

    @Override
    public Integer call() {
        parent.initGlade();
        Log.debug("Starting subcommand print");
        Grammar grammar = GrammarDataUtils.loadGrammar(this.grammar);
        Utils.printlnAnsi(grammar.node.toAnsiString());
        return 0;
    }
}

class Oracle implements DiscriminativeOracle {
    private String command;
    private int[] allowedLength;

    Oracle(String command, int[] allowedLength) {
        this.command = command;
        this.allowedLength = allowedLength;
    }

    public boolean query(String query) {
        Log.debug("Oracle input: " + CharacterUtils.queryToAnsiString(query));
        if (allowedLength.length == 1 && query.length() != allowedLength[0]) {
            Log.debug("Oracle @|red failed|@ because the query length is not equal to " + allowedLength[0] + ".");
            return false;
        }
        if (allowedLength.length == 2 && (query.length() < allowedLength[0] || query.length() > allowedLength[1])) {
            Log.debug("Oracle @|red failed|@ because the query length is not in " + allowedLength[0]
                + "-" + allowedLength[1] + ".");
            return false;
        }
        try {
            Process p;
            if (command.contains("{}")) {
                if (query.contains("\0")) { // Command arguments can't contain null bytes.
                    Log.debug("Oracle @|red failed|@ because the query contains a null byte.");
                    return false;
                }
                p = Runtime.getRuntime().exec(command.replace("{}", query));
            } else {
                p = Runtime.getRuntime().exec(command);
                for (char c : query.toCharArray()) {
                    p.getOutputStream().write(c);
                }
                p.getOutputStream().close();
            }

            Log.debug("Oracle output: " + new BufferedReader(new InputStreamReader(p.getInputStream()))
                .lines().collect(Collectors.joining("\n")));

            p.waitFor();

            Log.debug("Oracle exit value: " + (p.exitValue() == 0 ? "@|green " : "@|red ") + p.exitValue() + "|@");
            return p.exitValue() == 0;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
