package com.github.ozcanturkmen.ontocsv;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.util.FileManager;
import org.apache.jena.ontology.Individual;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstancePopulator {

    private enum FileType { OWL, CSV, OTHER }

    private static final Logger logger = LoggerFactory.getLogger(InstancePopulator.class);

    private final Path ontologyFilePath;
    private final Path csvClassesFilePath;
    private final Path csvInstancesFilePath;
    private final String nsPrefixURI;
    private final OntModel model;

    private InstancePopulator(Map<FileType, List<Path>> fileMap) {
        this.ontologyFilePath = fileMap.get(FileType.OWL).get(0);
        this.csvClassesFilePath = fileMap.get(FileType.CSV).get(0);
        this.csvInstancesFilePath = fileMap.get(FileType.CSV).get(1);
        this.model = this.getOntology();
        this.nsPrefixURI = this.model.getNsPrefixURI("");
    }

    private OntModel getOntology() {
        OntModel ontologyModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM, null);
        InputStream in = FileManager.get().open(this.ontologyFilePath.toString());
        if (in == null) {
            throw new IllegalArgumentException("Cannot read ontology file " + this.ontologyFilePath.toString());
        }
        ontologyModel.read(in, null);
        logger.info("Ontology " + this.ontologyFilePath.toString() + " loaded into memory");
        return ontologyModel;
    }

    private static <T> Collector<T, ?, List<T>> toSortedList(Comparator<? super T> c) {
        return collectingAndThen(
            toCollection(ArrayList::new), l -> { l.sort(c); return l; }
        );
    }

    private static String evaluateLine(String line) {
        if (line == null || !line.contains("\"")) return line.trim();
        StringBuilder sb = new StringBuilder(line.trim());
        int count = 0, fromIndex = 0, prevIndex = -2;
        while ((fromIndex = line.indexOf("\"", fromIndex)) != -1) {
            if (count % 2 == 0) prevIndex = fromIndex; 
            else {
                int indexOfComma = line.indexOf(",", prevIndex);
                if ((indexOfComma >= 0) && indexOfComma < fromIndex) sb.setCharAt(indexOfComma, '~');
            }
            fromIndex++;
            count++;
        }
        return sb.toString().replace("\"", "");
    }

    private static String getNormalizedInstanceName(String instanceName) {
        return instanceName
            .trim()
            .toLowerCase()
            // Remove double quotes and right parentheses
            .replaceAll("[\")]", "")
            // Replace slashes, dashes, dots, commas, equal signs, left parantheses, single quotes with underscores
            .replaceAll("(\\s)*(/|-|\\.|\\,|'|\\(|=)+(\\s)*", "_")
            // Replace ampersands with _and_
            .replaceAll("(\\s)*&+(\\s)*", "_and_")
            // Replace spaces with underscores
            .replaceAll("(\\s)+", "_");
    }
    
    public static Optional<InstancePopulator> create() {
        return create("./");
    }

    public static Optional<InstancePopulator> create(String path, String... otherPathParts) {
        try (Stream<Path> filesInCurrentDirectory = 
            Files.list(Paths.get(path, otherPathParts)).filter(Files::isRegularFile)
        ) {
            Map<InstancePopulator.FileType, List<Path>> fileMap = 
                filesInCurrentDirectory
                    .collect(
                        groupingBy(p -> {
                            if (p.toString().endsWith(".owl")) return FileType.OWL;
                            else if (p.toString().endsWith(".csv")) return FileType.CSV;
                            else return FileType.OTHER;
                        }, 
                        toSortedList(Comparator.comparing(p -> p.toFile().length())))
                    );

            if (!fileMap.containsKey(FileType.OWL) || fileMap.get(FileType.OWL).isEmpty()) {
                throw new IllegalArgumentException(
                    "OWL ontology file not found in specified path (current directry if none specified)");
            }

            if (!fileMap.containsKey(FileType.CSV) || fileMap.get(FileType.CSV).isEmpty()) {
                throw new IllegalArgumentException(
                    "CSV classes and instances files not found in specified path (current directry if none specified)");

            } else if (fileMap.get(FileType.CSV).size() < 2) {
                throw new IllegalArgumentException(
                    "Specified path must contain both class names and instance names csv files");
            }
            
            return Optional.of(new InstancePopulator(fileMap));

        } catch (IOException e) {
            logger.error(e.getMessage());
            return Optional.empty();
        }
    }

    public void process() {
        try (
            PrintWriter skippedRecordsWriter = 
                new PrintWriter(Paths.get(".","skipped.txt").toFile(), "UTF-8");
            FileWriter ontologyFileWriter = 
                new FileWriter(Paths.get(".","generated.owl").toFile());
            Stream<String> classesStream = 
                Files.lines(this.csvClassesFilePath, Charset.forName("UTF-8"));
            Stream<String> instancesStream = 
                Files.readAllLines(this.csvInstancesFilePath, Charset.forName("UTF-8")).parallelStream()
        ) {
            // Read class names
            List<String> classes = 
                classesStream
                    .filter(line -> line.trim().length() > 0)
                    .limit(1)
                    .flatMap(line -> Arrays.stream(evaluateLine(line).split(",")))
                    .collect(toList());

            // Initialize a map as a container for RDF statements
            Map<String, List<Statement>> instances = new HashMap<>();
                
            instancesStream
                .map(line -> evaluateLine(line).split(","))
                .forEach(line -> {
                    // Skip line if number of instances is greater than the number of classes
                    if (line.length > classes.size()) {
                        // Report that line in skipped.txt
                        skippedRecordsWriter.println(String.join(",", line));
                        return;
                    }
                    // Create statement from instance name
                    for (int index = 0; index < line.length; index++) {
                        String className = classes.get(index).replace('~', ',');
                        String instanceName = line[index].trim().replace('~', ',');
                        String normalizedInstanceName = getNormalizedInstanceName(instanceName);
                        if (!instances.containsKey(className)) instances.put(className, new ArrayList<Statement>());
                        if (instanceName.length() == 0) continue;
                        // Create class instance (individual)
                        OntClass classToAdd = this.model.getOntClass(this.nsPrefixURI + className);
                        if (classToAdd == null) continue;
                        Individual individual = classToAdd.createIndividual(this.nsPrefixURI + normalizedInstanceName);
                        individual.addRDFType(OWL2.NamedIndividual);
                        // Add RDF label
                        Statement stmt = model.createStatement(individual, RDFS.label,instanceName);
                        instances.get(className).add(stmt);
                    }
                });

            int instanceCount = 0;

            for (String key : instances.keySet()) {
                // Bulk add all instances to the model
                List<Statement> instancesToBeCreated = instances.get(key);
                this.model.add(instancesToBeCreated);
                instanceCount += instancesToBeCreated.size();
            }

            // Persist new individuals
            this.model.write(ontologyFileWriter);

            // Report processing result
            logger.info(instanceCount + " new individuals added to ontology");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        Optional<InstancePopulator> populator = InstancePopulator.create();
        populator.ifPresent(p -> p.process());
    }

}