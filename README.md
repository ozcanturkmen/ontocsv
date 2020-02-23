# OntoCSV (ver. 1.0.0)
OntoCSV is a small Java library that can be used to populate an OWL ontology (in RDF/XML format) with instances (individuals) provided via CSV files.

[![Javadoc](https://img.shields.io/badge/javadoc-1.0.0-brightgreen)](https://ozcanturkmen.github.io/ontocsv-apidocs/)

## How to build OntoCSV

OntoCSV uses Maven as its build tool.

Maven:
```xml
<dependency>
  <groupId>com.github.ozcanturkmen</groupId>
  <artifactId>ontocsv</artifactId>
  <version>1.0</version>
</dependency>
```

## Usage

### InstancePopulator class

OntoCSV provides the immutable *InstancePopulator* class which is instantiated through a Builder (*InstancePopulator.Builder*) instance's ```build()``` method. InstancePopulator provides a public instance method ```process()``` to populate the desired ontology classes with the instances/individuals provided via an input csv file. 

The Builder class provides the following methods to configure an InstancePopulator: 

* ```withPath(String path, String... otherPathParts)``` : Specifies the path of the directory for input files. If ```build()``` is invoked without specifying a custom path, the input files will be searched within the current working directory.
* ```withConfigurator(String configuratorYaml)``` : Specifies the custom transformation rules (trim, letter casing, character removals/replacements) to be applied to each parsed instance name (reads settings from the YAML configuration file specified by *configuratorYaml* parameter).
* ```withSpec(OntModelSpec spec)``` : Sets the Apache Jena ontology model specification. Default spec is *OWL_DL_MEM*.
* ```withOWL2Correction()``` : Re-creates existing individuals (if any) as *OWL2 Named Individuals* before processing the ontology file. Activating this option would probably be desirable when working with a source ontology file that is already populated with some OWL2 named individuals, because currently the Apache Jena library does not properly load an OWL2 ontology to the in-memory model.

None of the configuration methods listed above is required. It is up to the user whether to chain a combination of these method calls or not, before invoking the ```build()``` method. It should be noted, however, that a ```build()``` method call issued immediately after a  ```new InstancePopulator.Builder()``` statement will result in an InstancePopulator instance that will look for the current working directory when searching for input files, and uses the default *OWL_DL_MEM* ontology model specification, without any OWL2 names individuals correction or instance name transformations.  

### Input files (1 .owl and 2 .cvs files)

Prior to execution, exactly *3 input files* should exist in the specified directory: 

The original ontology (a .owl file in RDF/XML format), a csv file containing the names of ontology classes to be populated, plus another csv file that contains the names of individuals to be added to those classes. 

The class names csv file consists of a single line, containing the names of classes as its comma separated values. You can think of it as the header line that is split from a single instances input csv file. The reason the input data is expected to split into two separate files is to better benefit from *Java 8 Streams API*'s parallel processing capabilities. 

In the common use case, the instances input csv file will likely be huge, so there is a good reason to move the header line to a separate file, so that the remaining lines can be easily processed in parallel, without the need to sequentialize the ```java.nio.file.Files.lines()``` stream in order to read the first line. 

### Auto-discovery of input files within the specified directory

Thanks to the input splitting, with the above assumption, the auto-discovery of input files within the specified directory becomes a simple task for the InstancePopulator: "Source ontology" = The first found .owl file,  "classes input file" = .csv file that is the smallest in size, "instances input file" = the other .csv file. So there is no need to provide the names of input files separately. 

Note that the instances input file would be **some** other csv file, so it's best to keep no more than 2 csv files in the input directory, rather than relying on programmatic guessing or randomness. 

### Configuring custom string transformations to be applied to parsed instance names

InstancePopulator does not support custom string transformations by default, mainly due to Jena's capability of invalid XML characters handling. Still, you might want the InstancePopulator to apply some custom string transformations (such as removing spaces, replacing some non-word characters with underscores, etc.), in a specific order, before it adds the instances to the ontology. To achieve that, you can create a YAML transformations configuration file, and make the InstancePopulator use these settings, by providing the path of the configuration file within the ```configuratorYaml``` parameter of Builder's ```withConfigurator(String configuratorYaml)``` method. 

A sample name transformations configuration file is shown below: 

```Yaml
trim: true # true or false; false if key or value is not present
casing: "lower" # "lower" or "upper"; none if key or value is not present
# transformations will be applied to each instance name, in the order specified below
transformations: 
  -  pattern: "[\")(]+" # regex pattern to remove all double quotes and parantheses
     replacement: ""
  -  pattern: "\\s+" # regex pattern to replace whitespace character sequences with a single underscore
     replacement: "_"
```

## Example Code

The following example demonstrates the usage of *InstancePopulator*, including the ```build()``` method and the configuration methods exposed by the *Builder*, as well as the ```process()``` method of the *InstancePopulator*, which performs the actual populating task. 

```Java
import com.github.ozcanturkmen.ontocsv.InstancePopulator;

public class Example {
    public static void main(String... args) {
        try{
            new InstancePopulator.Builder()
                // there should be 2 csv files and an .owl file present in ./assets directory    
                .withPath("assets")
                // transformation rules are placed in config.yml, located within the classpath
                .withConfigurator("config.yml") 
                // existing OWL2 named individuals (if any) will be pre-processed  
                .withOWL2Correction()
                // this is the default spec (Jena ontology model specification)
                .withSpec(InstancePopulator.Builder.OWL_DL_MEM) 
                // get an InstancePopulator instance using above configuration
                .build()
                // resulting ontology: ./generated.owl, skipped records: ./skipped.txt
                .process(); 
        } catch(Throwable e){
            // Expect to catch some IllegalArgumentExceptions here, in case of misconfiguration
        }
    }
}
```
Running this program with the provided test resources produces the following output:
```
[main] INFO com.github.ozcanturkmen.ontocsv.InstancePopulator - Ontology sample.owl loaded into memory
[main] INFO com.github.ozcanturkmen.ontocsv.InstancePopulator - 6 new individuals added to ontology
```

## Dependencies
* [Apache Jena](https://github.com/apache/jena), version 3.14.0

## License
OntoCSV is released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0).

>
Copyright &copy; Özcan Türkmen, 2020

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.