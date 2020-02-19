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

## Dependencies
* [Apache Jena](https://github.com/apache/jena), version 3.14.0
* [SLF4J](https://github.com/qos-ch/slf4j), version 1.7.30

## License
### License

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