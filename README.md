## Distributed Append-Only Store

This project is a distributed append-only store designed for high-throughput data ingestion and real-time key range and temporal queries. It is implemented as an application-level topology and runs on top of Apache Storm. 

### Data & Query Model
Data tuples continuouly arrive at the system. Each tuple consists of a key, a timestamp and a payload. A payload is a collection of primitives and/or user-defined objects. We assume the timestamps of the tuples are roughly in increasing order. A user query contains a key range constraint and a temporal range constraint. 

### Requirement
1. JDK 8 or higher;
1. [maven](http://maven.apache.org);
1. [Apache Storm](https://github.com/apache/storm) (not needed in local mode);
1. [HDFS](https://hadoop.apache.org) (not needed in local mode);

### Quick Start
#### 1. Local mode
Running our system in local model is the easiest way to get a feeling of the system. Local model is typically used internally to debug the topology. If you want to use our system in production, we highly suggest you to run our system in cluster model to fully exploit the potential performance of the system.

To run our system in local mode, you should follow those steps:

1. Download the source codes

```
$ git clone https://github.com/ADSC-Cloud/append-only-store
```
2. Make sure that ```<scope>provided</scope>``` is commented in pom.xml file.

3. Change the configures in config/TopologyConfig accordingly.

set ```HDFSFlag = false``` to use local file system.  <br />

  Create a local folder to store the data chunks generated by the system and set ```dataDir``` properly. Make sure that the local folder is writable.  

4. Compile the source code

 ```
 $ mvn clean install -DskipTests
 ```
 
5. Launch the system

Run the following command to launch the system

```
$ mvn exec:java -pl topology -Dexec.mainClass=indexingTopology.topology.kingbase.KingBaseTopology -Dexec.args="-m submit --local"
```

Open a new terminal and run the following command to ingest tuples to the system:

```
$ mvn exec:java -pl topology -Dexec.mainClass=indexingTopology.topology.kingbase.KingBaseTopology -Dexec.args="-m ingest -r 10000 --ingest-server-ip localhost"
```

Open a new terminal and run the following command to issue generated queries:

```
$ mvn exec:java -pl topology -Dexec.mainClass=indexingTopology.topology.kingbase.KingBaseTopology -Dexec.args="-m query --query-server-ip localhost"
```

Use ```-h``` to print the detailed usage of the arguments.

Deploy Web-UI daemon

```
$ mvn tomcat7:run -pl web
```
Now you can get access to the web ui via [http://localhost:8080](http://localhost:8080)

#### 2. Cluster model

1. Deploy Apache Storm and make sure that the nimbus and supervisors are running properly.

2. Setup HDFS


3. Download the source codes

```
$ git clone https://github.com/ADSC-Cloud/append-only-store
```

4. Make sure that ```<scope>provided</scope>``` is uncommented in pom.xml file.

5. Change the configures in config/TopologyConfig accordingly.

set ```HDFSFlag = true``` to use HDFS as the storage system.

Create a folder for the system in HDFS and set ```dataDir``` in the config file properly. Make sure that the folder is writable.

6. Compile the source code

```bash
$ mvn clean install -DskipTests
```

8. Launch Apache Storm's nimbus and supervisors properly. 

9. Submit the topology to Apache Storm

```
$ storm jar SOURCE_CODE_PATH/target/IndexingTopology-1.0-SNAPSHOT.jar indexingTopologyNormalDistributionTopology append-only-store
```


