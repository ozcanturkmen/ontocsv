# OntoCSV (ver. 1.0.0)
OntoCSV is a small Java library that can be used to populate an OWL ontology (in RDF/XML format) with instances (individuals) provided via CSV files.

## How to build OntoCSV

OntoCSV uses Maven as its build tool.

Maven:
```xml
<dependency>
  <groupId>com.github.ozcanturkmen</groupId>
  <artifactId>ontocsv</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Usage

### InstancePopulator class

OntoCSV provides a single immutable *InstancePopulator* class which has a static factory method ```InstancePopulator.create()``` to instantiate the class, as well as a public instance method, ```process()```, to populate the desired ontology classes with the instances/individuals provided via an input csv file. 

The no-argument variant of ```InstancePopulator.create()``` expects to find the input files in the current working directory, whereas the overloaded variant, ```InstancePopulator.create(String path, String... otherPathParts)```, can be hinted about the location of the files. In the latter case, the method parameters are passed to ```java.nio.file.Paths.get(String first, String... other)``` method to construct a system dependent ```java.nio.file.Path```. 

Note, however, that in either case the ```create()``` method is working with a single **directory path**, and **not** with separate **file paths**. 

### Input files (1 .owl and 2 .cvs files)

Prior to execution, exactly *3 input files* should exist in the specified directory: 

The original ontology (a .owl file in RDF/XML format), a csv file containing the names of ontology classes to be populated, plus another csv file that contains the names of individuals to be added to those classes. 

The class names csv file consists of a single line, containing the names of classes as its comma separated values. You can think of it as the header line that is split from a single instances input csv file. The reason the input data is expected to split into two separate files is to better benefit from *Java 8 Streams API*'s parallel processing capabilities. 

In the common use case, the instances input csv file will likely be huge, so there is a good reason to move the header line to a separate file, so that the remaining lines can be easily processed in parallel, without the need to sequentialize the ```java.nio.file.Files.lines()``` stream in order to read the first (the header) line. 

### Auto-discovery of input files within the specified directory

We do not need to provide the name of each input file separately, as with the above assumption, and thanks to the input splitting, the auto-discovery of input files within the specified directory becomes a simple task for the InstancePopulator: "Source ontology" = The first found .owl file,  "classes input file" = .csv file that is the smallest in size, "instances input file" = the other .csv file. 

Note that the instances input file would be **some** other csv file, so it's best to keep no more than 2 csv files in the input directory, rather than relying on programmatic guessing or randomness. 

### Constructor creates an ```java.util.Optional``` 

If everything goes well, that is a correct path has been supplied, and no ```IllegalArgumentException``` has been thrown, ```InstancePopulator.create()``` will return an InstancePopulator instance, but wrapped inside an ```Optional```. 

So the consumer code is encouraged and supposed to check whether the ```Optional``` is empty or not, prior to ```process()``` method invocation.

## Example Code

The following example demonstrates the usage of *InstancePopulator*, including the static factory method ```InstancePopulator.create()``` and the ```process()``` instance method. 

The no-argument variant of ```create()``` expects to find the input files in the current working directory. The simple logic behind the auto-discovery of input files is explained in above section. 

Note the ```Optional.ifPresent()``` check before invoking ```process()```:  

```Java
import com.github.ozcanturkmen.ontocsv.*;
import java.util.Optional;

public class Example {
    public static void main(String... args) {
        Optional<InstancePopulator> populator = InstancePopulator.create();
        populator.ifPresent(p -> p.process());
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
* [SLF4J](https://github.com/qos-ch/slf4j), version 1.7.30

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